#!/usr/bin/env python3
"""Frozen final checkpoint from the already signed AND upgrade-tested run 46021416067.

The earlier 46001134300 / 092b... checkpoint remains in release_v134_recovery.py.
It is never substituted for the current Draft's 221e... APK. No signing constants change.
"""
import hashlib
import json
import subprocess
import pathlib
from release_common import verify_checksums
from release_v134 import require, validate_report, validate_upgrade_report
from release_v134_recovery import SOURCE

RUN = 46021416067
APK_SHA256 = '221e87745c322d0a2f7ebb55986c4c94de1f6b6a3737fe4c09c03f1d63c7b3ce'
UPGRADE_SHA256 = '11efa0633c2fde2e63e92155080f9d74ed07e2fb202bd86023688fb0bc987d5a'
FILES = {
 'Sakugapp-v1.3.4-release.apk': (14954167, APK_SHA256),
 'Sakugapp-v1.3.4-source.zip': (2928629, '33770bd1fef3cdb8522d6314ed450033087a6e151d4433fbcb612ee6ef8d8954'),
 'release-notes.md': (981, 'f351cc80bbec94583df985cbcbe8cd69a655faaee0ba96316b0424bace28ba82'),
 'release-verification.json': (1834, '9e9e911c4a2da3c7116c357f006fd1e91ed96ddfbd6a9f5f3d443daa88164e83'),
 'SHA256SUMS.txt': (362, '233451faa76e59326695e5644217deb5aa702c2b677bc8e81e909d46f601d308'),
}


def validate_files(folder):
    require({p.name for p in folder.iterdir()} == set(FILES), 'Wrong final checkpoint files')
    for name, (size, digest) in FILES.items():
        path = folder/name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size == size, 'Original file size mismatch: '+name)
        require(hashlib.sha256(path.read_bytes()).hexdigest() == digest, 'Original file SHA-256 mismatch: '+name)
    verify_checksums(folder, set(FILES)-{'SHA256SUMS.txt'})
    report = json.loads((folder/'release-verification.json').read_text())
    validate_report(report, SOURCE)
    require(report['apk_sha256'] == APK_SHA256, 'Wrong final signed APK checkpoint')
    return report


def validate_run(run, jobs):
    require(run['id'] == RUN and run['head_sha'] == SOURCE and run['status'] == 'completed', 'Wrong proof source/run')
    # The overall run failed ONLY at publication; that must not discard its passing upgrade proof.
    for name in ['preflight','signed-release','upgrade-install']:
        matches = [j for j in jobs if j['name'] == name]
        require(len(matches) == 1 and matches[0]['conclusion'] == 'success', 'Untrusted/failed original gate: '+name)


def validate_upgrade(path, get):
    require(hashlib.sha256(path.read_bytes()).hexdigest() == UPGRADE_SHA256, 'Original upgrade evidence bytes changed')
    report = json.loads(path.read_text())
    validate_upgrade_report(report, SOURCE, APK_SHA256, SOURCE)
    validate_run(get(f'actions/runs/{RUN}'), get(f'actions/runs/{RUN}/jobs?per_page=100')['jobs'])
    return report
