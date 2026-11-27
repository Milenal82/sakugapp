#!/usr/bin/env python3
"""Fail-closed v1.3.4 publication gates. Public metadata only; no signing material."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET

TAG = 'v1.3.4'
VERSION_CODE = 19
CERTIFICATE = '9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff'
V130_APK_SHA256 = '50da9e14b4c9fbcc3811f19419aedf5a2468d4d49a241cb399ba378a36ce0da1'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_report(report, sha):
    require(bool(re.fullmatch(r'[0-9a-f]{40}', sha)), 'Invalid source commit')
    expected = {'source_commit': sha, 'source_tag': TAG, 'applicationId': 'com.sakugapp',
                'versionName': '1.3.4', 'versionCode': VERSION_CODE,
                'signed': True, 'debuggable': False, 'certificate_sha256': CERTIFICATE,
                'lint_errors': 0, 'validation_scope': 'signed release verified'}
    for key, value in expected.items():
        require(type(report.get(key)) is type(value) and report[key] == value, 'Release metadata mismatch: '+key)
    require(report.get('unit_tests', 0) >= 230, 'Release unit-test coverage is missing')
    require(bool(re.fullmatch(r'[0-9a-f]{64}', report.get('apk_sha256', ''))), 'Invalid APK checksum')


def validate_upgrade_report(report, sha, apk_hash, validator=None):
    expected = {'result': 'passed', 'source_commit': sha, 'validation_commit': validator or sha,
                'apk_sha256': apk_hash, 'baseline_apk_sha256': V130_APK_SHA256,
                'uninstall_or_clear_data': False, 'private_data_injection': False,
                'existing_and_new_history_verified': True, 'preferences_retained': True,
                'local_profile_restart_verified': True}
    for key, value in expected.items():
        require(type(report.get(key)) is type(value) and report[key] == value, 'Upgrade evidence mismatch: '+key)
    for name, version, code in [('previous', '1.3.0', 16), ('candidate', '1.3.4', VERSION_CODE)]:
        expected_identity = {'package': 'com.sakugapp', 'versionName': version,
                             'versionCode': code, 'certificate_sha256': CERTIFICATE}
        require(report.get(name) == expected_identity, 'Upgrade APK identity mismatch: '+name)
    require(report.get('export_comparison') == 'all v4 folders, covers, positions, posts, memberships, children, saved tags and notes equal',
            'Full backup comparison evidence is missing')


def validate_public_baseline(releases):
    public = [r['tag_name'] for r in releases if not r['draft'] and not r['prerelease']]
    require(public and all(re.fullmatch(r'v\d+\.\d+\.\d+', tag) for tag in public), 'Unexpected public release metadata')
    latest = max(public, key=lambda tag: tuple(map(int, tag[1:].split('.'))))
    require(latest == 'v1.3.0', 'Latest official release changed; revalidate the upgrade baseline')


def passing_run(runs, sha, branch='main'):
    matching = [r for r in runs if r['head_sha'] == sha and r['head_branch'] == branch and r['event'] == 'push']
    require(matching, 'No exact-commit push checks on '+branch)
    run = max(matching, key=lambda item: item['id'])
    require(run['status'] == 'completed' and run['conclusion'] == 'success', 'Latest exact-commit check is not successful')
    return run


def verify_test_reports(folder, required_classes, minimum):
    files = list(folder.rglob('TEST-*.xml'))
    require(files, 'Native test XML missing')
    cases = [case for path in files for case in ET.parse(path).getroot().iter('testcase')]
    require(len(cases) >= minimum, 'Incomplete native test run')
    for case in cases:
        require(not any(case.find(k) is not None for k in ['failure', 'error', 'skipped']), 'Native test failed or skipped')
    for name, count in required_classes.items():
        require(sum(case.get('classname') == 'com.sakugapp.'+name for case in cases) >= count, 'Missing native tests: '+name)
    return len(cases)


def verify_remote_assets(assets, directory, payloads):
    """Before publishing, inspect the bytes actually attached to the draft."""
    require({a['name'] for a in assets} == payloads and len(assets) == len(payloads), 'Unexpected draft attachments')
    for asset in assets:
        data = (directory / asset['name']).read_bytes()
        require(asset['size'] == len(data), 'Draft asset size differs')
        # Current GitHub supplies a sha256 digest; missing/unknown digests fail closed.
        require(asset.get('digest') == 'sha256:'+hashlib.sha256(data).hexdigest(), 'Draft asset checksum differs')
