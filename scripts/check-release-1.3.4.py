#!/usr/bin/env python3
"""Read-only pre-tag/pre-sign gate. An old UI run cannot qualify a new merge commit."""
import argparse
import json
import os
import subprocess
from release_common import command, exact_tag, metadata
from release_v134 import TAG, VERSION_CODE, require, validate_public_baseline, passing_run


def check(require_tag=True):
    require(os.environ['GITHUB_REPOSITORY'] == 'Milenal82/sakugapp', 'Wrong repository')
    sha = command('git', 'rev-parse', 'HEAD^{commit}')
    require(not command('git', 'status', '--porcelain'), 'Clean committed source is required')
    require(command('git', 'rev-parse', 'origin/main') == sha, 'Only the current main commit may be released')
    data = metadata()
    require(data['tag'] == TAG and data['versionCode'] == VERSION_CODE and data['applicationId'] == 'com.sakugapp', 'Wrong release metadata')
    if require_tag:
        require(exact_tag(TAG) == sha, 'Wrong immutable tag')
    else:
        require(not subprocess.check_output(['git', 'ls-remote', 'origin', 'refs/tags/'+TAG], text=True).strip(), 'Tag already exists; do not replace it')
    repo = os.environ['GITHUB_REPOSITORY']
    pages = json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases?per_page=100']))
    releases = [r for page in pages for r in page]
    validate_public_baseline(releases)
    candidates = [r for r in releases if r['tag_name'] == TAG]
    require(not candidates or (require_tag and len(candidates) == 1 and candidates[0]['draft']
                              and not candidates[0]['prerelease'] and candidates[0]['target_commitish'] == sha),
            'Existing release is public or points to another commit')
    checks = {}
    for workflow in ['android.yml', 'maintenance.yml', 'refinement.yml', 'v134-validation.yml']:
        runs = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repo}/actions/workflows/{workflow}/runs?event=push&head_sha={sha}&per_page=100']))
        checks[workflow] = passing_run(runs['workflow_runs'], sha)['html_url']
    print(json.dumps({'source_commit': sha, 'tag': TAG, 'checks': checks}, indent=2))
    return sha


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--before-tag', action='store_true')
    args = parser.parse_args()
    check(require_tag=not args.before_tag)
