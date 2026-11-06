#!/usr/bin/env python3
"""Public build metadata only. Never reads passwords or private key material."""
import pathlib
import hashlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]


def metadata():
    source = (ROOT / "app/build.gradle.kts").read_text()
    result = {}
    for field in ("applicationId", "versionName", "versionCode", "minSdk", "targetSdk"):
        match = re.search(rf'\b{field}\s*=\s*(?:"([^"]+)"|(\d+))', source)
        if not match:
            raise ValueError(f"Missing {field}")
        result[field] = match.group(1) or int(match.group(2))
    if not re.fullmatch(r"\d+\.\d+\.\d+", result["versionName"]):
        raise ValueError("A stable semantic version is required")
    result["tag"] = "v" + result["versionName"]
    result["name"] = ET.parse(ROOT / "app/src/main/res/values/strings.xml").find("string[@name='app_name']").text
    return result


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def exact_tag(tag):
    data = metadata()
    if tag != data["tag"]:
        raise ValueError("Tag does not match the version in this source commit")
    sha = command("git", "rev-parse", "HEAD^{commit}")
    if command("git", "rev-parse", f"refs/tags/{tag}^{{commit}}") != sha:
        raise ValueError("Build must use the exact tagged commit")
    return sha


def verify_checksums(folder, expected):
    """Require exactly one SHA-256 entry for every published payload."""
    found = set()
    for line in (folder / "SHA256SUMS.txt").read_text().splitlines():
        digest, name = line.split("  ", 1)
        if name not in expected or name in found or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("Invalid or duplicate release checksum entry")
        if hashlib.sha256((folder / name).read_bytes()).hexdigest() != digest:
            raise ValueError("Release bundle checksum mismatch")
        found.add(name)
    if found != expected:
        raise ValueError("Release checksum manifest is incomplete")


def previous_release_tag(tag):
    """Explicit upgrade paths only; unknown versions must never fall back to v1.0.0."""
    versions={"v1.1.0":"v1.0.0","v1.2.0":"v1.1.0","v1.2.1":"v1.2.0","v1.2.2":"v1.2.1","v1.3.0":"v1.2.2"}
    if tag not in versions:
        raise ValueError("No verified previous release configured for "+tag)
    return versions[tag]


def validate_layout_matrix(cases):
    expected={(palette,mode,scale)for palette in ['default','mint','pink','amber']for mode in ['light','dark']for scale in [.75,1.0,1.25]}
    actual={(case['palette'],case['background'],case['font'])for case in cases}
    if len(cases)!=24 or actual!=expected or any(case.get('home_and_profile')!='passed'for case in cases):
        raise ValueError('The complete 24-case native layout matrix must pass')
