#!/usr/bin/env python3
"""Publish bounded test summaries and original captures, never APKs or signing material.

The main workflow still runs its established checks. Reading its results here avoids
rerunning those checks just to make their evidence available outside the Actions API.
"""
import datetime
import json
import os
import pathlib
import re
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
            for theme in ['dark', 'light']] + ['appearance-settings.png', 'appearance-player-75.png', 'appearance-player-125.png', 'appearance-search-75.png', 'appearance-search-125.png', 'stability-full-hidden.png', 'stability-full-controls.png', 'stability-tags-zero.png', 'stability-tags-twenty.png', 'stability-favorite-dark.png', 'stability-favorite-light.png']
CAPTURES += ['frames-inline-confirmed.png','frames-layout-dark.png','frames-layout-light.png']
CAPTURES += ["player-lock-inline.png","detail-portrait.png","detail-landscape.png"]
COUNT_KEYS = ['tests', 'failures', 'errors', 'skipped']
CAPTURES += ['v121-folder-order.png','v121-subfolders.png','v121-blue-dark.png','v121-pink-light.png','v121-favorites-primary.png']
CAPTURES += [f'v122-settings-{palette}-{mode}.png' for palette in ['pink','mint'] for mode in ['light','dark']] + ['v122-main-menu.png','v122-folder-menu.png']
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
        result = subprocess.run(['gh', 'api', '--allow-escape-sequences', f'repos/{REPO}/actions/jobs/{job["id"]}/logs'],
                                capture_output=True)
        if result.returncode == 0:
            log = re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]', '', result.stdout.decode(errors='replace'))
            selected = [line for line in log.splitlines()
                        if any(marker in line for marker in ['e: file:', 'FAILED', 'AssertionError',
                               'What went wrong', 'Caused by:', 'FAILURES!!!', '##[error]'])]
            message = '\n'.join(selected)[-12000:] or message
        failures.append({'job': job['name'], 'conclusion': job['conclusion'], 'message': message})
    return failures


