#!/usr/bin/env python3
"""Verify APK metadata, debuggability, signing identity and actual packaged ELF ABIs."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import struct
import subprocess
import zipfile
from release_common import metadata


def normalize_fingerprint(value):
    value = value.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("A valid expected SHA-256 signing certificate fingerprint is required")
    return value


def native_libraries(archive):
    machines = {"arm64-v8a": (64, 183), "armeabi-v7a": (32, 40), "x86_64": (64, 62), "x86": (32, 3)}
    result = []
    for name in archive.namelist():
        if not name.startswith("lib/") or not name.endswith(".so"):
            continue
        abi = name.split("/")[1]
        data = archive.read(name)
        if len(data) < 20 or data[:4] != b"\x7fELF" or data[5] != 1:
            raise ValueError("Invalid or unsupported packaged ELF")
        bits = {1: 32, 2: 64}.get(data[4])
        machine = struct.unpack("<H", data[18:20])[0]
        if machines.get(abi) != (bits, machine):
            raise ValueError("ELF architecture does not match its APK directory")
        result.append({"path": name, "abi": abi, "bits": bits, "machine": machine})
    for entry in result:
        counterpart = {"armeabi-v7a": "arm64-v8a", "x86": "x86_64"}.get(entry["abi"])
        if counterpart and not any(e["path"] == entry["path"].replace("/" + entry["abi"] + "/", "/" + counterpart + "/") for e in result):
            raise ValueError("A 32-bit native library lacks its 64-bit counterpart")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("--report", type=pathlib.Path, required=True)
    parser.add_argument("--unsigned-preflight", action="store_true")
    args = parser.parse_args()
    expected = metadata()
    sdk = pathlib.Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    aapt = sdk / "build-tools/35.0.0/aapt"
    signer = sdk / "build-tools/35.0.0/apksigner"
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(args.apk)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match.groups() != (expected["applicationId"], str(expected["versionCode"]), expected["versionName"]):
        raise ValueError("APK package/version differs from the release source")
    manifest = subprocess.check_output([str(aapt), "dump", "xmltree", str(args.apk), "AndroidManifest.xml"], text=True)
    debug = re.search(r"android:debuggable[^\n]*=\(type 0x12\)(0x[0-9a-f]+)", manifest)
    if "application-debuggable" in badging or (debug and int(debug.group(1), 16) != 0):
        raise ValueError("A debuggable APK cannot be released")
    verification = subprocess.run([str(signer), "verify", "--verbose", "--print-certs", str(args.apk)], text=True, capture_output=True)
    fingerprint = None
    subject = None
    if args.unsigned_preflight:
        if verification.returncode == 0:
            raise ValueError("Unsigned preflight unexpectedly contains a signing identity")
    else:
        if verification.returncode != 0:
            raise ValueError("Release APK signature verification failed")
        fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", verification.stdout)
        subjects = re.findall(r"Signer #\d+ certificate DN: (.+)", verification.stdout)
        if len(fingerprints) != 1 or len(subjects) != 1:
            raise ValueError("Exactly one signing certificate is required")
        fingerprint = normalize_fingerprint(fingerprints[0])
        subject = subjects[0]
        if "android debug" in subject.lower():
            raise ValueError("Debug signing certificate is forbidden")
        if fingerprint != normalize_fingerprint(os.environ.get("ANDROID_RELEASE_CERT_SHA256", "")):
            raise ValueError("Signing identity differs from the owner's pinned certificate")
    with zipfile.ZipFile(args.apk) as archive:
        libraries = native_libraries(archive)
        for license_name in ["GPL-3.0.txt", "APACHE-2.0.txt", "MPL-2.0.txt", "JSOUP-MIT.txt", "THIRD-PARTY-NOTICES.md"]:
            if "assets/licenses/" + license_name not in archive.namelist():
                raise ValueError("Required license notice is absent from the APK")
    report = {**expected, "debuggable": False, "signed": not args.unsigned_preflight,
              "certificate_sha256": fingerprint, "certificate_subject": subject,
              "validation_scope": "unsigned build metadata only; not installable or a formal release" if args.unsigned_preflight else "signed release verified",
              "apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "apk_bytes": args.apk.stat().st_size,
              "abis": sorted({e["abi"] for e in libraries}), "native_libraries": libraries}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print("UNSIGNED PREFLIGHT verified; no formal APK produced." if args.unsigned_preflight else "Release APK identity, signature, metadata and ABIs verified.")


if __name__ == "__main__":
    main()
