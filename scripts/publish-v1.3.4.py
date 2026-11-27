#!/usr/bin/env python3
"""Publish only the exact main/tag v1.3.4 after official signing and native upgrade gates."""
import hashlib
import json
import os
import pathlib
import subprocess
import time
import zipfile
from release_common import command, exact_tag, metadata, previous_release_tag, verify_checksums
from release_v134 import (TAG, CERTIFICATE, VERSION_CODE, require, passing_run,
                          validate_report, validate_upgrade_report, validate_public_baseline,
                          verify_test_reports, verify_remote_assets)


def main():
    repo = os.environ['GITHUB_REPOSITORY']
    require(repo == 'Milenal82/sakugapp', 'Unexpected repository')
    finish = os.environ.get('V134_FINISH_DRAFT') == 'true'
    recovery = os.environ.get('V134_RECOVERY') == 'true' or finish
    if recovery:
        from release_v134_recovery import checked_source, APK_SHA256
        sha, validator = checked_source()
        if finish:
            from release_v134_publication import APK_SHA256, FILES, validate_files, validate_upgrade
    else:
        sha = exact_tag(TAG); validator = sha
    require(not command('git', 'status', '--porcelain'), 'Dirty publication checkout')
    require(command('git', 'rev-parse', 'origin/main') == validator, 'Validator must be the verified main commit')
    require(metadata()['versionCode'] == VERSION_CODE and previous_release_tag(TAG) == 'v1.3.0', 'Wrong release path')
    def api(path):
        return json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/'+path]))
    def releases():
        return json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases?per_page=100']))
    def baseline():
        validate_public_baseline([item for page in releases() for item in page])
    baseline()
    # Main CI must rerun after the PR merge. Older UI evidence does not qualify.
    gates = {}
    deadline = time.monotonic()+30*60
    for workflow in ['android.yml', 'maintenance.yml', 'refinement.yml', 'v134-validation.yml']:
        while True:
            runs = api(f'actions/workflows/{workflow}/runs?event=push&head_sha={sha}&per_page=100')['workflow_runs']
            matches = [r for r in runs if r['head_sha'] == sha and r['head_branch'] == 'main']
            if matches and max(matches, key=lambda r: r['id'])['status'] == 'completed':
                gates[workflow] = passing_run(runs, sha)
                break
            require(time.monotonic() < deadline, 'Main exact-commit CI did not complete: '+workflow)
            time.sleep(20)
    if recovery:
        runs = api(f'actions/workflows/v134-recovery-checks.yml/runs?head_sha={validator}&per_page=100')['workflow_runs']
        passing_run(runs, validator, events=('push','workflow_dispatch'))
    root = pathlib.Path(os.environ['RUNNER_TEMP'])/'v134-publish-checks'
    def download(name, folder):
        subprocess.run(['gh', 'run', 'download', str(gates['v134-validation.yml']['id']), '--repo', repo,
                        '--name', name, '--dir', str(folder)], check=True)
    required = {'V134UiDeviceTest': 5, 'ReleaseUiDeviceTest': 3, 'MenuAndPoolDeviceTest': 3,
                'ExtensionUiDeviceTest': 5, 'DiscoveryNavigationDeviceTest': 6, 'LocalProfileDeviceTest': 1,
                'BackupRestoreDeviceTest': 1, 'FrameControlsDeviceTest': 1, 'ClipExportDeviceTest': 1,
                'CommunityUiDeviceTest': 1, 'PlayerLockDeviceTest': 1, 'DownloadFoldersDeviceTest': 1,
                'BatchQueueDeviceTest': 1, 'ThemeSettingsDeviceTest': 3, 'PlayerStabilityDeviceTest': 2,
                'PostScrollDeviceTest': 1, 'SakugabotDeviceTest': 1, 'PreviewToolsBackDeviceTest': 1}
    for api_level in [28, 35]:
        folder = root/str(api_level)
        download(f'v134-device-reports-{api_level}', folder)
        verify_test_reports(folder, required, 53)
    dist = pathlib.Path(os.environ['RELEASE_DIST'])
    payloads = {f'Sakugapp-{TAG}-release.apk', f'Sakugapp-{TAG}-source.zip', 'release-notes.md', 'release-verification.json'}
    require({p.name for p in dist.iterdir()} == payloads | {'SHA256SUMS.txt'}, 'Unexpected release bundle')
    verify_checksums(dist, payloads)
    report = json.loads((dist/'release-verification.json').read_text())
    validate_report(report, sha)
    if finish: validate_files(dist)
    if recovery: require(report['apk_sha256'] == APK_SHA256, 'Original signed APK changed')
    apk = dist/f'Sakugapp-{TAG}-release.apk'
    require(hashlib.sha256(apk.read_bytes()).hexdigest() == report['apk_sha256'], 'APK hash mismatch')
    # Re-run real platform signature/manifest/ELF checks instead of trusting a JSON flag.
    verification = root/'actual-apk.json'
    subprocess.run(['python3', 'scripts/verify-apk.py', str(apk), '--report', str(verification)], check=True,
                   env={**os.environ, 'ANDROID_RELEASE_CERT_SHA256': CERTIFICATE})
    actual = json.loads(verification.read_text())
    for key, value in actual.items():
        require(report.get(key) == value, 'APK report mismatch: '+key)
    with zipfile.ZipFile(apk) as archive:
        require(f'revision: "{sha}"' in archive.read('META-INF/version-control-info.textproto').decode(), 'APK source revision mismatch')
    subprocess.run(['python3', 'scripts/verify-source-zip.py', str(dist/f'Sakugapp-{TAG}-source.zip')], check=True)
    upgrade = json.loads((pathlib.Path(os.environ['RUNNER_TEMP'])/'upgrade-install-report/upgrade-install.json').read_text())
    if finish:
        validate_upgrade(pathlib.Path(os.environ['RUNNER_TEMP'])/'upgrade-install-report/upgrade-install.json',api)
    else:
        validate_upgrade_report(upgrade, sha, report['apk_sha256'], validator)
    # Exact, immutable source ZIP bytes must match a fresh archive of the tagged checkout.
    source = root/'expected-source.zip'
    subprocess.run(['git','archive','--format=zip','--prefix=Sakugapp/','-o',str(source),sha],check=True)
    subprocess.run(['python3','scripts/verify-source-zip.py',str(source)],check=True)
    require(source.read_bytes() == (dist/f'Sakugapp-{TAG}-source.zip').read_bytes(), 'Source ZIP differs from exact tag')
    from release_draft import find_draft, download_attachments, unchanged_draft, identity
    release=find_draft(sha)
    download_attachments(release,root/'draft-assets',dist,payloads|{'SHA256SUMS.txt'})
    baseline()
    # Re-fetch without force to catch concurrent source/tag changes before publication.
    subprocess.run(['git', 'fetch', 'origin', 'main:refs/remotes/origin/main', 'tag', TAG], check=True)
    if recovery:
        require(checked_source() == (sha,validator), 'Original source or validator moved')
        passing_run(api(f'actions/workflows/v134-recovery-checks.yml/runs?head_sha={validator}&per_page=100')['workflow_runs'],validator,events=('push','workflow_dispatch'))
    else:
        require(command('git', 'rev-parse', 'origin/main') == exact_tag(TAG) == sha, 'Release source moved')
    for workflow in gates:
        passing_run(api(f'actions/workflows/{workflow}/runs?event=push&head_sha={sha}&per_page=100')['workflow_runs'], sha)
    unchanged_draft(sha,release)
    subprocess.run(['gh','api','--method','PATCH',f'repos/{repo}/releases/{release["id"]}','--input','-'],
                   input=json.dumps({'draft':False,'make_latest':'true'}),text=True,check=True)
    public = api(f'releases/{release["id"]}')
    identity(public,sha,draft=False)
    verify_remote_assets(public['assets'],dist,payloads|{'SHA256SUMS.txt'})
    require({(a['id'],a['name'])for a in public['assets']}=={(a['id'],a['name'])for a in release['assets']},'Published attachment identity changed')
    latest=api('releases/latest')
    identity(latest,sha,draft=False)
    require(latest['id']==release['id'] and latest['tag_name'] == TAG,
            'Publication did not produce the expected Latest release; inspect GitHub before retrying')
    # Public download must work without a token and retain the exact validated bytes.
    import urllib.request,urllib.error
    download_url=f'https://github.com/{repo}/releases/download/{TAG}/Sakugapp-{TAG}-release.apk'
    for attempt in range(4):
        try:
            with urllib.request.urlopen(download_url,timeout=60) as response:
                public_bytes=response.read()
            break
        except (urllib.error.URLError,TimeoutError) as error:
            if attempt==3 or (isinstance(error,urllib.error.HTTPError) and error.code not in [404,429,502,503,504]):raise
            time.sleep(5*2**attempt)
    require(hashlib.sha256(public_bytes).hexdigest()==report['apk_sha256'] and public_bytes==apk.read_bytes(),
            'Published APK download differs from verified bytes')
    result={'release':public['html_url'],'release_id':public['id'],'draft':public['draft'],'prerelease':public['prerelease'],
            'latest':True,'apk_download':download_url,'source_commit':sha,'validation_commit':validator,
            'versionName':'1.3.4','versionCode':VERSION_CODE,'apk_sha256':report['apk_sha256'],
            'certificate_sha256':CERTIFICATE,'upgrade_result':'passed','files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest()for p in dist.iterdir()}}
    (pathlib.Path(os.environ['RUNNER_TEMP'])/'publication-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'release': public['html_url'], 'commit': sha, 'validation_commit': validator, 'versionName': '1.3.4',
                      'versionCode': VERSION_CODE, 'apk_sha256': report['apk_sha256'],
                      'certificate_sha256': CERTIFICATE, 'checks': {k: v['html_url'] for k, v in gates.items()}}, indent=2))


if __name__ == '__main__':
    main()