def same_commit_main_checks():
    if os.environ.get('GITHUB_REF_NAME') not in ['main','feature/frame-controls-clip-export']:
        return {'status': 'not_requested', 'reason': 'This push is not main.'}
    source_branch=os.environ['GITHUB_REF_NAME']
    # Existing android.yml may finish after these scoped checks. Wait read-only for
    # that same source SHA; never dispatch/re-run tests or change established CI.
    deadline = time.monotonic() + 28 * 60
    discovery_deadline = time.monotonic() + 2 * 60
    selected = None
    while True:
        runs = api(f'actions/workflows/android.yml/runs?branch={source_branch}&event=push&per_page=20'
                   f'&head_sha={SHA}')['workflow_runs']
        matching = [run for run in runs if run['head_sha'] == SHA and run['head_branch'] == source_branch]
        if matching:
            selected = max(matching, key=lambda run: run['id'])
            if selected['status'] == 'completed' or os.environ.get('EVIDENCE_WAIT_FOR_MAIN')=='false':
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
              'scope': 'Short playback resource/fullscreen, compact tags and folder selection checks.' if os.environ.get('GITHUB_REF_NAME')=='dev/player-stability-controls' else 'Backup restore, native screens, app font size and tag spacing; no full player suite.',
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
    report['scroll_profile'] = {path.name: json.loads(path.read_text()) for path in ROOT.rglob('scroll-*.json')}
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
    if os.environ.get('GITHUB_REF_NAME') == 'dev/player-lock-community-batch':
        (OUTPUT / 'README.md').write_text(
            f'# Player, community and batch queue checks\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
            'This branch publishes reports and original fixture screenshots only; no APK, release or tag. '
            'Device results are scoped emulator checks, not physical-device or long-session performance claims.\n')
    if os.environ.get('GITHUB_REF_NAME') == 'dev/player-stability-controls':
        (OUTPUT / 'README.md').write_text(
            f'# Playback stability verification\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
            'Only the bounded feed regression, Debug Lint/build and two short API 28 device checks run on this branch. '
            'The checks cover compact captions, folder selection, landscape/portrait immersive playback, '
            'confirmed taps, player identity/position/speed/A-B, lifecycle-owner changes, media switches and disposal. '
            'This is not an hours-long soak or a claim that every device crash is fixed. '
            'Captures are original test-app renders; the video pattern is an isolated codec fixture, not website content.\n')
    if os.environ.get('GITHUB_REF_NAME') == 'dev/release-1.2.1':
        report['scope']='Backup, Room 3-to-4 migration, hierarchy/order/selection, accents, playback sanity and short layout-scroll comparison.'
        (OUTPUT / 'report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
        (OUTPUT / 'README.md').write_text(
            f'# v1.2.1 focused verification\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
            'Focused backup/hierarchy/resource JVM checks, Lint/build, native migration/order/subfolder/theme and short playback tests. '
            'Original screenshots use public-response fixtures and an isolated codec pattern. '
            'scroll_profile contains actual FrameMetrics from the same API 28 emulator before/after the update, '
            'using 300 captured posts without thumbnails to isolate layout, with 24 equal swipes. '
            'It is not a real-device or network/image-decoding benchmark. Full unrelated CI remains enabled and is reported at its current status. '
            'Official certificate and v1.2.0 upgrade checks run separately before publication.\n')
    if os.environ.get('GITHUB_REF_NAME') in ['dev/release-1.2.2','main']:
        report['scope']='Fixed legacy preference policy, full light/dark pink/mint palettes, menus, short playback sanity and layout scrolling.'
        (OUTPUT / 'report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
        (OUTPUT / 'README.md').write_text(
            f'# v1.2.2 focused verification\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
            'Focused JVM checks, Lint/build and six API 28 device cases cover legacy settings/history preservation, '
            'complete pink/mint palettes and menus, temporary mute/loop controls, screenshot sharing, fullscreen and short scrolling. '
            'Original captures use public-response fixtures and an isolated codec pattern. '
            'scroll_profile is actual emulator FrameMetrics with thumbnails absent to isolate layout; it is not a real-device benchmark. '
            'The existing main CI remains enabled. Official v1.2.1-to-v1.2.2 signed upgrade checks run separately before publication.\n')
    if os.environ.get('GITHUB_REF_NAME') == 'feature/page-cache-download-folders':
        report['scope']='Page cache HTTPS ranges/repeats/frame decode/fullscreen/recreation/cleanup and public/SAF download destinations, cancellation, errors and legacy exports on API 28 and 35.'
        (OUTPUT / 'report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
        (OUTPUT / 'README.md').write_text(f'# Page media checks\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
            'Controlled TLS fixture transfers, actual Media3 looping and frame-index reads, page cleanup, native folder picker grants and persistent destinations. '
            'Storage-full handling uses an injected ENOSPC write failure; no device disk is filled. '
            'API 28 and 35 emulator results are recorded per report path. This is not a physical SD-card or long-duration stress test. '
            'Existing Android CI remains enabled separately. No signing material or user media is collected.\n')
    if os.environ.get('GITHUB_REF_NAME') == 'feature/frame-controls-clip-export':
        report['scope']='Actual VFR decoded +/-1/2/3 frame selection, shared A/B points, fixed geometry, copy sample hashes, re-encoded boundaries, SAF single/clip/batch destinations on API 28/35 phones and tablet.'
        (OUTPUT / 'report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
        (OUTPUT / 'README.md').write_text(f'# Player tools checks\n\nSource: `{SHA}`\n\nRun: {report["run_url"]}\n\n'
            'Actual decoder frames, serialized rapid steps, confirmed timestamps, player identity and video bounds; '
            'stream-copy encoded-sample hashes and re-encoded duration/frame counts, with separate SAF destinations. '
            'Synthetic codec fixtures are never represented as website content. '
            'Reports distinguish API 28/35 and physical emulator profiles; physical devices and arbitrary codecs remain unverified. '
            'No version, tag, Release or signing changes.\n')
    print('Maintenance evidence recorded for', SHA, report['test_counts'])


if __name__ == '__main__':
    main()
