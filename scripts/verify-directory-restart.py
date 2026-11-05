import subprocess,pathlib,os
root=pathlib.Path('upgrade-ui-check/build/outputs/apk')
paths=[pathlib.Path(os.environ['RUNNER_TEMP'])/'application/app-debug.apk',root/'debug/upgrade-ui-check-debug.apk',root/'androidTest/debug/upgrade-ui-check-debug-androidTest.apk']
for apk in paths:subprocess.run(['adb','install','-r',str(apk)],check=True)
log=subprocess.check_output(['adb','shell','am','instrument','-w','-r','-e','class','com.sakugapp.upgradecheck.DirectoryPersistenceTest','com.sakugapp.upgradecheck.test/androidx.test.runner.AndroidJUnitRunner'],text=True)
(pathlib.Path(os.environ['RUNNER_TEMP'])/'directory-restart.txt').write_text(log)
print(log)
assert 'OK (1 test)' in log and 'FAILURES' not in log
