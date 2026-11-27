#!/usr/bin/env python3
"""Read-only verification of the exact signed/upgraded checkpoint and existing Draft."""
import argparse
import json
import os
import pathlib
import subprocess
import tempfile
import zipfile
from release_v134 import CERTIFICATE, require
from release_v134_recovery import checked_source, checked_candidate_source, SOURCE
from release_v134_publication import FILES, APK_SHA256, RUN, validate_files, validate_upgrade
from release_draft import api, find_draft, download_attachments, snapshot


def verify(bundle, upgrade, candidate=False):
    source, validator = checked_candidate_source() if candidate else checked_source()
    require(source == SOURCE, 'Wrong immutable source')
    report = validate_files(bundle)
    validate_upgrade(upgrade, api)
    with tempfile.TemporaryDirectory(prefix='v134-readonly-', dir=os.environ['RUNNER_TEMP']) as tmp:
        root = pathlib.Path(tmp)
        actual = root/'apk.json'
        subprocess.run(['python3','scripts/verify-apk.py',str(bundle/'Sakugapp-v1.3.4-release.apk'),'--report',str(actual)],
                       env={**os.environ,'ANDROID_RELEASE_CERT_SHA256':CERTIFICATE},check=True)
        for key,value in json.loads(actual.read_text()).items():
            require(report.get(key)==value,'APK report mismatch: '+key)
        with zipfile.ZipFile(bundle/'Sakugapp-v1.3.4-release.apk') as archive:
            require(f'revision: "{SOURCE}"' in archive.read('META-INF/version-control-info.textproto').decode(),'Wrong APK source identity')
        source_zip=root/'original-source.zip'
        subprocess.run(['git','archive','--format=zip','--prefix=Sakugapp/','-o',str(source_zip),SOURCE],check=True)
        require(source_zip.read_bytes()==(bundle/'Sakugapp-v1.3.4-source.zip').read_bytes(),'Wrong source archive')
        subprocess.run(['python3','scripts/verify-source-zip.py',str(source_zip)],check=True)
        release=find_draft(SOURCE)
        download_attachments(release,root/'draft-assets',bundle,set(FILES))
        result={'result':'verified-read-only','source_commit':SOURCE,'validation_commit':validator,
                'signed_and_upgrade_run':RUN,'upgrade_validation_commit':SOURCE,'apk_sha256':APK_SHA256,
                'certificate_sha256':CERTIFICATE,'release_id':release['id'],'draft_snapshot':snapshot(release),
                'files':{k:{'size':v[0],'sha256':v[1]}for k,v in FILES.items()}}
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--bundle',type=pathlib.Path,required=True)
    parser.add_argument('--upgrade',type=pathlib.Path,required=True)
    parser.add_argument('--candidate',action='store_true',help='Read-only CI on an unmerged allowlisted validator; never authorizes publication')
    parser.add_argument('--report',type=pathlib.Path,required=True)
    args=parser.parse_args()
    result=verify(args.bundle,args.upgrade,args.candidate)
    args.report.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items()if k not in ['draft_snapshot','files']},indent=2))
