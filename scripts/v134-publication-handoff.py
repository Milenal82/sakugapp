#!/usr/bin/env python3
"""Use normal Actions permissions for PR/checks/dispatch. Never approves reviews or changes protection."""
import json
import os
import pathlib
import subprocess
import time
from release_v134 import require, passing_run
from release_v134_recovery import checked_candidate_source, checked_source
from release_draft import DRAFT_ID, identity

REPO='Milenal82/sakugapp'
BRANCH='fix/v134-draft-publication'
EVENTS=('push','workflow_dispatch','pull_request')


def gh(*args):
    return subprocess.run(['gh',*args],text=True,capture_output=True,check=True).stdout.strip()


def api(path):
    return json.loads(gh('api',f'repos/{REPO}/'+path))


def matching_runs(runs,sha,branch):
    return [r for r in runs if r['head_sha']==sha and r['head_branch']==branch and r['event']in EVENTS]


def wait_checks(workflow,sha,branch,dispatch=False):
    deadline=time.monotonic()+35*60;requested=False
    while True:
        data=api(f'actions/workflows/{workflow}/runs?head_sha={sha}&per_page=100')['workflow_runs']
        matches=matching_runs(data,sha,branch)
        if matches:
            current=max(matches,key=lambda r:r['id'])
            if current['status']=='completed':
                return passing_run(matches,sha,branch,events=EVENTS)
        elif dispatch and not requested:
            # Explicit workflow_dispatch is necessary after GITHUB_TOKEN creates/merges a PR.
            gh('workflow','run',workflow,'--repo',REPO,'--ref',branch)
            requested=True
        require(time.monotonic()<deadline,'Exact-commit CI is still pending: '+workflow)
        time.sleep(20)


def report(stage,**details):
    data={'stage':stage,**details}
    path=pathlib.Path(os.environ['RUNNER_TEMP'])/'v134-handoff.json'
    path.write_text(json.dumps(data,indent=2)+'\n')
    summary=os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary,'a')as out:out.write('v1.3.4 handoff: `'+stage+'`\n\n'+json.dumps(details,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(data,ensure_ascii=False),flush=True)


def blocked(stage,error,**details):
    # This is an authorization handoff, not a passing verification/publication result.
    text=getattr(error,'stderr',None) or getattr(error,'output',None) or str(error)
    report(stage,error=str(text)[-3000:],published=False,**details)


def main():
    require(os.environ['GITHUB_REPOSITORY']==REPO,'Wrong repository')
    ref=os.environ['GITHUB_REF']
    require(ref in ['refs/heads/'+BRANCH,'refs/heads/main'],'Unexpected automation branch')
    source,head=checked_candidate_source()
    if ref!='refs/heads/main':
        wait_checks('v134-recovery-checks.yml',head,BRANCH)
        existing=json.loads(gh('pr','list','--repo',REPO,'--head',BRANCH,'--base','main','--state','all',
                               '--json','number,url,headRefOid,state,mergeCommit'))
        opened=[p for p in existing if p['state']=='OPEN']
        require(len(opened)<=1,'Multiple conflicting open PRs')
        if opened:
            pr=opened[0];require(pr['headRefOid']==head,'PR head changed')
        elif any(p['state']=='MERGED'and p['headRefOid']==head for p in existing):
            pr=next(p for p in existing if p['state']=='MERGED'and p['headRefOid']==head)
        else:
            body=pathlib.Path(os.environ['RUNNER_TEMP'])/'v134-pr.md'
            body.write_text('Finish the existing v1.3.4 Draft 460623144 using the exact signed and upgrade-tested run 46021416067.\n\n'
                'Only allowlisted release scripts, tests and workflows change. No app source, version, Tag, key or APK changes. '
                'Draft lookup uses list + Release ID, downloads use Asset IDs, and all five payloads are frozen by SHA-256. '
                'A protected environment remains mandatory; this automation does not approve reviews or bypass branch protection.\n')
            try:
                url=gh('pr','create','--repo',REPO,'--base','main','--head',BRANCH,
                       '--title','Finish v1.3.4: verify existing Draft by ID and reuse passing upgrade',
                       '--body-file',str(body))
                pr=json.loads(gh('pr','view',url,'--repo',REPO,'--json','number,url,headRefOid,state,mergeCommit'))
            except subprocess.CalledProcessError as error:
                blocked('owner_pr_required',error,url=f'https://github.com/{REPO}/compare/main...{BRANCH}?expand=1')
                return
        if pr['state']!='MERGED':
            # Do not bypass required PR checks/reviews. Explicit dispatch runs the existing checks normally.
            wait_checks('android.yml',head,BRANCH,dispatch=True)
            report('checks_passed_requesting_normal_merge',pr=pr['url'],head=head,published=False)
            process=subprocess.run(['gh','pr','merge',str(pr['number']),'--repo',REPO,'--merge','--match-head-commit',head],
                                   text=True,capture_output=True)
            if process.returncode:
                blocked('owner_review_or_merge_required',RuntimeError(process.stderr or process.stdout),pr=pr['url'])
                return
            pr=json.loads(gh('pr','view',str(pr['number']),'--repo',REPO,'--json','number,url,headRefOid,state,mergeCommit'))
        require(pr['state']=='MERGED'and pr['mergeCommit'],'PR did not merge')
        merged=pr['mergeCommit']['oid']
        subprocess.run(['git','fetch','origin','main:refs/remotes/origin/main'],check=True)
        subprocess.run(['git','checkout','--detach',merged],check=True)
    source,validator=checked_source()
    # Owner merges already trigger main push CI. Dispatch only for bot merges/manual handoff,
    # avoiding a duplicate dispatch cancelling the real main push run during indexing.
    dispatch_main = ref!='refs/heads/main' or os.environ.get('GITHUB_EVENT_NAME')=='workflow_dispatch'
    wait_checks('v134-recovery-checks.yml',validator,'main',dispatch=dispatch_main)
    release=api(f'releases/{DRAFT_ID}')
    if not release.get('draft'):
        identity(release,source,draft=False)
        require(api('releases/latest')['id']==DRAFT_ID,'Existing public release is not Latest')
        report('already_public_no_changes',release=release['html_url'],published=True)
        return
    identity(release,source)
    runs=api(f'actions/workflows/release.yml/runs?head_sha={validator}&per_page=100')['workflow_runs']
    matches=[r for r in runs if r['head_sha']==validator and r['head_branch']=='main'and r['event']=='workflow_dispatch']
    if matches:
        run=max(matches,key=lambda r:r['id'])
        require(run['status']!='completed'or run['conclusion']=='success','An exact-validator release attempt already failed; inspect it before retrying')
        report('protected_release_already_requested',run=run['html_url'],published=not release['draft'])
        return
    gh('workflow','run','release.yml','--repo',REPO,'--ref','main','-f','tag=v1.3.4',
       '-f','finish_v134_draft=true','-f','reuse_v130_signed=false','-f','reuse_v134_signed=false')
    report('protected_release_requested',validator=validator,published=False,
           actions=f'https://github.com/{REPO}/actions/workflows/release.yml')


if __name__=='__main__':
    try:main()
    except Exception as error:
        report('failed_no_bypass',error=str(error)[-3000:],published=False)
        raise
