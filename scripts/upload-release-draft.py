#!/usr/bin/env python3
"""Upload only a verified signed bundle; refuse to alter a published release."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
from release_common import exact_tag, metadata, verify_checksums

parser = argparse.ArgumentParser()
parser.add_argument("--tag", required=True)
parser.add_argument("--directory", type=pathlib.Path, required=True)
args = parser.parse_args()
sha = exact_tag(args.tag)
repo = os.environ["GITHUB_REPOSITORY"]
if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
    raise SystemExit("Invalid repository identity")
folder = args.directory
report = json.loads((folder / "release-verification.json").read_text())
if not report.get("signed") or report.get("debuggable") or report.get("source_commit") != sha:
    raise SystemExit("Only a signed, verified exact-tag release can be uploaded.")
expected = {f"Sakugapp-{args.tag}-release.apk", f"Sakugapp-{args.tag}-source.zip", "release-notes.md", "release-verification.json", "SHA256SUMS.txt"}
if {p.name for p in folder.iterdir()} != expected:
    raise SystemExit("Unexpected/missing release files; refusing to upload.")
verify_checksums(folder, expected - {"SHA256SUMS.txt"})
if hashlib.sha256((folder / f"Sakugapp-{args.tag}-release.apk").read_bytes()).hexdigest() != report.get("apk_sha256"):
    raise SystemExit("APK differs from the verified signing report.")
view = subprocess.run(["gh", "release", "view", args.tag, "--repo", repo, "--json", "isDraft,targetCommitish,url"], text=True, capture_output=True)
if view.returncode == 0:
    release = json.loads(view.stdout)
    if not release["isDraft"] or release["targetCommitish"] != sha:
        raise SystemExit("Existing release is public or targets a different source; no changes made.")
    subprocess.run(["gh", "release", "edit", args.tag, "--repo", repo, "--title", f"Sakuga Mobile v{metadata()['versionName']}", "--notes-file", str(folder / "release-notes.md")], check=True)
elif "release not found" in view.stderr.lower() or "HTTP 404" in view.stderr:
    subprocess.run(["gh", "release", "create", args.tag, "--repo", repo, "--draft", "--verify-tag", "--target", sha, "--title", f"Sakuga Mobile v{metadata()['versionName']}", "--notes-file", str(folder / "release-notes.md")], check=True)
else:
    raise SystemExit("Unable to inspect existing release; refusing to overwrite it.")
subprocess.run(["gh", "release", "upload", args.tag, "--repo", repo, "--clobber"] + [str(folder / n) for n in sorted(expected)], check=True)
print("Verified files uploaded to a DRAFT release. No public publication was requested.")
