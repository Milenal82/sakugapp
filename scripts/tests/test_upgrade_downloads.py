"""Synthetic HTTP failures; no network requests, version changes or sleeping in unit tests."""
import importlib.util
import pathlib
import subprocess
import unittest

path = pathlib.Path(__file__).resolve().parents[1]/'build-upgrade-checker.py'
spec = importlib.util.spec_from_file_location('upgrade_downloads', path)
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)


class DownloadRetryTests(unittest.TestCase):
    def run_build(self, codes, texts):
        commands=[]; sleeps=[]
        def run(command, **kwargs):
            commands.append(command)
            return subprocess.CompletedProcess(command, codes.pop(0), texts.pop(0))
        result=module.build(run=run, sleep=sleeps.append, jitter=lambda a,b:0)
        return result,commands,sleeps

    def test_429_retries_then_proves_full_offline_rebuild(self):
        result,commands,sleeps=self.run_build([1,0,0],['Received status code 429 from server: Too Many Requests','',''])
        self.assertEqual(0,result);self.assertEqual([30],sleeps)
        self.assertNotIn('--refresh-dependencies',str(commands))
        self.assertIn('--offline',commands[-1]);self.assertIn(':upgrade-ui-check:clean',commands[-1])
        self.assertTrue(all('--max-workers=1' in c and '--no-parallel' in c for c in commands))

    def test_retries_are_bounded_and_preserve_failure(self):
        result,commands,sleeps=self.run_build([1]*4,['HTTP 429']*4)
        self.assertEqual(1,result);self.assertEqual(4,len(commands));self.assertEqual([30,60,120],sleeps)
        self.assertNotIn('--offline',commands[-1])

    def test_compile_or_test_failure_is_not_retried(self):
        for text in ['e: Unresolved reference: broken','Compilation error HTTP 429','There were failing tests HTTP 503','Could not find non-existent:dependency:1']:
            result,commands,sleeps=self.run_build([1],[text])
            self.assertEqual(1,result);self.assertEqual(1,len(commands));self.assertFalse(sleeps)

    def test_offline_missing_dependency_is_final(self):
        result,commands,sleeps=self.run_build([0,1],['','No cached version available for offline mode'])
        self.assertEqual(1,result);self.assertEqual(2,len(commands));self.assertFalse(sleeps)

    def test_authentication_and_not_found_are_not_rate_limit_errors(self):
        for text in ['HTTP 401','HTTP 403','HTTP 404']:
            self.assertFalse(module.transient_download_failure(text))
        for text in ['HTTP 502','HTTP 503','Read timed out','Temporary failure in name resolution']:
            self.assertTrue(module.transient_download_failure(text))
