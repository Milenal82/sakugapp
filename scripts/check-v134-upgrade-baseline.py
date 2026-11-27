#!/usr/bin/env python3
"""Exercise the new public-UI checker on official v1.3.0; NOT a v1.3.4 upgrade result."""
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys
from release_common import command
from release_v134 import CERTIFICATE, V130_APK_SHA256, require

apk, output = map(pathlib.Path, sys.argv[1:])
output.mkdir(parents=True, exist_ok=True)
def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)
require(hashlib.sha256(apk.read_bytes()).hexdigest() == V130_APK_SHA256, 'Wrong official baseline APK')
tools = pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
signature = run(str(tools/'apksigner'), 'verify', '--print-certs', str(apk))
require(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)', signature) == [CERTIFICATE], 'Wrong official certificate')
badging = run(str(tools/'aapt'), 'dump', 'badging', str(apk))
require("package: name='com.sakugapp' versionCode='16' versionName='1.3.0'" in badging and 'application-debuggable' not in badging, 'Wrong baseline identity')
fixture = json.loads(pathlib.Path('upgrade-ui-check/fixtures/official-v121-favorites.json').read_text())
ids = [str(p['post']['id']) for p in fixture['posts']]
def launch():
    run('adb', 'shell', 'am', 'start', '-W', '-n', 'com.sakugapp/.MainActivity')
def stage(name):
    result = run('adb', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'stage', name, '-e', 'firstId', ids[0], '-e', 'nextId', ids[1],
                 '-e', 'class', 'com.sakugapp.upgradecheck.UpgradeV134Test', 'com.sakugapp.upgradecheck.test/androidx.test.runner.AndroidJUnitRunner')
    (output/(name+'-native-ui.txt')).write_text(result)
    require('OK (1 test)' in result and 'FAILURES' not in result, 'Baseline checker failed: '+name)
try:
    require('Success' in run('adb', 'install', str(apk)), 'Baseline install failed')
    root = pathlib.Path('upgrade-ui-check/build/outputs/apk')
    for file in [root/'debug/upgrade-ui-check-debug.apk', root/'androidTest/debug/upgrade-ui-check-debug-androidTest.apk']:
        require('Success' in run('adb', 'install', '-r', str(file)), 'Checker install failed')
    run('adb', 'push', 'upgrade-ui-check/fixtures/official-v121-favorites.json', '/sdcard/Download/official-v130-favorites.json')
    run('adb', 'shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', 'file:///sdcard/Download/official-v130-favorites.json')
    launch(); stage('seed')
    run('adb', 'shell', 'am', 'force-stop', 'com.sakugapp')
    require('Success' in run('adb', 'install', '-r', str(apk)), 'Baseline reinstallation failed')
    launch(); stage('verify')
    run('adb', 'shell', 'am', 'force-stop', 'com.sakugapp')
    launch(); stage('restart')
    (output/'baseline-checker.json').write_text(json.dumps({'result': 'baseline-checker-passed',
        'source_commit': command('git', 'rev-parse', 'HEAD'), 'baseline_apk_sha256': V130_APK_SHA256,
        'scope': 'Official v1.3.0 public UI checker compatibility only; NOT a v1.3.4 signed upgrade or release authorization'}, indent=2)+'\n')
except Exception as error:
    (output/'failure.txt').write_text(str(error)+'\n')
    with (output/'failure.png').open('wb') as file:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=file)
    run('adb', 'shell', 'uiautomator', 'dump', '/sdcard/baseline-failure.xml')
    (output/'failure-ui.xml').write_text(run('adb', 'shell', 'cat', '/sdcard/baseline-failure.xml'))
    raise
