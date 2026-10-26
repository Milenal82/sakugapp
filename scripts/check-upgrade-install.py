#!/usr/bin/env python3
"""Real emulator upgrade: retain the published APK's settings/history, without uninstall or clear-data."""
import json,os,pathlib,re,subprocess,sys,time,xml.etree.ElementTree as ET
from release_common import metadata,verify_checksums
from screenshot_pixels import edge_background
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
    # Match UiAutomator's standard 100 ms press, rather than a zero-duration tap.
    x,y=str((x1+x2)//2),str((y1+y2)//2)
    run('adb','shell','input','swipe',x,y,x,y,'100')
def tap_text(value):tap(wait_node(lambda n:n.get('text')==value))
def screenshot(name):
    with (out/name).open('wb') as output:subprocess.run(['adb','exec-out','screencap','-p'],stdout=output,check=True)
def launch():run('adb','shell','am','start','-W','-n',package+'/.MainActivity')
try:
    run('adb','shell','svc','power','stayon','true')
    run('adb','shell','input','keyevent','224')
    run('adb','shell','wm','dismiss-keyguard')
    power=run('adb','shell','dumpsys','power')
    assert 'mWakefulness=Awake' in power,'Test device must be awake'
    night=run('adb','shell','cmd','uimode','night','yes')
    assert 'yes' in night.lower(),'Cannot force a dark system theme for the upgrade check'
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
    wait_node(lambda n:n.get('content-desc')=='Light')
    raw=subprocess.check_output(['adb','exec-out','screencap'])
    pixels=edge_background(raw)
    expected=(245,246,248) # AppTheme light background, distinct from the forced-dark system default.
    matching=sum(max(abs(a-b) for a,b in zip(pixel,expected))<=3 for pixel in pixels)
    assert matching>=len(pixels)*0.75,f'Legacy light theme was not rendered after upgrade: {pixels}'
    print('Retained light background samples:',matching,'/',len(pixels),'with system night mode forced on')
    settings_switch('Wrap tags','false')
    screenshot('after-upgrade-settings.png')
    tap_text('Menu');tap_text('Posts')
    # Dropdown/NavHost exit animations can still consume a first physical tap.
    # Synchronize with the real focused field, never infer expansion from a click.
    for attempt in range(3):
        tap(wait_node(lambda n:n.get('resource-id')=='search-entry'))
        try:
            wait_node(lambda n:n.get('package')==package and n.get('resource-id')=='search-input' and n.get('focused')=='true',timeout=5)
            print('Native search field focused after tap attempt',attempt+1)
            break
        except AssertionError:
            if attempt==2:raise
    tap_text('History')
    wait_node(lambda n:n.get('text')=='id:319947' and n.get('class')!='android.widget.EditText')
    screenshot('after-upgrade-history.png')
    result={'previous':previous,'candidate':current,'retained_light_theme':True,'system_night_mode':'yes','theme_verification':'actual rendered background pixels','retained_wrap_tags':True,'retained_search_history':True,'uninstall_or_clear_data':False,'result':'passed'}
    (out/'upgrade-install.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Public upgrade verification:',json.dumps(result,sort_keys=True))
    print('Published APK -> signed candidate upgrade succeeded; settings and search history retained.')
except Exception:
    print('Power state:',[line.strip() for line in run('adb','shell','dumpsys','power').splitlines() if any(key in line for key in ['mWakefulness=','mInteractive=','Display Power:'])])
    print('Focused windows:',[line.strip() for line in run('adb','shell','dumpsys','window','windows').splitlines() if 'mCurrentFocus' in line or 'mFocusedApp' in line])
    print('Search nodes:',[n.attrib for n in hierarchy().iter('node') if any(label in (n.get('text','')+' '+n.get('content-desc','')+' '+n.get('resource-id','')) for label in ['Search','History','search-'])])
    print('Theme options:',[n.attrib for n in hierarchy().iter('node') if n.get('content-desc') in ['Light','Dark','Follow system theme']])
    screenshot('failure.png')
    (out/'failure-ui.xml').write_text(ET.tostring(hierarchy(),encoding='unicode'))
    raise
