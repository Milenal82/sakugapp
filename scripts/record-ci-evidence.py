#!/usr/bin/env python3
"""Record public test results and original screenshots on a separate evidence branch; never copy APKs/keys."""
import datetime,json,os,pathlib,shutil,subprocess,xml.etree.ElementTree as ET
root=pathlib.Path(os.environ['EVIDENCE_DOWNLOADS']);out=pathlib.Path(os.environ['EVIDENCE_OUTPUT']);out.mkdir(parents=True,exist_ok=True)
repo=os.environ['GITHUB_REPOSITORY'];run=os.environ['GITHUB_RUN_ID'];sha=os.environ['GITHUB_SHA']
def api(path):return json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/{path}']))
jobs=api(f'actions/runs/{run}/jobs')['jobs'];artifacts=api(f'actions/runs/{run}/artifacts')['artifacts']
report={'source_commit':sha,'run_id':run,'run_url':f'https://github.com/{repo}/actions/runs/{run}','captured_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
'jobs':{j['name']:{'status':j['status'],'conclusion':j['conclusion']} for j in jobs if j['name'] in ['build','device-tests','signed-candidate','candidate-upgrade-install']},
'artifacts':[{'name':a['name'],'url':f'https://github.com/{repo}/actions/runs/{run}/artifacts/{a["id"]}','bytes':a['size_in_bytes']} for a in artifacts], 'tests':{},'failures':[]}
# Full JUnit XML stays in Actions. Only aggregate counts and assertion summaries are committed.
for variant in ['Debug','Release']:
    paths=list(root.rglob(f'test{variant}UnitTest/TEST-*.xml'))
    if paths:
        suites=[ET.parse(p).getroot() for p in paths]
        report['tests'][variant]={key:sum(int(s.get(key,'0')) for s in suites) for key in ['tests','failures','errors','skipped']}
paths=[p for p in root.rglob('TEST-*.xml') if 'androidTest-results' in str(p)]
if paths:
    suites=[ET.parse(p).getroot() for p in paths]
    report['tests']['device']={key:sum(int(s.get(key,'0')) for s in suites) for key in ['tests','failures','errors','skipped']}
for path in root.rglob('TEST-*.xml'):
    for case in ET.parse(path).getroot().iter('testcase'):
        failure=case.find('failure')
        if failure is not None:report['failures'].append({'test':case.get('classname','')+'.'+case.get('name',''),'message':(failure.text or failure.get('message',''))[:5000]})
report['lint']={}
for variant in ['debug','release']:
    paths=list(root.rglob(f'lint-results-{variant}.xml'))
    if paths:
        issues=ET.parse(paths[0]).getroot().findall('issue')
        report['lint'][variant]={'errors':sum(i.get('severity') in ['Error','Fatal'] for i in issues),'warnings':sum(i.get('severity')=='Warning' for i in issues)}
verification=list(root.rglob('release-verification.json'))
if verification:report['candidate']=json.loads(verification[0].read_text())
upgrades=list(root.rglob('upgrade-install.json'))
if upgrades:report['upgrade']=json.loads(upgrades[0].read_text())
# Extract compiler/failed step diagnostics if no JUnit report could be produced.
for job in jobs:
    if job.get('conclusion')=='failure' and job['name'] in report['jobs']:
        log=subprocess.run(['gh','api',f'repos/{repo}/actions/jobs/{job["id"]}/logs'],capture_output=True)
        if log.returncode==0:
            lines=log.stdout.decode(errors='replace').splitlines()
            selected=[line for line in lines if any(x in line for x in ['e: file:', 'FAILED','AssertionError','What went wrong','Caused by:','FAILURES!!!','##[error]'])]
            report['failures'].append({'job':job['name'],'message':'\n'.join(selected)[-12000:]})
images=out/'screenshots';images.mkdir(exist_ok=True)
for name in ['history-page.png','tag-favorites-page.png','tag-note-editor.png','language-fr.png','language-es.png','language-ru.png','language-ko.png','posts-refreshing.png','posts-refresh-failed.png','six-columns-large-font.png','six-columns-landscape.png']:
    found=list(root.rglob(name))
    if len(found)==1:shutil.copyfile(found[0],images/name)
(out/'report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
(out/'README.md').write_text(f'# Development verification\n\nSource: `{sha}`\n\nRun: {report["run_url"]}\n\nOriginal API 28 x86_64 emulator captures. Local collection/history screens use legally captured public-response fixtures; no fictional posts or account data. Refresh failure uses a controlled HTTP error. Language screens change only native UI. Full logs and reports remain in Actions artifacts. This branch contains no APK, credentials, signing files or media downloads.\n')
print('Public verification summary recorded for',sha,report['tests'])
