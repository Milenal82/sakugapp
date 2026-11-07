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


V130_SOURCE = '37f5b5515d776ecc53ad8590f14395bb7704bddb'
V130_VALIDATION_FILES = {
    '.github/workflows/release.yml', '.github/workflows/refinement.yml',
    'app/src/androidTest/java/com/sakugapp/SearchNavigationDeviceTest.kt',
    'upgrade-ui-check/src/androidTest/java/com/sakugapp/upgradecheck/UpgradeV130Test.kt',
    'scripts/release_common.py', 'scripts/publish-v1.3.0.py',
    'scripts/tests/test_release_tools.py', 'docs/release-signing.md', 'docs/v1.3.0.md',
}


def validate_v130_validation_paths(paths):
    """A validator repair cannot change APK code, resources, dependencies or signing tools."""
    if set(paths) - V130_VALIDATION_FILES:
        raise ValueError('Validation revision changes files outside the explicit tooling allowlist')


def checked_v130_validation_source():
    if metadata()['tag'] != 'v1.3.0':
        raise ValueError('This recovery applies only to the existing v1.3.0 candidate')
    source = command('git', 'rev-parse', 'refs/tags/v1.3.0^{commit}')
    validator = command('git', 'rev-parse', 'HEAD^{commit}')
    if source != V130_SOURCE or command('git', 'status', '--porcelain'):
        raise ValueError('The original immutable source and clean validator checkout are required')
    command('git', 'merge-base', '--is-ancestor', source, validator)
    command('git', 'merge-base', '--is-ancestor', validator, 'origin/main')
    paths = command('git', 'diff', '--name-only', '--no-renames', '-z', source, validator).split('\0')
    validate_v130_validation_paths([p for p in paths if p])
    return source, validator
