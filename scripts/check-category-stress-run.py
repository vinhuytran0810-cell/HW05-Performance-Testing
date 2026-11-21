"""Stress preflight/postcheck. Reuses frozen Load read-only credential/baseline helpers.
Never logs in, mutates SQLite, or generates workload. No credential values in output.
"""
import argparse, collections, csv, hashlib, importlib.util, json, os, re
from pathlib import Path
spec = importlib.util.spec_from_file_location('hw05_load_checks', Path(__file__).with_name('check-category-load-run.py'))
load = importlib.util.module_from_spec(spec)
spec.loader.exec_module(load)
ROOT = Path(__file__).resolve().parents[1]
STRESS_EXPECTED = {'test-plans/23120274_Stress_20261119.jmx': '1f4a70496facf5c4fcd110601fbdcc2e3fb78a1a3066bc79d1801a17f7af423b', 'scripts/category-stress-support.groovy': '8dd7091b68cc494337c142c9a597bec45fafcfcd2f6bf7574cf8a7f8013e797d', 'scripts/run-category-stress.ps1': '5a96cada9d17d6e24fefe2ab13be93a3f295440569ce09ab137ede0f70ff57a9', 'scripts/run-category-load.ps1': '610980a699d2e48697936718d06e7a23c39ac853fd88c12cb9b2b38f67a822d7', 'scripts/check-category-load-run.py': '94642d3f5cbe418d15517758c386a5db1eaad5fe5b715e0c7fcda34713f6ee7d'}
PATHS = ['test-plans/23120274_Stress_20261119.jmx', 'scripts/category-stress-support.groovy',
         'scripts/run-category-stress.ps1', 'scripts/check-category-stress-run.py']

def fingerprint():
    return {**load.fingerprint(ROOT), **{p: hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in PATHS}}

def preflight():
    result = load.preflight(ROOT)
    hashes = fingerprint()
    load.require(all(hashes[p] == h for p,h in STRESS_EXPECTED.items()), 'STRESS_REVIEWED_HASH_CHANGED')
    # No pending owned test categories may silently become baseline.
    load.require(not any(c['name'].startswith('HW05_23120274_') for c in result['baseline']['categories']), 'UNRESOLVED_OWNED_CATEGORY')
    result.update(kind='stress_preflight', hashesBefore=hashes)
    return result

def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def read_lines(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines()]

