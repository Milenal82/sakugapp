#!/usr/bin/env python3
"""Real emulator upgrade: retain the published APK's settings/history, without uninstall or clear-data."""
import json,os,pathlib,re,subprocess,sys,time,xml.etree.ElementTree as ET
from release_common import metadata,verify_checksums
old_dir,candidate_dir,out=map(pathlib.Path,sys.argv[1:4]);out.mkdir(parents=True,exist_ok=True)
old_files=list(old_dir.glob('*.apk'));new_files=list(candidate_dir.glob('*.apk'))
if len(old_files)!=1 or len(new_files)!=1:raise SystemExit('Exactly one previous and one candidate APK are required.')
old,new=old_files[0],new_files[0]
verify_checksums(candidate_dir,{p.name for p in candidate_dir.iterdir()}-{'SHA256SUMS.txt'})
sdk=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(*args):return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT)
def apk_info(path):
    signature=run(str(sdk/'apksigner'),'verify','--print-certs',str(path))
    fingerprint=re.findall(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)',signature)
    badging=run(str(sdk/'aapt'),'dump','badging',str(path))
    match=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    assert match and len(fingerprint)==1,'Invalid signed APK metadata'
    assert 'application-debuggable' not in badging,'Only non-debuggable official signatures are tested here'
    return {'package':match[1],'versionCode':int(match[2]),'versionName':match[3],'certificate_sha256':fingerprint[0].lower()}
previous,current=apk_info(old),apk_info(new)
assert previous['package']==current['package']==metadata()['applicationId']
assert previous['certificate_sha256']==current['certificate_sha256'],'Candidate certificate differs from the published APK'
assert current['versionCode']>previous['versionCode'],'Candidate does not increase versionCode'
package=current['package']
def hierarchy():
    run('adb','shell','uiautomator','dump','/sdcard/sakugapp-upgrade-window.xml')
    raw=run('adb','shell','cat','/sdcard/sakugapp-upgrade-window.xml')
    return ET.fromstring(raw[raw.index('<'):])
def wait_node(predicate,timeout=45):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        for node in hierarchy().iter('node'):
            if predicate(node):return node
        time.sleep(0.5)
    raise AssertionError('Expected native UI element did not appear')
def settings_switch(label,checked=None):
    dimensions=run('adb','shell','wm','size')
    match=re.findall(r'(\d+)x(\d+)',dimensions)[-1]
    width,height=map(int,match)
    for _ in range(12):
        for node in hierarchy().iter('node'):
            bounds=re.findall(r'\d+',node.get('bounds',''))
            if node.get('content-desc')==label and (checked is None or node.get('checked')==checked) and len(bounds)==4 and int(bounds[3])>int(bounds[1]):return node
        run('adb','shell','input','swipe',str(width//2),str(int(height*0.82)),str(width//2),str(int(height*0.3)),'350')
        time.sleep(0.25)
    raise AssertionError('Native setting switch not found: '+label)
def tap(node):
    x1,y1,x2,y2=map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds')).groups())
    run('adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def tap_text(value):tap(wait_node(lambda n:n.get('text')==value))
def screenshot(name):
    with (out/name).open('wb') as output:subprocess.run(['adb','exec-out','screencap','-p'],stdout=output,check=True)
def launch():run('adb','shell','am','start','-W','-n',package+'/.MainActivity')
try:
    assert 'Success' in run('adb','install',str(old))
    launch();wait_node(lambda n:n.get('text')=='Menu')
    tap(wait_node(lambda n:n.get('class')=='android.widget.EditText'))
    run('adb','shell','input','text','id:319947');tap_text('Search')
    tap_text('Menu');tap_text('Settings')
    switch=settings_switch('Light background')
    if switch.get('checked')!='true':tap(switch)
    settings_switch('Light background','true')
    wrap=settings_switch('Wrap tags')
    if wrap.get('checked')=='true':tap(wrap)
    settings_switch('Wrap tags','false')
    screenshot('before-upgrade.png')
    run('adb','shell','am','force-stop',package)
    assert 'Success' in run('adb','install','-r',str(new))
    launch();tap_text('Menu');tap_text('Settings')
    wait_node(lambda n:n.get('content-desc')=='Light' and (n.get('selected')=='true' or n.get('checked')=='true'))
    settings_switch('Wrap tags','false')
    screenshot('after-upgrade-settings.png')
    tap_text('Menu');tap_text('Posts')
    tap(wait_node(lambda n:n.get('content-desc')=='Search' and n.get('clickable')=='true'))
    tap_text('History')
    wait_node(lambda n:n.get('text')=='id:319947' and n.get('class')!='android.widget.EditText')
    screenshot('after-upgrade-history.png')
    result={'previous':previous,'candidate':current,'retained_light_theme':True,'retained_wrap_tags':True,'retained_search_history':True,'uninstall_or_clear_data':False,'result':'passed'}
    (out/'upgrade-install.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Public upgrade verification:',json.dumps(result,sort_keys=True))
    print('Published APK -> signed candidate upgrade succeeded; settings and search history retained.')
except Exception:
    print('Theme options:',[n.attrib for n in hierarchy().iter('node') if n.get('content-desc') in ['Light','Dark','Follow system theme']])
    screenshot('failure.png')
    (out/'failure-ui.xml').write_text(ET.tostring(hierarchy(),encoding='unicode'))
    raise
