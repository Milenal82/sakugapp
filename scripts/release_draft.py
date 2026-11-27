#!/usr/bin/env python3
"""Draft lookup/download by immutable IDs. No create/upload/publish fallback."""
import hashlib
import json
import pathlib
import subprocess
from release_v134 import require, verify_remote_assets

REPO = 'Milenal82/sakugapp'
TAG = 'v1.3.4'
DRAFT_ID = 460623144


def api(path, **kwargs):
    command = ['gh', 'api', f'repos/{REPO}/'+path]
    if kwargs:
        command += ['--method', kwargs['method'], '--input', '-']
        return json.loads(subprocess.check_output(command, input=json.dumps(kwargs['body']), text=True))
    return json.loads(subprocess.check_output(command, text=True))


def list_releases():
    pages = json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp',
                                               f'repos/{REPO}/releases?per_page=100'], text=True))
    require(isinstance(pages, list) and all(isinstance(p, list) for p in pages), 'Malformed release pagination')
    return [release for page in pages for release in page]


def identity(release, source, draft=True):
    expected = {'id': DRAFT_ID, 'tag_name': TAG, 'target_commitish': source,
                'draft': draft, 'prerelease': False}
    for key, value in expected.items():
        require(type(release.get(key)) is type(value) and release[key] == value, 'Release identity mismatch: '+key)


def snapshot(release):
    """Exclude download counters: our own verification reads legitimately increment them."""
    assets = release.get('assets', [])
    require(len({a['id'] for a in assets}) == len(assets), 'Duplicate attachment IDs')
    return {'release': {key: release.get(key) for key in
                       ['id','tag_name','target_commitish','draft','prerelease','name','body','updated_at','published_at']},
            'assets': sorted([{key: a.get(key) for key in
                               ['id','name','size','state','digest','updated_at','content_type']}
                              for a in assets], key=lambda a: a['id'])}


def find_draft(source, releases=None, get=api):
    # A by-tag 404 is never queried or interpreted as permission to create a replacement.
    releases = list_releases() if releases is None else releases
    matches = [r for r in releases if r.get('tag_name') == TAG]
    require(len(matches) == 1, 'Expected one unique v1.3.4 Release; missing/conflicting objects stop publication')
    listed = matches[0]; identity(listed, source)
    detail = get(f'releases/{listed["id"]}')
    identity(detail, source)
    require(snapshot(listed) == snapshot(detail), 'Draft changed during lookup')
    return detail


def validate_attachments(release, folder, expected):
    assets = release['assets']
    require(all(type(a['id']) is int and a['id'] > 0 and a.get('state') == 'uploaded' for a in assets), 'Invalid/incomplete attachments')
    require(len({a['id'] for a in assets}) == len(assets), 'Duplicate attachment IDs')
    verify_remote_assets(assets, folder, expected)


def download_attachments(release, folder, expected_folder, expected, run=subprocess.run):
    validate_attachments(release, expected_folder, expected)
    folder = pathlib.Path(folder); folder.mkdir(parents=True, exist_ok=True)
    require(not list(folder.iterdir()), 'Draft verification download directory must be empty')
    for asset in release['assets']:
        path = folder/asset['name']  # Name was checked against the fixed five-file allowlist above.
        try:
            with path.open('xb') as output:
                run(['gh','api',f'repos/{REPO}/releases/assets/{asset["id"]}',
                     '-H','Accept: application/octet-stream'], stdout=output, check=True)
            require(path.stat().st_size == asset['size'], 'Downloaded asset size mismatch')
            actual = hashlib.sha256(path.read_bytes()).hexdigest()
            require('sha256:'+actual == asset['digest'], 'Downloaded asset SHA-256 mismatch')
            require(path.read_bytes() == (expected_folder/asset['name']).read_bytes(), 'Draft attachment differs from verified original bytes')
        except Exception:
            path.unlink(missing_ok=True)
            raise


def unchanged_draft(source, original, releases=None, get=api):
    current = find_draft(source, releases, get)
    require(snapshot(current) == snapshot(original), 'Draft/attachments changed before publication')
    return current
