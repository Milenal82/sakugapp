#!/usr/bin/env python3
"""Publish bounded test summaries and original captures, never APKs or signing material.

The main workflow still runs its established checks. Reading its results here avoids
rerunning those checks just to make their evidence available outside the Actions API.
"""
import datetime
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile


ROOT = pathlib.Path(os.environ['EVIDENCE_DOWNLOADS'])
OUTPUT = pathlib.Path(os.environ['EVIDENCE_OUTPUT'])
REPO = os.environ['GITHUB_REPOSITORY']
RUN = os.environ['GITHUB_RUN_ID']
SHA = os.environ['GITHUB_SHA']
CAPTURES = [f'maintenance-{screen}-{theme}.png'
            for screen in ['search', 'history', 'settings', 'settings-backup']
            for theme in ['dark', 'light']]
COUNT_KEYS = ['tests', 'failures', 'errors', 'skipped']
MAIN_ARTIFACTS = {'check-reports', 'device-test-reports', 'search-back-gesture-reports',
                  'six-column-phone-reports', 'six-column-landscape-reports'}


def api(path):
    result = subprocess.run(['gh', 'api', f'repos/{REPO}/{path}'], capture_output=True)
    if result.returncode:
        # Avoid copying headers, token diagnostics or unrelated API response data.
        raise RuntimeError(f'GitHub API request failed: {path.split("?")[0]}')
    return json.loads(result.stdout)


