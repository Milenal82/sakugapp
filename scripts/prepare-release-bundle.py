#!/usr/bin/env python3
"""Assemble an allowlisted release bundle from the exact immutable tag."""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import zipfile
from release_common import ROOT, command, exact_tag, metadata

parser = argparse.ArgumentParser()
parser.add_argument("--tag", required=True)
parser.add_argument("--apk", type=pathlib.Path, required=True)
parser.add_argument("--output", type=pathlib.Path, required=True)
parser.add_argument("--unsigned-preflight", action="store_true")
args = parser.parse_args()
data = metadata()
sha = exact_tag(args.tag)
if command("git", "status", "--porcelain"):
    raise SystemExit("Release bundles require a clean tagged source checkout.")
out = args.output.resolve()
if out.is_relative_to(ROOT):
    raise SystemExit("Release output must be outside the source checkout.")
out.mkdir(parents=True, exist_ok=True)
if list(out.iterdir()):
    raise SystemExit("Release output directory must be empty; do not mix builds.")
report_path = out / "release-verification.json"
verify = [sys.executable, str(ROOT / "scripts/verify-apk.py"), str(args.apk), "--report", str(report_path)]
if args.unsigned_preflight:
    verify.append("--unsigned-preflight")
subprocess.run(verify, check=True)
report = json.loads(report_path.read_text())
with zipfile.ZipFile(args.apk) as archive:
    provenance = archive.read('META-INF/version-control-info.textproto').decode()
    if f'revision: "{sha}"' not in provenance:
        raise SystemExit('APK was not built from the exact release tag commit.')
report.update(source_commit=sha, source_tag=args.tag)
suite_dir = ROOT / "app/build/test-results/testReleaseUnitTest"
import xml.etree.ElementTree as ET
suites = [ET.parse(p).getroot() for p in suite_dir.glob("TEST-*.xml")]
if not suites or any(int(s.attrib.get(k, "0")) for s in suites for k in ("failures", "errors", "skipped")):
    raise SystemExit("Passing Release unit-test XML reports with no skipped cases are required.")
report["unit_tests"] = sum(int(s.attrib["tests"]) for s in suites)
lint = ET.parse(ROOT / "app/build/reports/lint-results-release.xml").getroot()
if any(i.attrib.get("severity") in {"Error", "Fatal"} for i in lint.findall("issue")):
    raise SystemExit("Release Lint has errors.")
report["lint_errors"] = 0
report["lint_warnings"] = len(lint.findall("issue"))
report_path.write_text(json.dumps(report, indent=2) + "\n")
print("Public release verification:", json.dumps({key: report[key] for key in ["applicationId", "versionName", "versionCode", "debuggable", "signed", "certificate_sha256", "abis", "source_commit", "unit_tests", "lint_errors", "lint_warnings", "apk_sha256"]}, sort_keys=True))
source_name = f"Sakugapp-{args.tag}-source.zip"
subprocess.run([str(ROOT / "scripts/package-source.sh"), str(out / source_name)], check=True)
if not args.unsigned_preflight:
    shutil.copyfile(args.apk, out / f"Sakugapp-{args.tag}-release.apk")
notes = (ROOT / "release-notes" / f"{args.tag}.md").read_text()
if args.unsigned_preflight:
    notes = "> 发布准备草稿：尚无正式签名 APK。当前附件只有对应源码、校验文件及未签名预检报告；不能安装或公开为正式版。\n\n" + notes
else:
    notes += f"\n签名证书 SHA-256：`{report['certificate_sha256']}`\n"
notes += f"\n对应源码提交：`{sha}`。单元测试 {report['unit_tests']} 项通过；Release Lint 无错误。实际 ABI：{', '.join(report['abis'])}。\n"
notes += "\n请使用 SHA256SUMS.txt 校验附件；验证报告明确列出签名与验证范围。\n"
(out / "release-notes.md").write_text(notes)
paths = sorted(p for p in out.iterdir() if p.is_file())
(out / "SHA256SUMS.txt").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in paths))
print("Unsigned preflight bundle prepared without an APK attachment." if args.unsigned_preflight else "Signed release bundle prepared.")
