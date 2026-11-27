"""Sanitized real Draft fixture + synthetic payloads. No real tokens or mutations."""
import copy
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import release_draft as draft
from release_v134_recovery import SOURCE

FIXTURE=pathlib.Path(__file__).parent/'fixtures/v134-draft.json'


class DraftLookupTests(unittest.TestCase):
    def setUp(self):self.release=json.loads(FIXTURE.read_text())

    def test_real_draft_found_by_list_and_id_without_by_tag_endpoint(self):
        calls=[]
        def get(path):
            calls.append(path)
            self.assertEqual('releases/460623144',path)
            return self.release
        self.assertEqual(self.release,draft.find_draft(SOURCE,[self.release],get))
        self.assertEqual(['releases/460623144'],calls)

    def test_missing_conflicting_public_wrong_id_or_wrong_source_stops(self):
        for releases in [[],[self.release,self.release]]:
            with self.assertRaises(ValueError):draft.find_draft(SOURCE,releases,lambda p:self.fail('GET must not occur'))
        for key,value in [('id',460623145),('target_commitish','b'*40),('draft',False),('prerelease',True),('id',True)]:
            changed={**self.release,key:value}
            with self.subTest(key=key),self.assertRaises(ValueError):draft.find_draft(SOURCE,[changed],lambda p:changed)

    def test_permissions_and_404_by_id_do_not_create_replacement(self):
        for status in [403,404]:
            def get(path):raise subprocess.CalledProcessError(1,['gh','api',path],stderr='HTTP '+str(status))
            with self.assertRaises(subprocess.CalledProcessError):draft.find_draft(SOURCE,[self.release],get)

    def test_concurrent_change_stops_but_download_counter_is_ignored(self):
        changed=copy.deepcopy(self.release);changed['assets'][0]['download_count']=8
        self.assertEqual(draft.snapshot(self.release),draft.snapshot(changed))
        for field in ['updated_at','body','name']:
            changed=copy.deepcopy(self.release);changed[field]='changed'
            with self.assertRaises(ValueError):draft.unchanged_draft(SOURCE,self.release,[changed],lambda p:changed)
        changed=copy.deepcopy(self.release);changed['assets'][0]['id']+=1
        with self.assertRaises(ValueError):draft.unchanged_draft(SOURCE,self.release,[changed],lambda p:changed)

    def test_metadata_changed_between_list_and_get_is_rejected(self):
        changed=copy.deepcopy(self.release);changed['assets'][0]['digest']='sha256:'+'0'*64
        with self.assertRaises(ValueError):draft.find_draft(SOURCE,[self.release],lambda p:changed)


class AssetDownloadTests(unittest.TestCase):
    def setup_payloads(self,root):
        original=root/'original';original.mkdir()
        assets=[];bodies={}
        for number,name in enumerate(['a.apk','source.zip','report.json','notes.md','SHA256SUMS.txt'],1):
            body=('original-'+name).encode();(original/name).write_bytes(body);bodies[number]=body
            assets.append({'id':number,'name':name,'state':'uploaded','size':len(body),
                           'digest':'sha256:'+hashlib.sha256(body).hexdigest(),'browser_download_url':'https://github.com/untagged-not-used'})
        return original,{'assets':assets},bodies

    def test_five_exact_files_use_asset_id_download_not_release_tag(self):
        with tempfile.TemporaryDirectory()as temp:
            root=pathlib.Path(temp);original,release,bodies=self.setup_payloads(root);calls=[]
            def run(command,stdout,check):
                calls.append(command);number=int(command[2].rsplit('/',1)[1]);stdout.write(bodies[number])
            target=root/'download'
            draft.download_attachments(release,target,original,{p.name for p in original.iterdir()},run)
            self.assertEqual(5,len(calls));self.assertTrue(all('/releases/assets/'in c[2]for c in calls))
            for p in original.iterdir():self.assertEqual(p.read_bytes(),(target/p.name).read_bytes())

    def test_corruption_and_interrupted_download_never_modify_original(self):
        for interrupt in [True,False]:
            with tempfile.TemporaryDirectory()as temp:
                root=pathlib.Path(temp);original,release,bodies=self.setup_payloads(root)
                snapshot={p.name:p.read_bytes()for p in original.iterdir()}
                def run(command,stdout,check):
                    stdout.write(b'corrupted')
                    if interrupt:raise subprocess.CalledProcessError(1,command)
                target=root/'download'
                with self.assertRaises((ValueError,subprocess.CalledProcessError)):
                    draft.download_attachments(release,target,original,set(snapshot),run)
                self.assertFalse(list(target.iterdir()))
                self.assertEqual(snapshot,{p.name:p.read_bytes()for p in original.iterdir()})

    def test_wrong_name_size_duplicate_id_or_extra_file_fails_before_network(self):
        for key,value in [('name','../escape'),('size',0),('state','new'),('digest','sha256:'+'0'*64)]:
            with tempfile.TemporaryDirectory()as temp:
                root=pathlib.Path(temp);original,release,bodies=self.setup_payloads(root);release['assets'][0][key]=value
                with self.assertRaises(ValueError):draft.download_attachments(release,root/'out',original,{p.name for p in original.iterdir()},lambda *a,**k:self.fail('No network expected'))
        with tempfile.TemporaryDirectory()as temp:
            root=pathlib.Path(temp);original,release,bodies=self.setup_payloads(root);release['assets'][1]['id']=release['assets'][0]['id']
            with self.assertRaises(ValueError):draft.validate_attachments(release,original,{p.name for p in original.iterdir()})
