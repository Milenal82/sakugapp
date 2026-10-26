#!/usr/bin/env python3
"""Signed development artifact only. Never creates tags or writes to GitHub Releases."""
import hashlib,json,pathlib,re,shutil,subprocess,sys,xml.etree.ElementTree as ET,zipfile
from release_common import ROOT,command,metadata
out=pathlib.Path(sys.argv[1]).resolve()
if out.is_relative_to(ROOT) or command('git','status','--porcelain'):
    raise SystemExit('Candidate requires a clean checkout and an output directory outside it.')
out.mkdir(parents=True,exist_ok=True)
if list(out.iterdir()):raise SystemExit('Candidate output directory must be empty.')
info=metadata();sha=command('git','rev-parse','HEAD')
apk=ROOT/'app/build/outputs/apk/release/app-release.apk'
with zipfile.ZipFile(apk) as archive:
    provenance=archive.read('META-INF/version-control-info.textproto').decode()
    if f'revision: "{sha}"' not in provenance:raise SystemExit('APK was not built from the current source commit.')
name=f"Sakugapp-{info['versionName']}-candidate.apk"
shutil.copyfile(apk,out/name)
subprocess.run([sys.executable,str(ROOT/'scripts/verify-apk.py'),str(out/name),'--report',str(out/'release-verification.json')],check=True)
report=json.loads((out/'release-verification.json').read_text())
suites=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testReleaseUnitTest').glob('TEST-*.xml')]
if not suites or any(int(s.get(k,'0')) for s in suites for k in ['failures','errors','skipped']):raise SystemExit('Passing Release tests with no skipped cases are required.')
lint=ET.parse(ROOT/'app/build/reports/lint-results-release.xml').getroot().findall('issue')
if any(i.get('severity') in ['Error','Fatal'] for i in lint):raise SystemExit('Release Lint contains errors.')
report.update(source_commit=sha,unit_tests=sum(int(s.get('tests','0')) for s in suites),lint_errors=0,lint_warnings=len(lint),distribution='signed development candidate; no Release published')
(out/'release-verification.json').write_text(json.dumps(report,indent=2)+'\n')
subprocess.run([str(ROOT/'scripts/package-source.sh'),str(out/f"Sakugapp-{info['versionName']}-source.zip")],check=True)
shutil.copyfile(ROOT/'release-notes'/f"v{info['versionName']}.md",out/'release-notes.md')
files=sorted(out.iterdir())
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in files))
print('Signed candidate, exact source, public verification report and checksums prepared. No Release changes.')
