#!/usr/bin/env python3
"""Narrow validator-only repair for the existing immutable, officially signed v1.3.4."""
from release_common import command, metadata
from release_v134 import require

SOURCE = 'a92f78d67e663f5fa7c271fbd0e65fec1770eac0'
TAG_OBJECT = '3bba4027a00e2fc751eb176ad0ad335866ad990b'
SIGNED_RUN = 46001134300
APK_SHA256 = '092b83a05a1af372f1f55d48af557317ae82e34b592f28ade178ca08d3675d9f'
ALLOWED = {
    '.github/workflows/release.yml', '.github/workflows/v134-recovery-checks.yml',
    '.github/workflows/v134-validation.yml', '.github/workflows/finish-v134.yml',
    '.github/workflows/v134-publication-handoff.yml',
    'scripts/release_v134_recovery.py', 'scripts/reverify-v134-signed.py',
    'scripts/upgrade-repositories.init.gradle', 'scripts/build-upgrade-checker.py',
    'scripts/release_v134.py', 'scripts/check-upgrade-1.3.4.py', 'scripts/publish-v1.3.4.py',
    'scripts/release_draft.py', 'scripts/release_v134_publication.py', 'scripts/verify-v134-publication.py',
    'scripts/v134-publication-handoff.py', 'scripts/upload-release-draft.py',
    'scripts/tests/test_release_draft.py', 'scripts/tests/test_publication_checkpoint.py',
    'scripts/tests/test_publication_handoff.py', 'scripts/tests/fixtures/v134-draft.json',
    'scripts/tests/fixtures/v134-upgrade.json',
    'scripts/tests/test_release_v134_recovery.py', 'scripts/tests/test_upgrade_downloads.py',
    'docs/release-signing.md', 'docs/v1.3.4.md',
}


def validate_paths(paths):
    require(not (set(paths)-ALLOWED), 'Recovery changes files outside the validator-only allowlist')


def checked_candidate_source():
    source = command('git', 'rev-parse', 'refs/tags/v1.3.4^{commit}')
    validator = command('git', 'rev-parse', 'HEAD^{commit}')
    require(source == SOURCE and command('git', 'rev-parse', 'refs/tags/v1.3.4') == TAG_OBJECT,
            'The original immutable tag must remain unchanged')
    require(not command('git', 'status', '--porcelain'), 'A clean committed validator is required')
    require(metadata()['tag'] == 'v1.3.4' and metadata()['versionCode'] == 19, 'Wrong candidate metadata')
    command('git', 'merge-base', '--is-ancestor', source, validator)
    paths = command('git', 'diff', '--name-only', '--no-renames', '-z', source, validator).split('\0')
    validate_paths([p for p in paths if p])
    return source, validator


def checked_source():
    source,validator=checked_candidate_source()
    require(command('git','rev-parse','origin/main')==validator,'Recovery must use the reviewed current main commit')
    return source,validator


def select_draft(releases, source):
    matches = [r for r in releases if r['tag_name'] == 'v1.3.4']
    require(len(matches) == 1, 'Exactly one existing v1.3.4 draft is required')
    release = matches[0]
    require(release.get('id') == 460623144, 'Unexpected v1.3.4 Draft ID')
    require(release['draft'] and not release['prerelease'] and release['target_commitish'] == source,
            'Public or different-source releases cannot be modified')
    return release
