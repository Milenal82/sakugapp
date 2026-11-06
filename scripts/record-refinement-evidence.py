#!/usr/bin/env python3
"""Bounded public test metadata and fixture captures; no APKs, credentials or signing files."""
import io,json,os,pathlib,re,shutil,subprocess,time,zipfile
import xml.etree.ElementTree as ET
repo=os.environ['GITHUB_REPOSITORY'];sha=os.environ['GITHUB_SHA'];run=os.environ['GITHUB_RUN_ID']
root=pathlib.Path(os.environ['EVIDENCE_DOWNLOADS']);out=pathlib.Path(os.environ['EVIDENCE_OUTPUT']);out.mkdir(parents=True,exist_ok=True);(out/'screenshots').mkdir(exist_ok=True)
def api(path):return json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/'+path]))
def artifact_urls(run_id):return [{'name':a['name'],'url':f'https://github.com/{repo}/actions/runs/{run_id}/artifacts/{a["id"]}'}for a in api(f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']]
def summarize(name,data):
 try:r=ET.fromstring(data)
 except ET.ParseError:return {'report':name,'invalid':True}
 if pathlib.PurePosixPath(name).name.startswith('lint-results-'):
  return {'report':name,'errors':sum(i.get('severity')in['Fatal','Error']for i in r.findall('issue')),'warnings':sum(i.get('severity')=='Warning'for i in r.findall('issue'))}
 return {'report':name,'counts':r.attrib,'failures':[{'test':c.get('classname','')+'.'+c.get('name',''),'message':(e.text or e.get('message',''))[:7000]}for c in r.iter('testcase')for e in list(c.findall('failure'))+list(c.findall('error'))]}
report={'source_commit':sha,'run_url':f'https://github.com/{repo}/actions/runs/{run}','validation_result':os.environ['VALIDATION_RESULT'],'reports':[],'scope':'Native fixture UI, four palettes × light/dark × 75/100/125%, prerequisite functionality and actual standard CI. No real-account claim.'}
for p in root.rglob('*'):
 if not p.is_file() or p.stat().st_size>4*1024*1024:continue
 if p.name.startswith('TEST-')and p.suffix=='.xml':report['reports'].append(summarize(str(p.relative_to(root)),p.read_bytes()))
 if p.name.startswith('v130-')and p.suffix=='.png':shutil.copyfile(p,out/'screenshots'/(('phone-' if 'reports-28' in str(p) else 'tablet-')+p.name))
 if p.name=='v130-layout-matrix.json':report.setdefault('layout_matrices',[]).append({'report':str(p.relative_to(root)),'cases':json.loads(p.read_text())})
report['artifacts']=artifact_urls(run)
deadline=time.monotonic()+28*60;started=time.monotonic();match=None
while time.monotonic()<deadline:
 runs=api(f'actions/workflows/android.yml/runs?event=push&head_sha={sha}&per_page=30')['workflow_runs']
 matches=[r for r in runs if r['head_sha']==sha and r['head_branch']==os.environ['GITHUB_REF_NAME']]
 if matches:
  match=max(matches,key=lambda r:r['id'])
  if match['status']=='completed':break
 elif time.monotonic()-started>150:break
 time.sleep(20)
if match:
 standard={'run_url':match['html_url'],'conclusion':match['conclusion'],'status':match['status'],'reports':[],'artifacts':artifact_urls(match['id'])}
 for a in api(f'actions/runs/{match["id"]}/artifacts?per_page=100')['artifacts']:
  if a['name']not in ['check-reports','device-test-reports','search-back-gesture-reports','six-column-phone-reports','six-column-landscape-reports']or a['size_in_bytes']>64*1024*1024:continue
  raw=subprocess.check_output(['gh','api',f'repos/{repo}/actions/artifacts/{a["id"]}/zip'])
  with zipfile.ZipFile(io.BytesIO(raw))as z:
   for p in z.infolist():
    name=pathlib.PurePosixPath(p.filename).name
    if p.file_size<=4*1024*1024 and name.endswith('.xml')and(name.startswith('TEST-')or name.startswith('lint-results-')):standard['reports'].append(summarize(a['name']+'/'+p.filename,z.read(p)))
 standard['jobs']=[{'name':j['name'],'conclusion':j['conclusion'],'url':j['html_url']}for j in api(f'actions/runs/{match["id"]}/jobs?per_page=100')['jobs']]
 report['standard_checks']=standard
else:report['standard_checks']={'status':'not_found'}
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'source_commit':sha,'validation_result':report['validation_result'],'standard_checks':report['standard_checks'].get('conclusion'),'reports':len(report['reports'])}))