def artifacts(run_id):
    return api(f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']


def public_artifacts(items, run_id):
    return [{'name': item['name'], 'bytes': item['size_in_bytes'],
             'url': f'https://github.com/{REPO}/actions/runs/{run_id}/artifacts/{item["id"]}'}
            for item in items]


def job_status(run_id):
    return [{'name': job['name'], 'status': job['status'], 'conclusion': job['conclusion'],
             'url': job['html_url'], 'id': job['id']}
            for job in api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs']]


def summarize_xml(name, data, counts, failures, lint):
    try:
        document = ET.fromstring(data)
    except ET.ParseError:
        failures.append({'report': name, 'message': 'The test report is not valid XML.'})
        return
    if pathlib.PurePosixPath(name).name.startswith('lint-results-'):
        issues = document.findall('issue')
        lint[name] = {'errors': sum(issue.get('severity') in ['Error', 'Fatal'] for issue in issues),
                      'warnings': sum(issue.get('severity') == 'Warning' for issue in issues)}
        return
    if not pathlib.PurePosixPath(name).name.startswith('TEST-'):
        return
    group = 'device' if 'androidTest-results' in name else (
        'release_unit' if 'testReleaseUnitTest' in name else 'debug_unit')
    totals = counts.setdefault(group, dict.fromkeys(COUNT_KEYS, 0))
    suites = [document] if document.tag == 'testsuite' else list(document.findall('testsuite'))
    for suite in suites:
        for key in COUNT_KEYS:
            totals[key] += int(suite.get(key, '0'))
        for case in suite.iter('testcase'):
            for kind in ['failure', 'error']:
                failure = case.find(kind)
                if failure is not None:
                    failures.append({'test': case.get('classname', '') + '.' + case.get('name', ''),
                                     'report': name,
                                     'message': (failure.text or failure.get('message', ''))[:5000]})


def failed_job_diagnostics(jobs):
    failures = []
    for job in jobs:
        if job['conclusion'] not in ['failure', 'timed_out', 'cancelled']:
            continue
        message = 'See the Actions job for this unsuccessful check.'
        result = subprocess.run(['gh', 'api', f'repos/{REPO}/actions/jobs/{job["id"]}/logs'],
                                capture_output=True)
        if result.returncode == 0:
            selected = [line for line in result.stdout.decode(errors='replace').splitlines()
                        if any(marker in line for marker in ['e: file:', 'FAILED', 'AssertionError',
                               'What went wrong', 'Caused by:', 'FAILURES!!!', '##[error]'])]
            message = '\n'.join(selected)[-12000:] or message
        failures.append({'job': job['name'], 'conclusion': job['conclusion'], 'message': message})
    return failures


def same_commit_main_checks():
    if os.environ.get('GITHUB_REF') != 'refs/heads/main':
        return {'status': 'not_requested', 'reason': 'This push is not main.'}
    # Existing android.yml may finish after these scoped checks. Wait read-only for
    # that same source SHA; never dispatch/re-run tests or change established CI.
    deadline = time.monotonic() + 28 * 60
    discovery_deadline = time.monotonic() + 2 * 60
    selected = None
    while True:
        runs = api('actions/workflows/android.yml/runs?branch=main&event=push&per_page=20'
                   f'&head_sha={SHA}')['workflow_runs']
        matching = [run for run in runs if run['head_sha'] == SHA and run['head_branch'] == 'main']
        if matching:
            selected = max(matching, key=lambda run: run['id'])
            if selected['status'] == 'completed':
                break
        elif time.monotonic() >= discovery_deadline:
            break
        if time.monotonic() >= deadline:
            break
        time.sleep(20)
    if selected is None:
        return {'status': 'not_found', 'reason': 'No automatic main check was found for this SHA.'}
    run_id = str(selected['id'])
    jobs = job_status(run_id)
    available = artifacts(run_id)
    result = {'run_url': selected['html_url'], 'source_commit': selected['head_sha'],
              'status': selected['status'], 'conclusion': selected['conclusion'], 'jobs': jobs,
              'artifacts': public_artifacts(available, run_id), 'test_counts': {},
              'lint': {}, 'failures': failed_job_diagnostics(jobs)}
    # Read only bounded XML members directly from the ZIP. No artifact files enter
    # the worktree, and no APK, private key or arbitrary archive path is extracted.
    for artifact in available:
        if artifact['name'] not in MAIN_ARTIFACTS or artifact['expired']:
            continue
        if artifact['size_in_bytes'] > 64 * 1024 * 1024:
            result['failures'].append({'artifact': artifact['name'], 'message': 'Report exceeds the 64 MiB evidence limit.'})
            continue
        with tempfile.TemporaryFile() as archive:
            download = subprocess.run(['gh', 'api', f'repos/{REPO}/actions/artifacts/{artifact["id"]}/zip'],
                                      stdout=archive, stderr=subprocess.PIPE)
            if download.returncode:
                result['failures'].append({'artifact': artifact['name'], 'message': 'Report download failed; use the Actions artifact.'})
                continue
            archive.seek(0)
            with zipfile.ZipFile(archive) as bundle:
                counts = result['test_counts'].setdefault(artifact['name'], {})
                for item in bundle.infolist():
                    basename = pathlib.PurePosixPath(item.filename).name
                    if item.file_size <= 4 * 1024 * 1024 and (
                            basename.startswith('TEST-') and basename.endswith('.xml') or
                            basename.startswith('lint-results-') and basename.endswith('.xml')):
                        summarize_xml(artifact['name'] + '/' + item.filename, bundle.read(item),
                                      counts, result['failures'], result['lint'])
    return result


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    images = OUTPUT / 'screenshots'
    images.mkdir(exist_ok=True)
    report = {'source_commit': SHA, 'run_id': RUN,
              'run_url': f'https://github.com/{REPO}/actions/runs/{RUN}',
              'captured_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'validation_result': os.environ.get('MAINTENANCE_VALIDATION_RESULT', 'unknown'),
              'scope': 'Backup import/restore, native search/history/settings screens; no player suite.',
              'jobs': [], 'artifacts': [], 'test_counts': {}, 'lint': {}, 'failures': []}
    try:
        report['jobs'] = job_status(RUN)
        report['artifacts'] = public_artifacts(artifacts(RUN), RUN)
        report['failures'].extend(failed_job_diagnostics(report['jobs']))
    except (RuntimeError, ValueError, KeyError) as error:
        report['failures'].append({'message': str(error)})
    for path in ROOT.rglob('*.xml'):
        if path.stat().st_size <= 4 * 1024 * 1024:
            summarize_xml(str(path.relative_to(ROOT)), path.read_bytes(), report['test_counts'],
                          report['failures'], report['lint'])
    report['device_diagnostics'] = [
        {'report': str(path.relative_to(ROOT)), 'tail': path.read_text(errors='replace')[-16000:]}
        for path in ROOT.rglob('maintenance-diagnostics/runner.log')]
    for name in CAPTURES:
        found = list(ROOT.rglob(name))
        if len(found) == 1:
            shutil.copyfile(found[0], images / name)
    try:
        report['main_automatic_checks'] = same_commit_main_checks()
    except (RuntimeError, ValueError, KeyError, zipfile.BadZipFile) as error:
        report['main_automatic_checks'] = {'status': 'unavailable', 'message': str(error)}
    report['captured_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    (OUTPUT / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    (OUTPUT / 'README.md').write_text(
        f'# Maintenance verification\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
        'Focused JVM backup tests, Android Lint, Debug compilation, fresh-install Room restore '
        'and API 28 Nexus S native search/history/settings checks. Original screenshots use '
        'a physical narrow emulator profile and 1.3 font scale, in dark and light themes. '
        'Collection/history tests use legal public-response fixtures, not account data. '
        'The existing main workflow is unchanged; its same-commit automatic checks are read '
        'in report.json when available. Full reports and the installable Debug APK remain '
        'Actions artifacts. This branch contains no APK, signing material, credentials or media.\n')
    print('Maintenance evidence recorded for', SHA, report['test_counts'])


if __name__ == '__main__':
    main()
