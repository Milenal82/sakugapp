#!/usr/bin/env python3
"""Bounded exact-commit Debug reports; no media, account state, credentials or signing inputs."""
import json, os, pathlib, re, shutil, subprocess
import xml.etree.ElementTree as ET

repo=os.environ['GITHUB_REPOSITORY'];sha=os.environ['GITHUB_SHA'];run=os.environ['GITHUB_RUN_ID']
root=pathlib.Path(os.environ['V134_INPUT']);out=pathlib.Path(os.environ['V134_OUTPUT'])
(out/'screenshots').mkdir(parents=True,exist_ok=True)
report={'source_commit':sha,'branch':os.environ['GITHUB_REF_NAME'],'result':os.environ['V134_RESULT'],
        'run_url':f'https://github.com/{repo}/actions/runs/{run}','reports':[],'layouts':[],'performance':[]}
for path in root.rglob('*'):
    if not path.is_file() or path.stat().st_size>4*1024*1024:continue
    name=path.name;relative=str(path.relative_to(root))
    if name.startswith('TEST-') and name.endswith('.xml'):
        suite=ET.parse(path).getroot();failures=[]
        for case in suite.iter('testcase'):
            for failure in list(case.findall('failure'))+list(case.findall('error')):
                text=failure.text or failure.get('message','')
                text='\n'.join(line for line in text.splitlines() if not re.search(r'password|authorization:|cookie:|bearer ',line,re.I))[:9000]
                failures.append({'test':case.get('classname','')+'.'+case.get('name',''),'message':text})
        report['reports'].append({'file':relative,'counts':suite.attrib,'failures':failures})
    if name in ['lint-results-debug.xml','lint-results-release.xml']:
        issues=ET.parse(path).getroot().findall('issue')
        report.setdefault('lint',{})[name]={'errors':sum(i.get('severity') in ['Error','Fatal'] for i in issues),
                        'warnings':sum(i.get('severity')=='Warning' for i in issues)}
    if name=='v134-unsigned.json':report['unsigned_preflight']=json.loads(path.read_text())
    if name.endswith('-runner.log') and 'content-first-diagnostics' in relative:
        lines=path.read_text(errors='replace').splitlines()
        bounded='\n'.join(line for line in lines if not re.search(r'password|authorization:|cookie:|bearer ',line,re.I))[-24000:]
        report.setdefault('diagnostics',[]).append({'file':relative,'log':bounded})
    if name in ['content-first-layouts.json','content-first-performance.json','scroll-current.json','scroll-baseline.json'] or (name.startswith('content-first-scroll-') and name.endswith('.json')):
        report['layouts' if 'layouts' in name else 'performance'].append({'file':relative,'data':json.loads(path.read_text())})
    if name.startswith('v134-') and name.endswith('.png'):
        prefix='phone-' if 'reports-28' in relative else 'tablet-'
        shutil.copyfile(path,out/'screenshots'/(prefix+name))
def api(path):return json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/'+path]))
report['artifacts']=[{'name':a['name'],'url':f'https://github.com/{repo}/actions/runs/{run}/artifacts/{a["id"]}'}
                     for a in api(f'actions/runs/{run}/artifacts?per_page=100')['artifacts']]
report['jobs']=[{'name':j['name'],'status':j['status'],'conclusion':j['conclusion']}
                for j in api(f'actions/runs/{run}/jobs?per_page=100')['jobs']]
(out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'source_commit':sha,'result':report['result'],'reports':len(report['reports'])}))
