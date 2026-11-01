#!/usr/bin/env python3
"""Install official v1.2.0, seed using its importer, then upgrade without uninstall or private-data writes."""
import json,os,pathlib,re,subprocess,sys,xml.etree.ElementTree as ET
from release_common import verify_checksums
from screenshot_pixels import edge_background
old_dir,new_dir,out=map(pathlib.Path,sys.argv[1:]);out.mkdir(parents=True,exist_ok=True)
old,=old_dir.glob('*.apk');new,=new_dir.glob('*.apk')
verify_checksums(new_dir,{p.name for p in new_dir.iterdir()}-{'SHA256SUMS.txt'})
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(*a):return subprocess.check_output(a,text=True,stderr=subprocess.STDOUT)
def metadata(apk):
    signature=run(str(tools/'apksigner'),'verify','--print-certs',str(apk));badging=run(str(tools/'aapt'),'dump','badging',str(apk))
    identity=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    assert identity and 'application-debuggable' not in badging
    return {'package':identity[1],'versionCode':int(identity[2]),'versionName':identity[3],'certificate_sha256':re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',signature)[1]}
previous,current=metadata(old),metadata(new)
assert previous['package']==current['package']=='com.sakugapp'
assert previous['versionName']=='1.2.0' and current['versionName']=='1.2.1' and current['versionCode']>previous['versionCode']
assert previous['certificate_sha256']==current['certificate_sha256']=='9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff'
package=current['package']
def capture(name):
    with (out/name).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
def launch():run('adb','shell','am','start','-W','-n',package+'/.MainActivity')
def helper(stage):
    log=run('adb','shell','am','instrument','-w','-r','-e','stage',stage,'-e','class','com.sakugapp.upgradecheck.UpgradeFoldersTest','com.sakugapp.upgradecheck.test/androidx.test.runner.AndroidJUnitRunner')
    (out/(stage+'-native-ui.txt')).write_text(log);print(log)
    assert 'OK (1 test)' in log and 'FAILURES' not in log
def read_export(name):
    assert name in ['before-upgrade.json','after-upgrade.json']
    paths=run('adb','shell',f'find /sdcard/Download /sdcard/Documents -name {name} 2>/dev/null || true').splitlines()
    path=next((v for v in paths if v.startswith('/') and v.endswith('/'+name)),None);assert path,'Normal system export file must exist'
    return json.loads(run('adb','shell','cat',path))
try:
    run('adb','shell','svc','power','stayon','true');run('adb','shell','input','keyevent','224');run('adb','shell','wm','dismiss-keyguard')
    run('adb','shell','cmd','uimode','night','yes')
    assert 'Success' in run('adb','install',str(old))
    root=pathlib.Path('upgrade-ui-check/build/outputs/apk')
    for apk in [root/'debug/upgrade-ui-check-debug.apk',root/'androidTest/debug/upgrade-ui-check-debug-androidTest.apk']:
        assert 'Success' in run('adb','install','-r',str(apk))
    run('adb','push','upgrade-ui-check/fixtures/official-v120-favorites.json','/sdcard/Download/official-v120-favorites.json')
    # Make a file pushed by the test runner visible to the ordinary Android document picker.
    run('adb','shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Download/official-v120-favorites.json')
    launch();helper('seed');before=read_export('before-upgrade.json');capture('v120-seeded.png')
    assert len(before['folders'])==3 and len(before['posts'])==2 and len(before['links'])==3 and len(before['tagNotes'])==2
    run('adb','shell','am','force-stop',package);assert 'Success' in run('adb','install','-r',str(new))
    launch();helper('verify');after=read_export('after-upgrade.json');capture('v121-upgraded.png')
    assert before['schemaVersion']==3 and after['schemaVersion']==4
    for key in ['folders','posts','links','tagFavorites','tagNotes']:assert before[key]==after[key],key+' changed during upgrade'
    assert after['subfolders']==[] and after['subfolderLinks']==[]
    raw=subprocess.check_output(['adb','exec-out','screencap']);pixels=edge_background(raw);expected=(245,246,248)
    assert sum(max(abs(a-b) for a,b in zip(p,expected))<=3 for p in pixels)>=len(pixels)*.75,'Saved light theme not rendered'
    report={'previous':previous,'candidate':current,'result':'passed','retained': ['folder names/order/covers and memberships via normal before/after exports; fixed IDs separately tested in migration','two posts in three memberships','known tag favorite and two local notes including an unfavorited tag','light theme and wrap setting'],
            'uninstall_or_clear_data':False,'private_data_injection':False,'seed':'Public-response fixture imported using the old official app and system picker; both exports compared',
            'source_commit':json.loads((new_dir/'release-verification.json').read_text())['source_commit']}
    (out/'upgrade-install.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,sort_keys=True))
except Exception:
    capture('failure.png')
    try:
        run('adb','shell','uiautomator','dump','/sdcard/upgrade-failure.xml');(out/'failure-ui.xml').write_text(run('adb','shell','cat','/sdcard/upgrade-failure.xml'))
    except Exception:pass
    raise
