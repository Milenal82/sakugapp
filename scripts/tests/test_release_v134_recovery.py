"""Validator-only recovery cannot substitute APKs, modify the app or bypass upgrade evidence."""
import copy
import pathlib
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import release_v134_recovery as recovery
from release_v134 import validate_upgrade_report
from test_release_v134 import upgrade_report, SHA, APK_HASH


class RecoverySafetyTests(unittest.TestCase):
    def test_only_explicit_validator_files_are_allowed(self):
        recovery.validate_paths(list(recovery.ALLOWED))
        for path in ['app/build.gradle.kts','app/src/main/java/com/sakugapp/MainActivity.kt','settings.gradle.kts',
                     'build.gradle.kts','upgrade-ui-check/build.gradle.kts','gradle.properties','scripts/prepare-signing.py',
                     'scripts/verify-apk.py','.github/workflows/android.yml','private/key.p12']:
            with self.subTest(path=path),self.assertRaises(ValueError):recovery.validate_paths([path])

    def test_actual_tag_object_source_and_apk_are_pinned(self):
        self.assertEqual(46001134300,recovery.SIGNED_RUN)
        self.assertEqual('a92f78d67e663f5fa7c271fbd0e65fec1770eac0',recovery.SOURCE)
        self.assertEqual('3bba4027a00e2fc751eb176ad0ad335866ad990b',recovery.TAG_OBJECT)
        self.assertEqual('092b83a05a1af372f1f55d48af557317ae82e34b592f28ade178ca08d3675d9f',recovery.APK_SHA256)

    def test_existing_draft_is_selected_without_published_tag_endpoint(self):
        draft={'id':460623144,'tag_name':'v1.3.4','draft':True,'prerelease':False,'target_commitish':recovery.SOURCE}
        self.assertEqual(draft,recovery.select_draft([draft],recovery.SOURCE))
        for changed in [{**draft,'draft':False},{**draft,'target_commitish':'b'*40},{**draft,'prerelease':True}]:
            with self.assertRaises(ValueError):recovery.select_draft([changed],recovery.SOURCE)
        for releases in [[],[draft,draft]]:
            with self.assertRaises(ValueError):recovery.select_draft(releases,recovery.SOURCE)

    def test_upgrade_evidence_separates_original_source_and_reviewed_validator(self):
        report=upgrade_report();validator='c'*40;report['validation_commit']=validator
        validate_upgrade_report(report,SHA,APK_HASH,validator)
        with self.assertRaises(ValueError):validate_upgrade_report(report,SHA,APK_HASH)
        for key,value in [('uninstall_or_clear_data',True),('apk_sha256','f'*64),('source_commit',validator),('validation_commit',SHA)]:
            changed=copy.deepcopy(report);changed[key]=value
            with self.assertRaises(ValueError):validate_upgrade_report(changed,SHA,APK_HASH,validator)

    def test_unmerged_dirty_or_mutated_tag_validator_is_rejected(self):
        validator='c'*40
        def command(*args):
            if args==('git','rev-parse','refs/tags/v1.3.4^{commit}'):return recovery.SOURCE
            if args==('git','rev-parse','refs/tags/v1.3.4'):return recovery.TAG_OBJECT
            if args in [('git','rev-parse','HEAD^{commit}'),('git','rev-parse','origin/main')]:return validator
            if args==('git','status','--porcelain'):return ''
            if args[:3]==('git','merge-base','--is-ancestor'):return ''
            if args[:3]==('git','diff','--name-only'):return 'scripts/build-upgrade-checker.py\0'
            raise AssertionError(args)
        with patch.object(recovery,'metadata',return_value={'tag':'v1.3.4','versionCode':19}):
            with patch.object(recovery,'command',side_effect=command):
                self.assertEqual((recovery.SOURCE,validator),recovery.checked_source())
            for arguments,value in [(('git','rev-parse','origin/main'),'b'*40),(('git','status','--porcelain'),'M app'),
                                    (('git','rev-parse','refs/tags/v1.3.4'),'d'*40),
                                    (('git','diff','--name-only','--no-renames','-z',recovery.SOURCE,validator),'app/build.gradle.kts\0')]:
                with patch.object(recovery,'command',side_effect=lambda *a: value if a==arguments else command(*a)):
                    with self.assertRaises(ValueError):recovery.checked_source()
