"""Read-only JMeter CSV analysis; no synthetic evidence."""
import argparse, csv, json, math, hashlib, sys
from pathlib import Path
from collections import defaultdict, Counter
from statistics import mean

def percentile(values, p):
    s = sorted(values); h = (len(s)-1)*p; i = int(h)
    return s[i]+(s[min(i+1,len(s)-1)]-s[i])*(h-i)

def dist(values):
    return {'min':min(values),'max':max(values),'mean':mean(values), **{k:percentile(values,p) for k,p in [('median',.5),('p90',.9),('p95',.95),('p99',.99)]}}

def summary(rows, seconds):
    n=len(rows); failures=[r for r in rows if not r['success']]
    result={'samples':n,'failures':len(failures),'error_percent':100*len(failures)/n,'samples_per_second':n/seconds if seconds>0 else None,'elapsed_ms':dist([r['elapsed'] for r in rows]),'failed_response_codes':dict(Counter(r['responseCode'] for r in failures))}
    for key in ('Latency','Connect'):
        if key in rows[0]: result[key+'_ms']=dist([r[key] for r in rows])
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('jtl',type=Path); p.add_argument('--sample-kind',required=True,choices=['http','transaction','unknown']); p.add_argument('--label',action='append'); p.add_argument('--timestamp',choices=['start','end'],default='start'); p.add_argument('--start-ms',type=int); p.add_argument('--end-ms',type=int); p.add_argument('--bucket-seconds',type=int,default=60); p.add_argument('--output',type=Path); a=p.parse_args()
    if a.bucket_seconds<=0: p.error('bucket must be positive')
    if (a.start_ms is None)!=(a.end_ms is None) or (a.start_ms is not None and a.end_ms<=a.start_ms): p.error('provide both bounds with end > start')
    if a.sample_kind=='unknown' and not a.label: p.error('unknown sample kind requires explicit labels; use per-label results only')
    rows=[]; all_labels=Counter(); required={'timeStamp','elapsed','label','success','responseCode'}
    with a.jtl.open(encoding='utf-8-sig',newline='') as f:
        reader=csv.DictReader(f)
        if not required.issubset(reader.fieldnames or []): raise ValueError('missing required columns: '+str(required-set(reader.fieldnames or [])))
        if len(set(reader.fieldnames))!=len(reader.fieldnames): raise ValueError('duplicate header')
        for line,r in enumerate(reader,2):
            if None in r or any(v is None for v in r.values()): raise ValueError(f'malformed row {line}')
            try:
                ts=int(r['timeStamp']); elapsed=int(r['elapsed']); success=r['success'].lower()
                if ts<0 or elapsed<0 or success not in ('true','false') or not r['label'].strip(): raise ValueError()
                row={'start':ts if a.timestamp=='start' else ts-elapsed,'elapsed':elapsed,'success':success=='true','label':r['label'],'responseCode':r['responseCode']}
                if row['start']<0: raise ValueError()
                for key in ('Latency','Connect'):
                    if key in r:
                        row[key]=int(r[key]);
                        if row[key]<0: raise ValueError()
            except (ValueError,TypeError): raise ValueError(f'invalid metrics at row {line}') from None
            all_labels[row['label']]+=1
            if a.label and row['label'] not in a.label: continue
            if a.start_ms is not None and not a.start_ms<=row['start']<a.end_ms: continue
            rows.append(row)
    if a.label and set(a.label)-set(all_labels): raise ValueError('requested labels missing')
    if not rows: raise ValueError('no selected samples')
    lo=a.start_ms if a.start_ms is not None else min(r['start'] for r in rows); hi=a.end_ms if a.end_ms is not None else max(r['start']+r['elapsed'] for r in rows); seconds=(hi-lo)/1000
    groups=defaultdict(list); buckets=defaultdict(list); width=a.bucket_seconds*1000
    for r in rows:
        groups[r['label']].append(r); buckets[(r['start']-lo)//width].append(r)
    result={'file':str(a.jtl),'sha256':hashlib.sha256(a.jtl.read_bytes()).hexdigest(),'sample_kind_user_declared':a.sample_kind,'timestamp_mode':a.timestamp,'window_start_ms':lo,'window_end_ms':hi,'window_seconds':seconds,'window_policy':'explicit half-open start selection' if a.start_ms is not None else 'selected sample span','percentile_method':'linear interpolation (n-1)*p','includes_failed_samples':True,'all_label_counts':dict(all_labels),'warnings':['Parent/child type cannot be inferred reliably; verify sample kind. Buckets use sample start, not completion. Empty buckets omitted.'], 'by_label':{k:summary(v,seconds) for k,v in sorted(groups.items())}}
    if a.sample_kind!='unknown':
        result['overall']=summary(rows,seconds); result['buckets']=[{'start_ms':lo+i*width,'end_ms':min(hi,lo+(i+1)*width),**summary(v,max(0,min(hi,lo+(i+1)*width)-(lo+i*width))/1000)} for i,v in sorted(buckets.items())]
    out=json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)
    if a.output:
        with a.output.open('x',encoding='utf-8') as f: f.write(out+'\n')
    else: print(out)
if __name__=='__main__':
    try: main()
    except (ValueError,OSError,csv.Error) as e: print(f'ERROR: {e}',file=sys.stderr); sys.exit(1)
