"""Freeze the latest already-verified checkpoint; preserve legacy source/key/hash pins."""
import copy
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import release_v134_publication as checkpoint
import release_v134_recovery as legacy
from release_v134 import CERTIFICATE,passing_run

FIXTURE=pathlib.Path(__file__).parent/'fixtures/v134-upgrade.json'


def api(path):
    if path.endswith('/jobs?per_page=100'):
        return {'jobs':[{'name':n,'conclusion':'success'}for n in ['preflight','signed-release','upgrade-install']]+[{'name':'publish-v1-3-4','conclusion':'failure'}]}
    return {'id':checkpoint.RUN,'head_sha':legacy.SOURCE,'status':'completed','conclusion':'failure'}


class CheckpointTests(unittest.TestCase):
    def test_latest_signed_and_upgrade_checkpoint_is_explicitly_frozen(self):
        self.assertEqual(46021416067,checkpoint.RUN)
        self.assertEqual('221e87745c322d0a2f7ebb55986c4c94de1f6b6a3737fe4c09c03f1d63c7b3ce',checkpoint.APK_SHA256)
        self.assertEqual('092b83a05a1af372f1f55d48af557317ae82e34b592f28ade178ca08d3675d9f',legacy.APK_SHA256)
        self.assertEqual('9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff',CERTIFICATE)
        self.assertEqual(5,len(checkpoint.FILES))

    def test_actual_successful_upgrade_fixture_survives_failed_publish(self):
        result=checkpoint.validate_upgrade(FIXTURE,api)
        self.assertEqual('passed',result['result']);self.assertFalse(result['uninstall_or_clear_data'])
        self.assertEqual(legacy.SOURCE,result['validation_commit'])

    def test_changed_original_evidence_bytes_are_rejected(self):
        with tempfile.TemporaryDirectory()as temp:
            path=pathlib.Path(temp)/'upgrade.json';path.write_bytes(FIXTURE.read_bytes()+b' ')
            with self.assertRaises(ValueError):checkpoint.validate_upgrade(path,api)

    def test_wrong_source_run_or_failed_cancelled_upgrade_cannot_be_reused(self):
        for run in [{**api('run'),'id':1},{**api('run'),'head_sha':'d'*40},{**api('run'),'status':'in_progress'}]:
            with self.assertRaises(ValueError):checkpoint.validate_run(run,api('x/jobs?per_page=100')['jobs'])
        for conclusion in ['failure','cancelled','skipped',None]:
            jobs=api('x/jobs?per_page=100')['jobs'];jobs[2]['conclusion']=conclusion
            with self.assertRaises(ValueError):checkpoint.validate_run(api('run'),jobs)

    def test_unknown_or_tampered_payload_set_is_rejected(self):
        with tempfile.TemporaryDirectory()as temp:
            folder=pathlib.Path(temp)
            with self.assertRaises(ValueError):checkpoint.validate_files(folder)
            for name in checkpoint.FILES:(folder/name).write_bytes(b'bad bytes')
            with self.assertRaises(ValueError):checkpoint.validate_files(folder)

    def test_manual_validator_ci_is_allowed_only_for_exact_main_identity(self):
        run={'id':1,'head_sha':'c'*40,'head_branch':'main','event':'workflow_dispatch','status':'completed','conclusion':'success'}
        with self.assertRaises(ValueError):passing_run([run],'c'*40)
        passing_run([run],'c'*40,events=('push','workflow_dispatch'))
        for key,value in [('head_sha','b'*40),('head_branch','unreviewed'),('conclusion','failure')]:
            with self.assertRaises(ValueError):passing_run([{**run,key:value}],'c'*40,events=('push','workflow_dispatch'))

    def test_upload_tool_refuses_v134_before_any_release_query_or_creation(self):
        script=pathlib.Path(__file__).resolve().parents[1]/'upload-release-draft.py'
        process=subprocess.run([sys.executable,str(script),'--tag','v1.3.4','--directory','/tmp/nonexistent-v134'],text=True,capture_output=True)
        self.assertNotEqual(0,process.returncode);self.assertIn('never create or replace',process.stderr)
