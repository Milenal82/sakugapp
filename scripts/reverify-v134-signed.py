#!/usr/bin/env python3
"""Read and reverify original bytes; never builds, signs, edits or uploads a release."""
import hashlib
import json
import os
import pathlib
import subprocess
import zipfile
from release_common import verify_checksums
from release_v134 import CERTIFICATE, require, validate_report
from release_v134_recovery import SOURCE, SIGNED_RUN, APK_SHA256, checked_source


def main():
    source, validator = checked_source()
    require(source == SOURCE, 'Wrong immutable source')
    folder = pathlib.Path(os.environ['RELEASE_DIST'])
    expected = {'Sakugapp-v1.3.4-release.apk', 'Sakugapp-v1.3.4-source.zip', 'release-notes.md', 'release-verification.json'}
    require({p.name for p in folder.iterdir()} == expected | {'SHA256SUMS.txt'}, 'Unexpected original bundle files')
    verify_checksums(folder, expected)
    report = json.loads((folder/'release-verification.json').read_text())
    validate_report(report, source)
    apk = folder/'Sakugapp-v1.3.4-release.apk'
    require(hashlib.sha256(apk.read_bytes()).hexdigest() == report['apk_sha256'] == APK_SHA256, 'Original signed APK bytes changed')
    with zipfile.ZipFile(apk) as archive:
        require(f'revision: "{source}"' in archive.read('META-INF/version-control-info.textproto').decode(), 'Wrong APK provenance')
    verification = folder.parent/'original-apk-reverification.json'
    subprocess.run(['python3', 'scripts/verify-apk.py', str(apk), '--report', str(verification)], check=True,
                   env={**os.environ, 'ANDROID_RELEASE_CERT_SHA256': CERTIFICATE})
    for key, value in json.loads(verification.read_text()).items():
        require(report.get(key) == value, 'Original APK verification mismatch: '+key)
    subprocess.run(['python3', 'scripts/verify-source-zip.py', str(folder/'Sakugapp-v1.3.4-source.zip')], check=True)
    expected_zip = folder.parent/'original-source.zip'
    subprocess.run(['git', 'archive', '--format=zip', '--prefix=Sakugapp/', '-o', str(expected_zip), source], check=True)
    require(expected_zip.read_bytes() == (folder/'Sakugapp-v1.3.4-source.zip').read_bytes(), 'Source ZIP differs from immutable tag')
    repo = os.environ['GITHUB_REPOSITORY']
    require(repo == 'Milenal82/sakugapp', 'Wrong repository')
    def api(path):
        return json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/'+path]))
    run = api(f'actions/runs/{SIGNED_RUN}')
    require(run['head_sha'] == SOURCE, 'Wrong original signing run source')
    jobs = api(f'actions/runs/{SIGNED_RUN}/jobs?per_page=100')['jobs']
    for name in ['preflight', 'signed-release']:
        matches = [j for j in jobs if j['name'] == name]
        require(len(matches) == 1 and matches[0]['conclusion'] == 'success', 'Original signing/preflight gate did not pass')
    print(json.dumps({'source_commit': source, 'validation_commit': validator,
                      'original_signed_run': SIGNED_RUN, 'apk_sha256': APK_SHA256,
                      'certificate_sha256': CERTIFICATE, 'signed_bytes_unchanged': True}, indent=2))


if __name__ == '__main__':
    main()
