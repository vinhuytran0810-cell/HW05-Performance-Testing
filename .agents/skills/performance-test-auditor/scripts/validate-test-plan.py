"""Conservative static JMX review. Findings are not runtime validation."""
import argparse, json, re, sys, datetime
from pathlib import Path
import xml.etree.ElementTree as ET

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('jmx',type=Path); p.add_argument('--student-id'); p.add_argument('--scenario',choices=['Load','Stress','Spike','Endurance']); a=p.parse_args()
    raw=a.jmx.read_text(encoding='utf-8-sig'); findings=[]
    def add(level,code,detail): findings.append({'level':level,'code':code,'detail':detail})
    if re.search(r'<!DOCTYPE|<!ENTITY',raw,re.I): raise ValueError('DTD/entity declarations disallowed')
    root=ET.fromstring(raw)
    if root.tag!='jmeterTestPlan': add('ERROR','ROOT','Expected jmeterTestPlan')
    def walk(tree, enabled=True):
        children=list(tree)
        if len(children)%2: add('ERROR','TREE','Odd hashTree child count')
        for i in range(0,len(children)-1,2):
            node,sub=children[i:i+2]
            if node.tag=='hashTree' or sub.tag!='hashTree': add('ERROR','TREE','Expected element/hashTree pair'); continue
            active=enabled and node.get('enabled','true').lower()!='false'
            yield node,active
            yield from walk(sub,active)
    trees=root.findall('hashTree')
    if len(trees)!=1 or len(list(root))!=1: add('ERROR','TREE','Expected one root hashTree')
    nodes=[n for t in trees for n,active in walk(t) if active]; counts={}
    for n in nodes: counts[n.tag]=counts.get(n.tag,0)+1
    for tags,code in [(('TestPlan',),'PLAN'),(('ThreadGroup','SteppingThreadGroup','UltimateThreadGroup','ConcurrencyThreadGroup'),'THREAD'),(('HTTPSamplerProxy',),'HTTP'),(('CSVDataSet',),'CSV')]:
        if not any(counts.get(t) for t in tags): add('WARN',code,'No recognized enabled component; inspect plugin or scope manually')
    if not any('Assertion' in n.tag for n in nodes): add('WARN','ASSERT','No enabled assertion recognized')
    if not any('Extractor' in n.tag or 'PostProcessor' in n.tag for n in nodes): add('WARN','CORRELATION','No extractor/postprocessor recognized; inspect need for dynamic correlation')
    for n in nodes:
        if n.tag=='ResultCollector' and n.get('guiclass') in ('ViewResultsFullVisualizer','GraphVisualizer'): add('WARN','LISTENER','Heavy listener enabled; disable for load and use offline evidence')
        if n.tag=='CSVDataSet':
            if not (n.findtext("stringProp[@name='filename']") or '').strip(): add('ERROR','CSV_PATH','CSV filename empty')
        if n.tag=='HTTPSamplerProxy' and not (n.findtext("stringProp[@name='HTTPSampler.path']") or '').strip(): add('WARN','HTTP_PATH','Empty sampler path; check defaults')
    for n in root.iter():
        key=n.get('name',''); value=(n.text or '').strip()
        if re.search(r'password|passwd|secret|authorization|api.?key',key,re.I) and value and not ('${' in value or value.lower() in ('false','true','authorization','password')): add('WARN','SECRET','Potential literal credential in XML property; inspect locally (value suppressed)')
        if n.tag=='elementProp' and re.search(r'password|passwd|secret|token|api.?key',n.get('name',''),re.I):
            v=n.findtext("stringProp[@name='Argument.value']") or ''
            if v and '${' not in v: add('WARN','SECRET','Potential literal argument credential (value suppressed)')
        if n.tag=='elementProp':
            v=n.findtext("stringProp[@name='Header.value']") or ''
            if v and (n.findtext("stringProp[@name='Header.name']") or '').lower()=='authorization' and '${' not in v: add('WARN','SECRET','Literal authorization header (value suppressed)')
    if re.search(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',raw): add('WARN','JWT','Possible literal JWT; value suppressed')
    match=re.fullmatch(r'([^_]+)_(Load|Stress|Spike|Endurance)_(\d{8})\.jmx',a.jmx.name)
    if not match: add('WARN','FILENAME','Expected StudentID_Scenario_YYYYMMDD.jmx')
    else:
        try: datetime.datetime.strptime(match[3],'%Y%m%d')
        except ValueError: add('WARN','DATE','Invalid filename date')
        if a.student_id and match[1]!=a.student_id: add('WARN','STUDENT','StudentID differs from supplied ID')
        if a.scenario and match[2]!=a.scenario: add('WARN','SCENARIO','Scenario differs from supplied scenario')
    refs=sorted(set(re.findall(r'\$\{([A-Za-z_][\w.-]*)\}',raw)))
    print(json.dumps({'file':str(a.jmx),'enabled_component_counts':counts,'variable_references_for_manual_review':refs,'findings':findings,'manual_checks':['Variable definitions and stale values across iterations; dynamic Groovy not resolved','Extractor scope, failure guards and assertion semantics','CSV path/EOF/sharing, workload shape and three distinct views across plans','Manifest, ownership, cleanup, secrets in body/scripts and runtime config','Actual JMeter compatibility and pilot execution'], 'status':'STATIC_ERRORS' if any(f['level']=='ERROR' for f in findings) else 'REVIEW_REQUIRED'},ensure_ascii=False,indent=2))
    return int(any(f['level']=='ERROR' for f in findings))
if __name__=='__main__':
    try: sys.exit(main())
    except (ValueError,OSError,ET.ParseError) as e: print(f'ERROR: {e}',file=sys.stderr); sys.exit(1)
