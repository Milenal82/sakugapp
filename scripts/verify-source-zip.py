#!/usr/bin/env python3
"""Reject local configurations/private key files; require complete source/build/license inputs."""
import pathlib
import re
import sys
import zipfile


def verify(filename):
    with zipfile.ZipFile(filename) as archive:
        names = archive.namelist()
        for name in names:
            path = pathlib.PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or any(p in {".git", ".gradle", ".kotlin", ".idea", "build", "private", "dist", "__pycache__"} for p in path.parts):
                raise ValueError("Unexpected private/cache path in source ZIP")
            if path.name.startswith(".env") or path.name in {"local.properties", "keystore.properties", "signing.properties", "key.properties"} or path.suffix.lower() in {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".b64", ".base64"}:
                raise ValueError("Private/configuration file in source ZIP")
            data = archive.read(name)
            if re.search(rb"-----BEGIN (?:RSA |EC |ENCRYPTED |OPENSSH )?PRIVATE KEY-----[\r\n]+[A-Za-z0-9+/=\r\n]{64,}", data):
                raise ValueError("Private key material in source ZIP")
        required = ["LICENSE", "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", "app/build.gradle.kts", "app/src/main/AndroidManifest.xml", "app/src/main/assets/licenses/THIRD-PARTY-NOTICES.md", "scripts/verify-apk.py", ".github/workflows/release.yml", "docs/release-signing.md"]
        required += ["app/src/main/assets/licenses/" + p for p in ["GPL-3.0.txt", "APACHE-2.0.txt", "MPL-2.0.txt", "JSOUP-MIT.txt", "OKHTTP-PUBLICSUFFIX-NOTICE.txt"]]
        if any("Sakugapp/" + p not in names for p in required):
            raise ValueError("Corresponding source/build/license files are incomplete")
    print("Source ZIP checked: complete build inputs; no private signing files or local configuration.")


if __name__ == "__main__":
    verify(sys.argv[1])
