#!/usr/bin/env python3
"""One-version publication authorized by the owner; all other releases stay drafts."""
import json
import os
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET
from release_common import exact_tag, metadata, verify_checksums

tag = 'v1.2.0'
sha = exact_tag(tag)
repo = os.environ['GITHUB_REPOSITORY']
assert repo == 'Milenal82/sakugapp'
# Reuse the scoped device checks for this exact main commit, without rerunning
# already-passing appearance tests in another emulator just because a tag was added.
runs = json.loads(subprocess.check_output(['gh', 'api',
    f'repos/{repo}/actions/workflows/maintenance.yml/runs?branch=main&event=push&head_sha={sha}&per_page=10'], text=True))['workflow_runs']
tested = [run for run in runs if run['head_sha'] == sha and run['conclusion'] == 'success']
assert tested, 'The exact source must pass the focused appearance checks before publication.'
run = max(tested, key=lambda item: item['id'])
checks = pathlib.Path(os.environ['RUNNER_TEMP']) / 'validated-appearance'
subprocess.run(['gh', 'run', 'download', str(run['id']), '--repo', repo,
                '--name', 'maintenance-test-reports', '--dir', str(checks)], check=True)
cases = [case for path in checks.rglob('TEST-*.xml') for case in ET.parse(path).getroot().iter('testcase')
         if case.get('classname') == 'com.sakugapp.AppearanceDeviceTest']
assert len(cases) == 2 and all(not any(case.find(kind) is not None for kind in ['failure', 'error', 'skipped']) for case in cases)
folder = pathlib.Path(os.environ['RELEASE_DIST'])
expected = {f'Sakugapp-{tag}-release.apk', f'Sakugapp-{tag}-source.zip',
            'release-notes.md', 'release-verification.json'}
verify_checksums(folder, expected)
report = json.loads((folder / 'release-verification.json').read_text())
assert report['source_commit'] == sha and report['source_tag'] == tag
assert report['versionName'] == '1.2.0' and report['versionCode'] == metadata()['versionCode']
assert report['signed'] and not report['debuggable'] and report['lint_errors'] == 0
assert report['certificate_sha256'] == '9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff'
previous = pathlib.Path(os.environ['PREVIOUS_RELEASE'])
subprocess.run(['gh', 'release', 'download', 'v1.1.0', '--repo', repo,
                '--pattern', '*release.apk', '--dir', str(previous)], check=True)
old, = previous.glob('*.apk')
tools = pathlib.Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
signature = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(old)], text=True)
assert re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signature)[1] == report['certificate_sha256']
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(old)], text=True)
identity = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
assert identity[1] == report['applicationId'] == 'com.sakugapp'
assert report['versionCode'] > int(identity[2])
release = json.loads(subprocess.check_output(['gh', 'release', 'view', tag, '--repo', repo,
                    '--json', 'isDraft,targetCommitish,url'], text=True))
assert release['isDraft'] and release['targetCommitish'] == sha, 'Do not modify an existing public release.'
subprocess.run(['gh', 'release', 'edit', tag, '--repo', repo, '--draft=false', '--latest'], check=True)
print('Published', release['url'], 'from', sha, 'with the existing official certificate.')
