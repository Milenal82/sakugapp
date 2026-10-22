"""Fault/safety tests use synthetic bytes only, never a real private signing key."""
import base64
import hashlib
import importlib.util
import os
import pathlib
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPTS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


apk = module("verify_apk", "verify-apk.py")
source = module("verify_source", "verify-source-zip.py")
from release_common import verify_checksums


class SigningPreparationTests(unittest.TestCase):
    def run_prepare(self, directory, extra=None):
        env = {"PATH": os.environ["PATH"], "RUNNER_TEMP": directory, "GITHUB_ENV": directory + "/github-env"}
        env.update(extra or {})
        return subprocess.run([sys.executable, str(SCRIPTS / "prepare-signing.py")], env=env, text=True, capture_output=True)

    def values(self):
        return {"ANDROID_RELEASE_KEYSTORE_BASE64": base64.b64encode(b"synthetic-not-a-real-key-file" * 3).decode(),
                "ANDROID_RELEASE_STORE_PASSWORD": "synthetic-store-secret", "ANDROID_RELEASE_KEY_PASSWORD": "synthetic-key-secret",
                "ANDROID_RELEASE_KEY_ALIAS": "synthetic-alias", "ANDROID_RELEASE_CERT_SHA256": "a" * 64}

    def test_missing_secrets_fail_without_materializing_files(self):
        with tempfile.TemporaryDirectory() as folder:
            result = self.run_prepare(folder)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("Missing protected Secrets", result.stderr)
            self.assertEqual([], list(pathlib.Path(folder).iterdir()))

    def test_malformed_secret_never_echoes_values(self):
        with tempfile.TemporaryDirectory() as folder:
            values = self.values()
            values["ANDROID_RELEASE_KEYSTORE_BASE64"] = "SYNTHETIC_PRIVATE_INPUT_NOT_BASE64!"
            result = self.run_prepare(folder, values)
            self.assertNotEqual(0, result.returncode)
            for value in values.values():
                self.assertNotIn(value, result.stdout + result.stderr)
            self.assertEqual([], list(pathlib.Path(folder).iterdir()))

    def test_public_certificate_pin_is_required_before_decoding(self):
        with tempfile.TemporaryDirectory() as folder:
            values = self.values(); values["ANDROID_RELEASE_CERT_SHA256"] = "invalid-pin"
            result = self.run_prepare(folder, values)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual([], list(pathlib.Path(folder).iterdir()))

    def test_keystore_is_private_and_only_path_enters_github_environment(self):
        with tempfile.TemporaryDirectory() as folder:
            values = self.values(); result = self.run_prepare(folder, values)
            self.assertEqual(0, result.returncode, result.stderr)
            record = (pathlib.Path(folder) / "github-env").read_text()
            path = pathlib.Path(record.strip().split("=", 1)[1])
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual(0o700, path.parent.stat().st_mode & 0o777)
            self.assertEqual(base64.b64decode(values["ANDROID_RELEASE_KEYSTORE_BASE64"]), path.read_bytes())
            for value in values.values():
                self.assertNotIn(value, result.stdout + result.stderr + record)


class ApkSafetyTests(unittest.TestCase):
    def test_fingerprint_is_exact_not_partial_or_arbitrary(self):
        self.assertEqual("ab" * 32, apk.normalize_fingerprint(":".join(["AB"] * 32)))
        for value in ["", "a" * 63, "x" * 64]:
            with self.assertRaises(ValueError): apk.normalize_fingerprint(value)

    def libraries(self, entries):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "synthetic.apk"
            with zipfile.ZipFile(path, "w") as archive:
                for name, bits, machine in entries:
                    header = bytearray(20); header[:4] = b"\x7fELF"; header[4] = 2 if bits == 64 else 1; header[5] = 1
                    header[18:20] = struct.pack("<H", machine); archive.writestr(name, header)
            with zipfile.ZipFile(path) as archive: return apk.native_libraries(archive)

    def test_actual_elf_machine_must_match_abi_directory(self):
        with self.assertRaises(ValueError): self.libraries([("lib/arm64-v8a/libtest.so", 32, 40)])

    def test_a_32_bit_only_library_is_rejected(self):
        with self.assertRaises(ValueError): self.libraries([("lib/armeabi-v7a/libtest.so", 32, 40)])

    def test_matching_64_bit_counterpart_is_preserved_in_report(self):
        result = self.libraries([("lib/armeabi-v7a/libtest.so", 32, 40), ("lib/arm64-v8a/libtest.so", 64, 183)])
        self.assertEqual([32, 64], [entry["bits"] for entry in result])


class SourceSafetyTests(unittest.TestCase):
    def test_private_files_and_traversal_are_rejected(self):
        for name in ["Sakugapp/private/key", "Sakugapp/signing.p12", "Sakugapp/.env", "Sakugapp/keystore.properties", "Sakugapp/key.base64", "Sakugapp/../escape"]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                path = pathlib.Path(folder) / "source.zip"
                with zipfile.ZipFile(path, "w") as archive: archive.writestr(name, b"synthetic")
                with self.assertRaises(ValueError): source.verify(path)

    def test_private_key_material_is_rejected_even_under_an_innocent_filename(self):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "source.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("Sakugapp/accidental.txt", b"-----BEGIN PRIVATE KEY-----\n" + b"A" * 80)
            with self.assertRaisesRegex(ValueError, "Private key material"): source.verify(path)

    def test_missing_build_or_license_inputs_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "source.zip"
            with zipfile.ZipFile(path, "w") as archive: archive.writestr("Sakugapp/README.md", "synthetic")
            with self.assertRaisesRegex(ValueError, "incomplete"): source.verify(path)


class ChecksumTests(unittest.TestCase):
    def manifest(self, folder, names):
        (folder / "SHA256SUMS.txt").write_text("".join(
            hashlib.sha256((folder / name).read_bytes()).hexdigest() + "  " + name + "\n" for name in names))

    def test_complete_manifest_verifies_every_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = pathlib.Path(temp)
            (folder / "app.apk").write_bytes(b"synthetic-apk")
            self.manifest(folder, ["app.apk"])
            verify_checksums(folder, {"app.apk"})
            (folder / "app.apk").write_bytes(b"changed-apk")
            with self.assertRaisesRegex(ValueError, "mismatch"): verify_checksums(folder, {"app.apk"})

    def test_omitted_payload_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = pathlib.Path(temp)
            (folder / "source.zip").write_bytes(b"synthetic-source")
            self.manifest(folder, ["source.zip"])
            with self.assertRaisesRegex(ValueError, "incomplete"): verify_checksums(folder, {"source.zip", "app.apk"})

    def test_duplicate_checksum_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = pathlib.Path(temp)
            (folder / "app.apk").write_bytes(b"synthetic-apk")
            self.manifest(folder, ["app.apk", "app.apk"])
            with self.assertRaisesRegex(ValueError, "duplicate"): verify_checksums(folder, {"app.apk"})


if __name__ == "__main__": unittest.main()