def postcheck(run_dir):
    pre,ex = read_json(run_dir/'preflight.json'),read_json(run_dir/'execution.json')
    checks={}; result={'kind':'stress_task1_postcheck','checkedAt':load.now(),'runId':ex['runId'],
                      'mode':ex['mode'],'status':'REVIEW_REQUIRED','checks':checks,'issues':[]}
    try:
        after=load.snapshot(ROOT)
        load.write_new(run_dir/'post-run-baseline.json',{'checkedAt':load.now(),**after})
        checks['hashesUnchanged']=fingerprint()==pre['hashesBefore']
        checks['categoriesUnchanged']=load.canonical(after['categories'])==load.canonical(pre['baseline']['categories'])
        checks['productsUnchanged']=load.canonical(after['products'])==load.canonical(pre['baseline']['products'])
        checks['adminCleanUnchanged']=after['admin']==pre['baseline']['admin']
        checks['nativeExitZero']=ex['nativeLauncherExitCode']==0
        checks['sameBackend']=ex['backendProcessUnchanged']
        checks['monitorCompleted']=ex['monitorJobState']=='Completed' and ex['monitorErrorCount']==0
        checks['dashboardExists']=(run_dir/'html/index.html').exists()
        with (run_dir/'samples.jtl').open(encoding='utf-8-sig',newline='') as f:
            reader=csv.DictReader(f);rows=list(reader);columns=reader.fieldnames or []
        result['rawJtlSHA256']=hashlib.sha256((run_dir/'samples.jtl').read_bytes()).hexdigest()
        result['jtlColumns']=columns;result['samples']=len(rows)
        result['failedSamples']=sum(r['success'].lower()!='true' for r in rows)
        result['httpNon200Samples']=sum(r['responseCode']!='200' for r in rows)
        result['failed200Samples']=sum(r['success'].lower()!='true' and r['responseCode']=='200' for r in rows)
        result['byLabel']=dict(collections.Counter(r['label'] for r in rows))
        checks['onlyExpectedHttpLabels']=bool(rows) and set(result['byLabel']).issubset(load.LABELS)
        checks['fiveLabelsPresent']=set(result['byLabel'])==set(load.LABELS)
        checks['timestampAndThreadsPresent']=all(k in columns for k in ['timeStamp','elapsed','allThreads','grpThreads']) and all(int(r['timeStamp'])>0 and int(r['allThreads'])>0 for r in rows)
        checks['noSensitiveJtlColumns']=not set(columns).intersection({'responseData','samplerData','requestHeaders','responseHeaders','password','token'})
        checks['safeDashboardMessages']=all(re.fullmatch(r'HW05_HTTP_[0-9]{3}|HW05_TRANSPORT_FAILURE|HW05_PRE_MUTATION_AVAILABILITY_FAILURE',r.get('responseMessage','')) for r in rows)
        manifest=ROOT/'audit/manifests'/ex['runId']/ex['engineId']
        baseline=read_json(manifest/'baseline.json')
        checks['manifestBaselineMatchesPreflight']=load.canonical(baseline['baselineCategories'])==load.canonical(pre['baseline']['categories']) and load.canonical(baseline['baselineProducts'])==load.canonical(pre['baseline']['products']) and baseline['adminBefore']==pre['baseline']['admin']
        groups=collections.defaultdict(list)
        for f in manifest.glob('thread-*.jsonl'):
            for e in read_lines(f): groups[e['intentId']].append(e)
        complete=[];workers=set();ok=True;names=set();size=ex['workload']['cohortThreads']
        for intent,events in groups.items():
            first=events[0];worker=first['thread'];name=first['categoryName'];workers.add(worker)
            ok &= 1<=worker<=5*size and name not in names and f'_T{worker}_I{first["iteration"]}_' in name
            names.add(name)
            ok &= all(e['runId']==ex['runId'] and e['engineId']==ex['engineId'] and e['thread']==worker and e['categoryName']==name and e['iteration']==first['iteration'] and e['baselineHash']==baseline['baselineHash'] for e in events)
            ids={e['actualId'] for e in events if 'actualId' in e}
            ok &= len(ids)<=1 and all(isinstance(i,int) and i>0 and i not in {c['id'] for c in baseline['baselineCategories']} for i in ids)
            if [e['stepName'] for e in events]==load.CHAIN:complete.append(first)
        result.update(ownedIntents=len(groups),completeWorkloadIterations=len(complete),workersObserved=sorted(workers),completeWorkers=sorted({e['thread'] for e in complete}))
        checks['manifestOwnershipCorrelation']=bool(groups) and ok
        checks['atLeastOneCompleteIteration']=bool(complete)
        recovery=read_lines(manifest/'recovery.jsonl')
        cleanup=[e for e in recovery if e['stepName']=='RECOVERY_RESULT'][-1]
        summary=[e for e in recovery if e['stepName']=='RUN_SUMMARY'][-1]
        result.update(cleanupResult=cleanup,runSummary=summary)
        checks['cleanupVerified']=cleanup['cleanupStatus']=='VERIFIED' and not cleanup['unresolvedIntents']
        checks['noSafetyGuardStop']=summary['performanceStatus']=='SCHEDULE_FINISHED_NOT_A_PERFORMANCE_PASS'
        observations=read_lines(manifest/'stress-observations.jsonl')
        start=[e for e in observations if e['stepName']=='MAIN_WORKLOAD_START'][-1]
        quiet=[e for e in observations if e['stepName']=='QUIET_RECOVERY_START'][-1]
        result['softPreMutationFailures']=quiet['softFailures']
        result['mainAnchorEpochMs']=start['epochMs']
        result['anchorDefinition']='First workflow gate, approximate schedule origin; actual active threads retained per sample.'
        result['activeThreadsObserved']=sorted({int(r['allThreads']) for r in rows})
        stages=collections.Counter()
        for r in rows:
            offset=(int(r['timeStamp'])-start['epochMs'])/1000
            stage=int(offset//80)+1 if 0<=offset<400 else 0
            stages[str(stage)]+=1
        result['nominal80SecondStageSampleCounts']=dict(stages) if ex['mode']=='official' else None
        with (run_dir/'backend-resources.csv').open(encoding='utf-8-sig',newline='') as f:resources=list(csv.DictReader(f))
        checks['resourcesPresent']=len(resources)>=2 and all(r['sameBackendProcess'].lower()=='true' for r in resources)
        checks['noMonitorAlert']=not (run_dir/'monitor-alert.json').exists()
        result['resourceSampleCount']=len(resources)
        if ex['mode']=='pilot':
            checks['pilotFiveCohortsComplete']=set(result['completeWorkers'])==set(range(1,6))
            checks['pilotNoHttpOrAssertionFailure']=result['failedSamples']==0 and result['httpNon200Samples']==0
        findings=[]
        files=[f for f in run_dir.rglob('*') if f.is_file()]+[f for f in manifest.rglob('*') if f.suffix in ['.json','.jsonl']]
        for f in files:
            raw=f.read_bytes()
            if any(os.environ.get(k,'').encode() and os.environ[k].encode() in raw for k in ['ESHOP_ADMIN_EMAIL','ESHOP_ADMIN_PASSWORD']):findings.append({'path':str(f.relative_to(ROOT)),'kind':'credential literal'})
            if re.search(rb'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',raw):findings.append({'path':str(f.relative_to(ROOT)),'kind':'JWT pattern'})
        result['secretScan']={'filesScanned':len(files),'findings':findings};checks['noKnownSecrets']=not findings
        if checks and all(checks.values()):result['status']='ARTIFACTS_AND_CLEANUP_VERIFIED_NOT_SLA_PASS'
    except Exception:
        result['issues'].append('Postcheck incomplete; preserve raw artifacts and inspect locally. No reset or broad deletion attempted.')
    result['limitations']=['Error counts come from raw JTL, independently of safety abort/performanceStatus.','No SLA/capacity verdict.','Secret scan is not universal.','Pilot does not validate 50VU,400s schedule or forced-stop paths.']
    load.write_new(run_dir/'postcheck.json',result)
    return {k:result.get(k) for k in ['status','checks','samples','failedSamples','httpNon200Samples','completeWorkloadIterations','workersObserved','completeWorkers','issues']}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('mode',choices=['preflight','postcheck']);parser.add_argument('--run-dir',type=Path);a=parser.parse_args()
    try:
        if a.mode=='preflight':r=preflight()
        else:
            load.require(a.run_dir is not None,'MISSING_RUN_DIR');d=a.run_dir.resolve();load.require(d.is_relative_to(ROOT/'results/stress'),'WRONG_RESULT_PATH');r=postcheck(d)
        print(json.dumps(r,ensure_ascii=True));return 0 if r['status'] in ['READY','ARTIFACTS_AND_CLEANUP_VERIFIED_NOT_SLA_PASS'] else 2
    except Exception:
        print(json.dumps({'status':'BLOCKED','reason':'Check backend/RAM/credentials/clean baseline/reviewed hashes; no login or DB write performed by checker.'}));return 2
if __name__=='__main__':raise SystemExit(main())
