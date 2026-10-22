#!/usr/bin/env python3
"""Decode an owner-provided keystore into a private, temporary runner directory. Never prints secrets."""
import base64
import os
import pathlib
import re
import tempfile

names = ["ANDROID_RELEASE_KEYSTORE_BASE64", "ANDROID_RELEASE_STORE_PASSWORD", "ANDROID_RELEASE_KEY_ALIAS", "ANDROID_RELEASE_KEY_PASSWORD", "ANDROID_RELEASE_CERT_SHA256"]
missing = [name for name in names if not os.environ.get(name, "").strip()]
if missing:
    raise SystemExit("Release signing is not configured. Missing protected Secrets: " + ", ".join(missing) + ". See docs/release-signing.md. No Debug fallback.")
if not re.fullmatch(r"[0-9a-fA-F]{64}", os.environ["ANDROID_RELEASE_CERT_SHA256"].replace(":", "").strip()):
    raise SystemExit("ANDROID_RELEASE_CERT_SHA256 must pin the owner's public certificate SHA-256.")
try:
    data = base64.b64decode(os.environ["ANDROID_RELEASE_KEYSTORE_BASE64"], validate=True)
except Exception:
    raise SystemExit("Signing keystore secret is not valid Base64.") from None
if not 32 <= len(data) <= 1024 * 1024:
    raise SystemExit("Signing keystore has an invalid size.")
folder = pathlib.Path(tempfile.mkdtemp(prefix="sakugapp-signing-", dir=os.environ["RUNNER_TEMP"]))
folder.chmod(0o700)
path = folder / "release.keystore"
with path.open("xb") as output:
    path.chmod(0o600)
    output.write(data)
with open(os.environ["GITHUB_ENV"], "a") as output:
    output.write(f"ANDROID_RELEASE_KEYSTORE={path}\n")
print("Owner-provided signing keystore prepared in temporary private storage.")
