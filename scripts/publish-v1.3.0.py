#!/usr/bin/env python3
"""Owner-authorized v1.3.0 only. No other tag, public release, key or failed gate can be substituted."""
import json,os,pathlib,re,subprocess,time,xml.etree.ElementTree as ET
from release_common import exact_tag,metadata,verify_checksums,validate_layout_matrix,previous_release_tag
repo=os.environ['GITHUB_REPOSITORY'];assert repo=='Milenal82/sakugapp'
tag='v1.3.0';sha=exact_tag(tag);assert previous_release_tag(tag)=='v1.2.2'
def api(path):return json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/'+path]))
def passed(workflow,branches):
 deadline=time.monotonic()+15*60
 while True:
  runs=api(f'actions/workflows/{workflow}/runs?event=push&head_sha={sha}&per_page=50')['workflow_runs']
  matches=[r for r in runs if r['head_sha']==sha and r['head_branch']in branches]
  if matches:
   latest=max(matches,key=lambda r:r['id'])
   if latest['conclusion']=='success':return latest
   assert latest['status']!='completed',f'Exact-source {workflow} failed; publication stopped.'
  assert time.monotonic()<deadline,f'Exact-source {workflow} must finish successfully before publication.'
  time.sleep(20)
def download(run_id,name,directory):subprocess.run(['gh','run','download',str(run_id),'--repo',repo,'--name',name,'--dir',str(directory)],check=True)
# Existing standard CI remains a gate; this version's UI checks supplement it.
standard=passed('android.yml',['main'])
maintenance=passed('maintenance.yml',['main'])
scoped=passed('refinement.yml',['main','feature/v1.3.0-ui-refinement'])
root=pathlib.Path(os.environ['RUNNER_TEMP'])/'validated-v130'
for api_level in [28,35]:
 folder=root/str(api_level);download(scoped['id'],f'v130-device-reports-{api_level}',folder)
 cases=[c for p in folder.rglob('TEST-*.xml')for c in ET.parse(p).getroot().iter('testcase')]
 assert cases and all(not any(c.find(k)is not None for k in ['failure','error','skipped'])for c in cases)
 for classname,minimum in [('ReleaseUiDeviceTest',3),('MenuAndPoolDeviceTest',3),('ExtensionUiDeviceTest',5),('DiscoveryNavigationDeviceTest',6),('LocalProfileDeviceTest',1),('BackupRestoreDeviceTest',1),('FrameControlsDeviceTest',1),('ClipExportDeviceTest',1),('CommunityUiDeviceTest',1),('PlayerLockDeviceTest',1),('BatchQueueDeviceTest',1),('DownloadFoldersDeviceTest',1),('ThemeSettingsDeviceTest',3),('PlayerStabilityDeviceTest',2),('PostScrollDeviceTest',1)]:
  assert sum(c.get('classname')=='com.sakugapp.'+classname for c in cases)>=minimum,classname+' missing'
 matrices=list(folder.rglob('v130-layout-matrix.json'));assert len(matrices)==1
 validate_layout_matrix(json.loads(matrices[0].read_text()))
upgrade=json.loads((pathlib.Path(os.environ['RUNNER_TEMP'])/'upgrade-install-report/upgrade-install.json').read_text())
assert upgrade['result']=='passed'and upgrade['source_commit']==sha and upgrade['previous']['versionName']=='1.2.2'
assert not upgrade['uninstall_or_clear_data']and not upgrade['private_data_injection']
assert upgrade['existing_and_new_history_verified']and upgrade['preferences_retained']and upgrade['local_profile_restart_verified']
dist=pathlib.Path(os.environ['RELEASE_DIST']);expected={f'Sakugapp-{tag}-release.apk',f'Sakugapp-{tag}-source.zip','release-notes.md','release-verification.json'}
verify_checksums(dist,expected)
report=json.loads((dist/'release-verification.json').read_text());assert report['source_commit']==sha and report['source_tag']==tag
assert report['applicationId']=='com.sakugapp'and report['versionName']=='1.3.0'and report['versionCode']==metadata()['versionCode']==16
assert report['signed']and not report['debuggable']and report['lint_errors']==0 and report['unit_tests']>=182
assert report['certificate_sha256']=='9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff'
# A concurrent newer official release changes the upgrade baseline and must stop this publication.
public=[r['tag_name']for r in api('releases?per_page=100')if not r['draft']and not r['prerelease']]
assert max(public,key=lambda v:tuple(map(int,v[1:].split('.'))))=='v1.2.2'
previous=pathlib.Path(os.environ['PREVIOUS_RELEASE'])
subprocess.run(['gh','release','download','v1.2.2','--repo',repo,'--pattern','*release.apk','--dir',str(previous)],check=True)
old,=previous.glob('*.apk');tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
signature=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(old)],text=True)
assert re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',signature)[1]==report['certificate_sha256']
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(old)],text=True)
identity=re.search(r"package: name='([^']+)' versionCode='(\d+)'",badging)
assert identity[1]==report['applicationId']and int(identity[2])<report['versionCode']
release=json.loads(subprocess.check_output(['gh','release','view',tag,'--repo',repo,'--json','isDraft,targetCommitish,url'],text=True))
assert release['isDraft']and release['targetCommitish']==sha,'An existing public release cannot be modified.'
subprocess.run(['gh','release','edit',tag,'--repo',repo,'--draft=false','--latest'],check=True)
print('Published',release['url'],'source',sha,'standard CI',standard['html_url'],'UI CI',scoped['html_url'],'official certificate',report['certificate_sha256'])
