#!/usr/bin/env python3
"""Upgrade the real official v1.2.2 using its public importer; never uninstall or inject private data."""
import json,os,pathlib,re,subprocess,sys
from release_common import verify_checksums
from screenshot_pixels import edge_background
old_dir,new_dir,out=map(pathlib.Path,sys.argv[1:]);out.mkdir(parents=True,exist_ok=True)
old,=old_dir.glob('*.apk');new,=new_dir.glob('*.apk')
verify_checksums(new_dir,{p.name for p in new_dir.iterdir()}-{'SHA256SUMS.txt'})
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(*args):return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT)
def metadata(apk):
 badging=run(str(tools/'aapt'),'dump','badging',str(apk));signature=run(str(tools/'apksigner'),'verify','--print-certs',str(apk))
 m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
 assert m and 'application-debuggable'not in badging
 return {'package':m[1],'versionCode':int(m[2]),'versionName':m[3],'certificate_sha256':re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',signature)[1]}
previous,current=metadata(old),metadata(new)
assert previous['package']==current['package']=='com.sakugapp'
assert previous['versionName']=='1.2.2'and current['versionName']=='1.3.0'and current['versionCode']>previous['versionCode']
assert previous['certificate_sha256']==current['certificate_sha256']=='9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff'
fixture=json.loads(pathlib.Path('upgrade-ui-check/fixtures/official-v121-favorites.json').read_text());ids=[str(p['post']['id'])for p in fixture['posts']]
def launch():run('adb','shell','am','start','-W','-n','com.sakugapp/.MainActivity')
def capture(name):
 with(out/name).open('wb')as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
def helper(stage):
 text=run('adb','shell','am','instrument','-w','-r','-e','stage',stage,'-e','firstId',ids[0],'-e','nextId',ids[1],'-e','class','com.sakugapp.upgradecheck.UpgradeV130Test','com.sakugapp.upgradecheck.test/androidx.test.runner.AndroidJUnitRunner')
 (out/(stage+'-native-ui.txt')).write_text(text);print(text);assert 'OK (1 test)'in text and 'FAILURES'not in text
def exported(name):
 assert name in ['before-upgrade.json','after-upgrade.json']
 paths=run('adb','shell',f'find /sdcard/Download /sdcard/Documents -name {name} 2>/dev/null || true').splitlines()
 path=next((p for p in paths if p.startswith('/')and p.endswith('/'+name)),None);assert path,'Normal system export was not found'
 return json.loads(run('adb','shell','cat',path))
try:
 run('adb','shell','svc','power','stayon','true');run('adb','shell','input','keyevent','224');run('adb','shell','wm','dismiss-keyguard')
 assert 'Success'in run('adb','install',str(old))
 root=pathlib.Path('upgrade-ui-check/build/outputs/apk')
 for a in [root/'debug/upgrade-ui-check-debug.apk',root/'androidTest/debug/upgrade-ui-check-debug-androidTest.apk']:assert 'Success'in run('adb','install','-r',str(a))
 # The captured v4 backup is supported by both versions; its filename is not used as an internal ID.
 run('adb','push','upgrade-ui-check/fixtures/official-v121-favorites.json','/sdcard/Download/official-v122-favorites.json')
 run('adb','shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Download/official-v122-favorites.json')
 launch();helper('seed');before=exported('before-upgrade.json');capture('v122-seeded.png')
 assert len(before['folders'])==3 and len(before['posts'])==2 and len(before['links'])==3 and len(before['tagNotes'])==2
 run('adb','shell','am','force-stop','com.sakugapp');assert 'Success'in run('adb','install','-r',str(new))
 launch();helper('verify');after=exported('after-upgrade.json');capture('v130-upgraded.png')
 assert before['schemaVersion']==after['schemaVersion']==4
 for key in ['folders','posts','links','tagFavorites','tagNotes','subfolders','subfolderLinks']:assert before[key]==after[key],key+' changed during upgrade'
 assert len(after['subfolders'])==len(after['subfolderLinks'])==1
 run('adb','shell','am','force-stop','com.sakugapp');launch();helper('restart');capture('v130-restarted.png')
 report={'result':'passed','previous':previous,'candidate':current,'source_commit':json.loads((new_dir/'release-verification.json').read_text())['source_commit'],'uninstall_or_clear_data':False,'private_data_injection':False,'export_comparison':'all v4 folders, covers, positions, posts, memberships, children, saved tags and notes equal','existing_and_new_history_verified':True,'preferences_retained':True,'local_profile_restart_verified':True,'profile_limit':'v1.2.2 did not have local avatars/nicknames; new nickname restart tested separately from old website credentials. No live-account claim.'}
 (out/'upgrade-install.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,sort_keys=True))
except Exception as error:
 (out/'failure.txt').write_text(str(error)+'\n');capture('failure.png')
 try:run('adb','shell','uiautomator','dump','/sdcard/upgrade-failure.xml');(out/'failure-ui.xml').write_text(run('adb','shell','cat','/sdcard/upgrade-failure.xml'))
 except Exception:pass
 raise
