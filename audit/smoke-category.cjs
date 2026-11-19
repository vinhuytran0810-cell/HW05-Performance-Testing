// One sequential Category API smoke; no performance workload or direct DB writes.
// Credentials/JWTs remain in memory. Never import backend/database.js (it resets data).
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const sqlite3 = require('../eshop-sut/backend/node_modules/sqlite3');
const root = path.resolve(__dirname, '..');
const output = path.join(__dirname, '03-category-smoke.json');
if (fs.existsSync(output)) throw new Error('Evidence already exists; preserve it and review before any new run');
const base = 'http://localhost:3000';
const uniqueName = 'HW05_SMOKE_' + Date.now() + '_' + crypto.randomUUID().replaceAll('-', '');
const evidence = {
  schemaVersion:1, startedAt:new Date().toISOString(), timezone:'Asia/Saigon',
  kind:'controlled-sequential-api-smoke', baseUrl:base, temporaryCategoryName:uniqueName,
  steps:[], cleanup:{attempted:false, verified:false}, limitations:[],
};
let db, token, baseline, createdId, createAttempted=false, uncertainCreate=false;
const sourcePaths = ['eshop-sut/backend/server.js','eshop-sut/backend/database.js','eshop-sut/frontend-admin/src/App.jsx','eshop-sut/api_specification.md','eshop-sut/README.md'];
const oldReports = ['reports/task1/01-environment.md','reports/task1/02-api-analysis.md'];
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const hashes = files => Object.fromEntries(files.map(p=>[p,hash(fs.readFileSync(path.join(root,p)))]));
const canonical = rows => JSON.stringify([...rows].sort((a,b)=>a.id-b.id));
const same = (a,b) => canonical(a)===canonical(b);
const persist = () => fs.writeFileSync(output,JSON.stringify(evidence,null,2)+'\n','utf8');
const event = (name,data) => {const step={timestamp:new Date().toISOString(),name,...data};evidence.steps.push(step);persist();console.log(JSON.stringify(step));};
const assert = (condition,description) => {if(!condition) throw new Error(description);};
const getDb = sql => new Promise((resolve,reject)=>db.get(sql,(err,row)=>err?reject(new Error('Read-only DB query failed')):resolve(row)));
const allDb = sql => new Promise((resolve,reject)=>db.all(sql,(err,rows)=>err?reject(new Error('Read-only DB query failed')):resolve(rows)));
const delay = ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function request(method,endpoint,body,credential=token){
  const r=await fetch(base+endpoint,{method,headers:{Accept:'application/json',...(body!==undefined?{'Content-Type':'application/json'}:{}),...(credential?{Authorization:'Bearer '+credential}:{})},body:body!==undefined?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});
  let data; try{data=await r.json();}catch{throw new Error('Unexpected non-JSON API response: '+method+' '+endpoint);}
  return {status:r.status,data};
}
async function list(endpoint){const r=await request('GET',endpoint);assert(r.status===200&&Array.isArray(r.data),'Cannot read baseline '+endpoint);return r.data;}
async function reconcile(){
  let matches=[];
  for(let attempt=0;attempt<(uncertainCreate?4:1);attempt++){
    const categories=await list('/api/categories');matches=categories.filter(c=>c.name===uniqueName);
    if(matches.length) break;
    if(uncertainCreate) await delay(500);
  }
  assert(matches.length<=1,'Ambiguous ownership: duplicate unique smoke name; manual review required');
  if(matches.length){
    const row=matches[0];assert(Number.isSafeInteger(row.id)&&row.id>0&&!baseline.categories.some(c=>c.id===row.id),'Unsafe reconciled category ID');
    if(createdId!==undefined)assert(row.id===createdId,'Response ID differs from reconciled category');
    createdId=row.id;evidence.createdCategoryId=createdId;persist();
  }
  if(uncertainCreate&&!matches.length){evidence.cleanup.lateCreateOutcomeUnresolved=true;persist();}
  return matches;
}
async function deleteOwned(stepName){
  assert(baseline&&Number.isSafeInteger(createdId)&&createdId>0,'Missing owned ID');
  assert(!baseline.categories.some(c=>c.id===createdId),'Refusing baseline category deletion');
  const categories=await list('/api/categories');const row=categories.find(c=>c.id===createdId);
  if(!row){event(stepName,{result:'ALREADY_ABSENT',id:createdId});return;}
  assert(row.name===uniqueName,'Owned category name mismatch; refusing DELETE');
  const products=await list('/api/products');
  assert(!products.some(p=>Number(p.category_id)===createdId),'Products reference temporary category; refusing DELETE');
  evidence.cleanup.attempted=true;persist();
  const r=await request('DELETE','/api/categories/'+createdId);
  event(stepName,{status:r.status,body:r.data,id:createdId});
  assert(r.status===200&&r.data.message==='Category deleted','Delete response contract failed');
}
async function main(){
  evidence.sourceHashesBefore=hashes(sourcePaths);evidence.previousReportHashesBefore=hashes(oldReports);persist();
  // These API snapshots also verify that the existing backend is serving requests.
  baseline={categories:await list('/api/categories'),products:await list('/api/products')};
  evidence.baselineBefore=baseline;
  assert(!baseline.categories.some(c=>c.name===uniqueName),'Unique name already exists');persist();
  db=await new Promise((resolve,reject)=>{const connection=new sqlite3.Database(path.join(root,'eshop-sut/backend/database.sqlite'),sqlite3.OPEN_READONLY,err=>err?reject(new Error('Cannot open database read-only')):resolve(connection));});
  const admin=await getDb("SELECT id,email,password,role,login_attempts,locked_until FROM users WHERE role='admin' ORDER BY id LIMIT 1");
  assert(admin,'Existing admin not found; no new administrator will be created');
  evidence.adminBefore={id:admin.id,role:admin.role,login_attempts:admin.login_attempts,locked_until:admin.locked_until};
  assert(!admin.locked_until||Date.parse(admin.locked_until)<=Date.now(),'Administrator is locked; no unlock/reset attempted');
  assert(admin.login_attempts===0&&admin.locked_until===null,'Admin not at clean login baseline; preserving existing state');
  evidence.databaseSchema={categories:await allDb('PRAGMA table_info(categories)'),categoryIndexes:await allDb('PRAGMA index_list(categories)'),productForeignKeys:await allDb('PRAGMA foreign_key_list(products)')};
  evidence.categorySequenceBefore=await getDb("SELECT seq FROM sqlite_sequence WHERE name='categories'");persist();
  event('Preflight',{backendReachable:true,categories:baseline.categories.length,products:baseline.products.length,adminUnlocked:true});
  const login=await request('POST','/api/login',{email:admin.email,password:admin.password},null);
  assert(login.status===200&&typeof login.data.token==='string'&&login.data.user?.id===admin.id&&login.data.user?.role==='admin','Admin login failed');
  token=login.data.token;const payload=JSON.parse(Buffer.from(token.split('.')[1],'base64url').toString());
  event('1 Admin Login',{status:login.status,userId:admin.id,role:login.data.user.role,tokenPresent:true,expPresent:Object.hasOwn(payload,'exp')});
  const categories=await list('/api/categories');
  assert(same(categories,baseline.categories),'Baseline changed before test mutation; aborting');
  event('2 View Categories',{status:200,count:categories.length,baselineMatches:true});
  const publicRead=await request('GET','/api/categories',undefined,null);
  event('Diagnostic public GET',{status:publicRead.status,count:Array.isArray(publicRead.data)?publicRead.data.length:null});
  for(const credential of [null,'invalid-smoke-token']){
    // If authentication is unexpectedly absent, finally reconciles this test's unique name.
    createAttempted=true;persist();
    let r;try{r=await request('POST','/api/categories',{name:uniqueName},credential);}catch{uncertainCreate=true;throw new Error('Auth probe outcome unknown; reconciling owned name');}
    event(credential?'Diagnostic Create invalid JWT':'Diagnostic Create without JWT',{status:r.status,body:r.status===401||r.status===403?r.data:{unexpected:true}});
    assert(r.status===(credential?403:401),'Create authentication differs from expected guard');
  }
  evidence.createAttemptedAt=new Date().toISOString();createAttempted=true;persist();
  let create;
  try{create=await request('POST','/api/categories',{name:uniqueName});}
  catch{uncertainCreate=true;evidence.createOutcome='UNKNOWN_RECONCILE_REQUIRED';persist();throw new Error('Create timeout/network/parse error; reconciling unique name in finally');}
  assert(create.status===200&&create.data.message==='Category created'&&Number.isSafeInteger(create.data.id)&&create.data.id>0,'Create response contract failed');
  createdId=create.data.id;evidence.createdCategoryId=createdId;
  assert(!baseline.categories.some(c=>c.id===createdId),'Create ID belongs to baseline; refusing deletion');
  event('3 Create Category',{status:create.status,body:{message:create.data.message,id:createdId},name:uniqueName});
  const afterCreate=await list('/api/categories');const matches=afterCreate.filter(c=>c.id===createdId&&c.name===uniqueName);
  assert(matches.length===1&&afterCreate.length===baseline.categories.length+1&&same(afterCreate.filter(c=>c.id!==createdId),baseline.categories),'Created category verification failed');
  event('Diagnostic Verify Creation',{status:200,id:createdId,name:uniqueName,existsExactlyOnce:true,count:afterCreate.length,originalCategoriesUnchanged:true});
  const unauthDelete=await request('DELETE','/api/categories/'+createdId,undefined,null);
  event('Diagnostic Delete without JWT',{status:unauthDelete.status,body:unauthDelete.data,id:createdId});
  assert(unauthDelete.status===401,'Delete missing-token guard differs from expected');
  await deleteOwned('4 Delete Category');
  const afterDelete=await list('/api/categories');
  assert(!afterDelete.some(c=>c.id===createdId||c.name===uniqueName),'Temporary category still exists');
  event('5 Verify Category Deletion',{status:200,absent:true,count:afterDelete.length});
  // Safe nonexistent-ID diagnostic: the ID is exclusively owned by this test and now absent.
  assert(!afterDelete.some(c=>c.id===createdId),'Owned ID must be absent before repeated DELETE');
  const secondDelete=await request('DELETE','/api/categories/'+createdId);
  event('Diagnostic Delete already-deleted owned ID',{status:secondDelete.status,body:secondDelete.data,id:createdId,absentBeforeRequest:true});
  evidence.fiveStepWorkflowPassed=true;
}
(async()=>{
  try{await main();}
  catch(error){evidence.failure=error.message;console.error(error.message);process.exitCode=1;}
  finally{
    try{
      if(baseline&&token&&createAttempted){await reconcile();if(createdId!==undefined)await deleteOwned('Finally guarded cleanup');}
      if(baseline){
        const after={categories:await list('/api/categories'),products:await list('/api/products')};
        evidence.baselineAfter=after;
        evidence.comparison={categoriesUnchanged:same(baseline.categories,after.categories),productsUnchanged:same(baseline.products,after.products),beforeCategoryHash:hash(canonical(baseline.categories)),afterCategoryHash:hash(canonical(after.categories)),beforeProductHash:hash(canonical(baseline.products)),afterProductHash:hash(canonical(after.products))};
        evidence.cleanup.verified=!after.categories.some(c=>c.name===uniqueName)&&!evidence.cleanup.lateCreateOutcomeUnresolved;
        assert(evidence.comparison.categoriesUnchanged&&evidence.comparison.productsUnchanged&&evidence.cleanup.verified,'Baseline/cleanup mismatch; review evidence, do not reset SUT');
      }
      if(db){evidence.adminAfter=await getDb('SELECT id,role,login_attempts,locked_until FROM users WHERE id='+Number(evidence.adminBefore.id));evidence.categorySequenceAfter=await getDb("SELECT seq FROM sqlite_sequence WHERE name='categories'");}
    }catch(error){evidence.cleanup.failure=error.message;console.error(error.message);process.exitCode=1;}
    evidence.sourceHashesAfter=hashes(sourcePaths);evidence.previousReportHashesAfter=hashes(oldReports);
    evidence.sourceUnchanged=JSON.stringify(evidence.sourceHashesBefore)===JSON.stringify(evidence.sourceHashesAfter);
    evidence.previousReportsUnchanged=JSON.stringify(evidence.previousReportHashesBefore)===JSON.stringify(evidence.previousReportHashesAfter);
    evidence.limitations.push('No ordinary-user role-bypass mutation, invalid-name insertion, duplicate insertion or referenced-category deletion was executed; conclusions use handler/schema inspection.','No incorrect administrator password, lockout timing, backend restart, load or concurrency test.','Deleting the temporary category preserves original records but does not rewind SQLite AUTOINCREMENT or database file metadata.','Create timeout reconciliation is implemented; its timeout branch was not intentionally triggered.');
    evidence.finishedAt=new Date().toISOString();
    evidence.executionCompleted=!!evidence.fiveStepWorkflowPassed&&!!evidence.cleanup.verified&&!evidence.failure&&!evidence.cleanup.failure&&evidence.sourceUnchanged&&evidence.previousReportsUnchanged;
    persist();if(db)await new Promise(resolve=>db.close(resolve));token=undefined;
    if(!evidence.executionCompleted)process.exitCode=1;
    console.log(JSON.stringify({evidence:'audit/03-category-smoke.json',executionCompleted:evidence.executionCompleted,comparison:evidence.comparison,cleanup:evidence.cleanup,sourceUnchanged:evidence.sourceUnchanged,previousReportsUnchanged:evidence.previousReportsUnchanged}));
  }
})();
