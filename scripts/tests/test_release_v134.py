"""Public synthetic evidence only. No private signing key, live API or mutable version tag."""
import copy
import hashlib
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from release_common import previous_release_tag
from release_v134 import (CERTIFICATE, V130_APK_SHA256, validate_report, validate_upgrade_report,
                          validate_public_baseline, passing_run, verify_test_reports, verify_remote_assets)

SHA = 'a'*40
APK_HASH = 'b'*64


def signed_report():
    return {'source_commit': SHA, 'source_tag': 'v1.3.4', 'applicationId': 'com.sakugapp',
            'versionName': '1.3.4', 'versionCode': 19, 'signed': True, 'debuggable': False,
            'certificate_sha256': CERTIFICATE, 'lint_errors': 0, 'unit_tests': 230,
            'validation_scope': 'signed release verified', 'apk_sha256': APK_HASH}


def upgrade_report():
    def identity(version, code):
        return {'package': 'com.sakugapp', 'versionName': version, 'versionCode': code, 'certificate_sha256': CERTIFICATE}
    return {'result': 'passed', 'source_commit': SHA, 'validation_commit': SHA, 'apk_sha256': APK_HASH,
            'baseline_apk_sha256': V130_APK_SHA256, 'previous': identity('1.3.0', 16),
            'candidate': identity('1.3.4', 19), 'uninstall_or_clear_data': False, 'private_data_injection': False,
            'existing_and_new_history_verified': True, 'preferences_retained': True,
            'local_profile_restart_verified': True,
            'export_comparison': 'all v4 folders, covers, positions, posts, memberships, children, saved tags and notes equal'}


class V134ReleaseTests(unittest.TestCase):
    def test_explicit_upgrade_baseline(self):
        self.assertEqual('v1.3.0', previous_release_tag('v1.3.4'))

    def test_matching_signed_report(self):
        validate_report(signed_report(), SHA)

    def test_wrong_source_tag_package_version_code_or_certificate_cannot_publish(self):
        for key, value in [('source_commit', 'c'*40), ('source_tag', 'v1.3.3'), ('applicationId', 'com.other'),
                           ('versionName', '1.3.2-dev'), ('versionCode', 18), ('certificate_sha256', '0'*64),
                           ('signed', False), ('debuggable', True), ('unit_tests', 229), ('lint_errors', 1),
                           ('signed', 1), ('apk_sha256', 'bad'), ('validation_scope', 'unsigned')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                report = signed_report(); report[key] = value; validate_report(report, SHA)

    def test_missing_report_fields_fail(self):
        for key in signed_report():
            with self.subTest(key=key), self.assertRaises(ValueError):
                report = signed_report(); del report[key]; validate_report(report, SHA)

    def test_matching_native_upgrade_evidence(self):
        validate_upgrade_report(upgrade_report(), SHA, APK_HASH)

    def test_old_rebuilt_debug_wrong_revision_and_data_clearing_reports_fail(self):
        for key, value in [('source_commit', 'c'*40), ('validation_commit', 'c'*40), ('apk_sha256', 'd'*64),
                           ('baseline_apk_sha256', 'd'*64), ('result', 'skipped'), ('uninstall_or_clear_data', True),
                           ('private_data_injection', True), ('preferences_retained', False),
                           ('local_profile_restart_verified', False), ('existing_and_new_history_verified', False),
                           ('export_comparison', 'only posts compared')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                report = upgrade_report(); report[key] = value; validate_upgrade_report(report, SHA, APK_HASH)
        for section in ['previous', 'candidate']:
            for key, value in [('package', 'debug.package'), ('versionCode', 1), ('versionName', '1.3.3'),
                               ('certificate_sha256', '0'*64)]:
                with self.subTest(section=section, key=key), self.assertRaises(ValueError):
                    report = upgrade_report(); report[section][key] = value; validate_upgrade_report(report, SHA, APK_HASH)

    def test_missing_upgrade_fields_fail(self):
        for key in upgrade_report():
            with self.subTest(key=key), self.assertRaises(ValueError):
                report = upgrade_report(); del report[key]; validate_upgrade_report(report, SHA, APK_HASH)

    def test_actual_latest_release_must_remain_v130(self):
        releases = [{'tag_name': 'v1.2.2', 'draft': False, 'prerelease': False},
                    {'tag_name': 'v1.3.0', 'draft': False, 'prerelease': False},
                    {'tag_name': 'v1.3.4', 'draft': True, 'prerelease': False}]
        validate_public_baseline(releases)
        releases[-1]['draft'] = False
        with self.assertRaises(ValueError): validate_public_baseline(releases)
        with self.assertRaises(ValueError): validate_public_baseline([])

    def test_only_latest_successful_exact_main_push_qualifies(self):
        run = {'id': 1, 'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'status': 'completed', 'conclusion': 'success'}
        self.assertEqual(run, passing_run([run], SHA))
        for key, value in [('head_sha', 'b'*40), ('head_branch', 'dev/v134-ui-polish'), ('event', 'pull_request'),
                           ('status', 'in_progress'), ('conclusion', 'failure'), ('conclusion', 'cancelled')]:
            changed = {**run, 'id': 2, key: value}
            with self.subTest(key=key), self.assertRaises(ValueError): passing_run([changed], SHA)
        with self.assertRaises(ValueError): passing_run([run, {**run, 'id': 2, 'conclusion': 'failure'}], SHA)
        with self.assertRaises(ValueError): passing_run([], SHA)

    def test_required_native_cases_must_run_without_skips(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = pathlib.Path(temp); path = folder/'TEST-native.xml'
            xml = '<testsuite><testcase classname="com.sakugapp.V134UiDeviceTest" name="layout"/></testsuite>'
            path.write_text(xml)
            self.assertEqual(1, verify_test_reports(folder, {'V134UiDeviceTest': 1}, 1))
            for failure in ['skipped', 'failure', 'error']:
                path.write_text(xml.replace('/>', f'><{failure}/></testcase>'))
                with self.assertRaises(ValueError): verify_test_reports(folder, {'V134UiDeviceTest': 1}, 1)
            path.write_text(xml)
            with self.assertRaises(ValueError): verify_test_reports(folder, {'OtherTest': 1}, 1)
            with self.assertRaises(ValueError): verify_test_reports(folder, {'V134UiDeviceTest': 1}, 2)

    def test_draft_assets_need_exact_files_sizes_and_hashes(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = pathlib.Path(temp); data = b'synthetic-signed-payload'; (folder/'app.apk').write_bytes(data)
            asset = {'name': 'app.apk', 'size': len(data), 'digest': 'sha256:'+hashlib.sha256(data).hexdigest()}
            verify_remote_assets([asset], folder, {'app.apk'})
            for key, value in [('size', 1), ('digest', None), ('digest', 'sha256:'+'0'*64)]:
                with self.subTest(key=key), self.assertRaises(ValueError):
                    verify_remote_assets([{**asset, key: value}], folder, {'app.apk'})
            with self.assertRaises(ValueError): verify_remote_assets([asset, asset], folder, {'app.apk'})
            with self.assertRaises(ValueError): verify_remote_assets([], folder, {'app.apk'})


if __name__ == '__main__':
    unittest.main()
