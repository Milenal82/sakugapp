"""Permission/review denial must stop automation, never approve or bypass protection."""
import importlib.util
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
path=pathlib.Path(__file__).resolve().parents[1]/'v134-publication-handoff.py'
spec=importlib.util.spec_from_file_location('handoff',path);h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
SOURCE='a'*40;HEAD='b'*40


class HandoffTests(unittest.TestCase):
    def env(self,root,branch=h.BRANCH):return {'GITHUB_REPOSITORY':h.REPO,'GITHUB_REF':'refs/heads/'+branch,'RUNNER_TEMP':root,'GITHUB_STEP_SUMMARY':root+'/summary'}

    def test_pr_creation_policy_denial_requires_owner_and_never_merges_or_dispatches(self):
        with tempfile.TemporaryDirectory()as root:
            calls=[]
            def gh(*args):
                calls.append(args)
                if args[:2]==('pr','list'):return '[]'
                raise subprocess.CalledProcessError(1,args,stderr='GitHub Actions is not permitted to create pull requests')
            with patch.dict(os.environ,self.env(root)),patch.object(h,'checked_candidate_source',return_value=(SOURCE,HEAD)),patch.object(h,'wait_checks'),patch.object(h,'gh',side_effect=gh):h.main()
            report=json.loads((pathlib.Path(root)/'v134-handoff.json').read_text())
            self.assertEqual('owner_pr_required',report['stage']);self.assertFalse(report['published'])
            self.assertFalse(any(c[:2]in [('pr','merge'),('workflow','run')]for c in calls))

    def test_required_review_stops_normal_merge_without_admin_fallback(self):
        with tempfile.TemporaryDirectory()as root:
            pr={'number':5,'url':'https://github.com/Milenal82/sakugapp/pull/5','state':'OPEN','headRefOid':HEAD}
            with patch.dict(os.environ,self.env(root)),patch.object(h,'checked_candidate_source',return_value=(SOURCE,HEAD)),patch.object(h,'wait_checks'),patch.object(h,'gh',return_value=json.dumps([pr])),patch.object(h.subprocess,'run',return_value=subprocess.CompletedProcess([],1,'','Reviews required'))as run:
                h.main();self.assertNotIn('--admin',run.call_args.args[0])
            report=json.loads((pathlib.Path(root)/'v134-handoff.json').read_text());self.assertEqual('owner_review_or_merge_required',report['stage']);self.assertFalse(report['published'])

    def test_main_dispatch_requests_protected_finish_only_never_patches_release(self):
        with tempfile.TemporaryDirectory()as root:
            def api(path):
                if path.startswith('releases/'):return {'id':460623144,'tag_name':'v1.3.4','target_commitish':SOURCE,'draft':True,'prerelease':False}
                return {'workflow_runs':[]}
            with patch.dict(os.environ,self.env(root,'main')),patch.object(h,'checked_candidate_source',return_value=(SOURCE,HEAD)),patch.object(h,'checked_source',return_value=(SOURCE,HEAD)),patch.object(h,'wait_checks'),patch.object(h,'api',side_effect=api),patch.object(h,'gh')as gh:
                h.main()
                command=gh.call_args.args
                self.assertEqual(('workflow','run','release.yml'),command[:3]);self.assertIn('finish_v134_draft=true',command)
            self.assertEqual('protected_release_requested',json.loads((pathlib.Path(root)/'v134-handoff.json').read_text())['stage'])

    def test_stale_other_branch_and_fork_runs_do_not_qualify(self):
        run={'head_sha':HEAD,'head_branch':h.BRANCH,'event':'push'}
        self.assertEqual([run],h.matching_runs([run],HEAD,h.BRANCH))
        for changed in [{**run,'head_sha':SOURCE},{**run,'head_branch':'fork'},{**run,'event':'pull_request_target'}]:self.assertFalse(h.matching_runs([changed],HEAD,h.BRANCH))
