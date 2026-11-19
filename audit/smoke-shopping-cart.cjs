// Sequential readiness/functional smoke only. No load generation or database reset.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const sqlite3 = require('../eshop-sut/backend/node_modules/sqlite3');
const root = path.resolve(__dirname, '..');
const output = path.join(__dirname, '02-api-analysis-smoke.json');
const evidence = { startedAt: new Date().toISOString(), kind: 'sequential-functional-smoke', steps: [], limitations: [] };
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const db = new sqlite3.Database(path.join(root, 'eshop-sut/backend/database.sqlite'), sqlite3.OPEN_READONLY);
const get = sql => new Promise((resolve, reject) => db.get(sql, (e, row) => e ? reject(e) : resolve(row)));
const fingerprint = () => Object.fromEntries(['backend/server.js','backend/database.js','backend/package-lock.json','frontend-web/src/context/CartContext.jsx','frontend-web/src/context/AuthContext.jsx','frontend-web/package-lock.json'].map(p => [p, crypto.createHash('sha256').update(fs.readFileSync(path.join(root, 'eshop-sut', p))).digest('hex')]));
let chrome, profile, ws, send, token;
const record = (name, result) => { evidence.steps.push({name, ...result}); console.log(JSON.stringify({name, ...result})); };
async function api(method, endpoint, body, authenticated = true) {
  const r = await fetch('http://localhost:3000' + endpoint, {method, headers:{...(body ? {'Content-Type':'application/json'} : {}), ...(authenticated ? {Authorization:'Bearer '+token} : {})}, body:body ? JSON.stringify(body) : undefined, signal:AbortSignal.timeout(10000)});
  const text = await r.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return {status:r.status, data, contentType:r.headers.get('content-type')};
}
async function evaluate(expression) {
  const r = await send('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true});
  if (r.exceptionDetails) throw new Error('Browser evaluation failed');
  return r.result.value;
}
async function until(expression) {
  for (let i=0; i<80; i++) { const v = await evaluate(expression); if (v) return v; await sleep(150); }
  throw new Error('Browser condition timed out');
}
async function run() {
  evidence.sourceBefore = fingerprint();
  const demo = await get("SELECT id,email,password,login_attempts,locked_until FROM users WHERE email='test@eshop.com' AND role='user'");
  if (!demo) throw new Error('Seeded demo user missing; no account created');
  evidence.demoUserId = demo.id;
  evidence.accountBefore = {login_attempts:demo.login_attempts, locked_until:demo.locked_until};
  if (demo.locked_until && Date.parse(demo.locked_until) > Date.now()) throw new Error('Demo user locked; no reset attempted');
  if (demo.login_attempts !== 0 || demo.locked_until !== null) throw new Error('Demo account has existing login state; preserving it');
  const auth = await api('POST','/api/login',{email:demo.email,password:demo.password},false);
  token = auth.data.token;
  record('API Login', {status:auth.status, tokenPresent:!!token});
  if (auth.status!==200 || !token) throw new Error('Demo login failed');
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());
  evidence.jwtClaims = {id:payload.id, role:payload.role, iatPresent:!!payload.iat, expPresent:Object.hasOwn(payload,'exp')};
  const products = await api('GET','/api/products');
  record('API Product Listing',{status:products.status, count:products.data.length, productIds:products.data.map(p=>p.id)});
  if (products.status!==200 || !products.data.length) throw new Error('No valid product');
  evidence.selectedProduct = {id:products.data[0].id,name:products.data[0].name,price:products.data[0].price,quantity:1};
  const baseline = await api('GET','/api/cart');
  if (baseline.status!==200 || !Array.isArray(baseline.data)) throw new Error('Cart baseline unreadable');
  record('Backend cart baseline',{status:baseline.status,count:baseline.data.length});
  const baselineCart = JSON.stringify(baseline.data);
  for (const [method,endpoint,body] of [['PUT','/api/cart',{id:products.data[0].id,quantity:2}],['DELETE','/api/cart/'+products.data[0].id],['PUT','/api/cart/'+products.data[0].id,{quantity:2}],['DELETE','/api/cart']]) {
    const r=await api(method,endpoint,body);
    record('Missing route probe',{method,endpoint,status:r.status,contentType:r.contentType,defaultCannotResponse:typeof r.data==='string' && r.data.includes('Cannot '+method)});
  }
  const noAuth = await api('GET','/api/cart',undefined,false);
  record('Missing token check',{status:noAuth.status,body:noAuth.data});
  const savedToken=token; token='invalid-demo-token';
  const badAuth=await api('GET','/api/cart'); token=savedToken;
  record('Invalid token check',{status:badAuth.status,body:badAuth.data});
  // An isolated real browser tests the frontend cart without creating an unremovable backend item.
  profile = fs.mkdtempSync(path.join(os.tmpdir(), 'hw05-cart-smoke-'));
  chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new','--remote-debugging-port=0','--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','about:blank'], {windowsHide:true, stdio:'ignore'});
  chrome.on('error',()=>{});
  let port;
  for(let i=0;i<80;i++){ const p=path.join(profile,'DevToolsActivePort'); if(fs.existsSync(p)){port=fs.readFileSync(p,'utf8').split('\n')[0];break;} await sleep(150); }
  if(!port) throw new Error('Headless Chrome did not start');
  const browserInfo = await (await fetch('http://127.0.0.1:'+port+'/json/version')).json();
  evidence.browser = browserInfo.Browser;
  const tabs = await (await fetch('http://127.0.0.1:'+port+'/json/list')).json();
  const tab=tabs.find(t=>t.type==='page');
  ws=new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
  let nextId=0; const pending=new Map(); const network=[];
  ws.addEventListener('message',event=>{const m=JSON.parse(event.data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}} else if(m.method==='Network.requestWillBeSent' && m.params.request.url.startsWith('http://localhost:3000/api/')) {const u=new URL(m.params.request.url);network.push({method:m.params.request.method,path:u.pathname});}});
  send=(method,params={})=>new Promise((resolve,reject)=>{const id=++nextId; const timer=setTimeout(()=>{pending.delete(id);reject(new Error('CDP command timeout'));},15000);pending.set(id,{resolve:r=>{clearTimeout(timer);resolve(r);},reject:e=>{clearTimeout(timer);reject(e);}});ws.send(JSON.stringify({id,method,params}));});
  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
  await send('Page.navigate',{url:'http://localhost:5173/login'});
  await until('document.querySelectorAll("form input").length===2');
  await evaluate(`(()=>{const inputs=document.querySelectorAll('form input');const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;set.call(inputs[0],${JSON.stringify(demo.email)});inputs[0].dispatchEvent(new Event('input',{bubbles:true}));set.call(inputs[1],${JSON.stringify(demo.password)});inputs[1].dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  await evaluate('document.querySelector("form").requestSubmit();true');
  await until('location.pathname==="/" && !!localStorage.getItem("token") && [...document.querySelectorAll("button")].some(b=>b.textContent.trim()==="Thêm vào giỏ")');
  record('UI Login and Product Listing',{pass:true});
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Thêm vào giỏ").click();true');
  await evaluate('[...document.querySelectorAll("a")].find(a=>a.textContent.trim()==="Giỏ hàng").click();true');
  await until('location.pathname==="/cart" && document.querySelectorAll("tbody tr").length===1');
  const cartRow=await evaluate('[...document.querySelector("tbody tr").querySelectorAll("td")].map(t=>t.textContent.trim())');
  record('UI Add to Cart',{pass:true,rows:1,row:cartRow});
  const updateUi=await evaluate('({numberInputs:document.querySelectorAll("input[type=number]").length,buttons:[...document.querySelectorAll("tbody button")].map(b=>b.textContent.trim())})');
  record('UI Update Cart',{result:'BLOCKED_MISSING_FUNCTION',...updateUi});
  await evaluate('[...document.querySelectorAll("tbody button")].find(b=>b.textContent.trim()==="Xóa").click();true');
  await until('document.body.innerText.includes("Giỏ hàng của bạn đang trống")');
  record('UI Remove from Cart',{pass:true,rows:await evaluate('document.querySelectorAll("tbody tr").length')});
  record('UI View Cart',{pass:true,emptyMessage:await evaluate('document.querySelector("main").innerText.trim()')});
  evidence.browserApiRequests=network;
  evidence.frontendCartApiRequests=network.filter(r=>r.path==='/api/cart');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Thoát").click();true');
  await until('localStorage.getItem("token")===null');
  record('UI Cleanup',{emptyCartConfirmed:true,loggedOut:true});
  const finalCart=await api('GET','/api/cart');
  record('Final backend cart verification',{status:finalCart.status,unchanged:JSON.stringify(finalCart.data)===baselineCart,count:finalCart.data.length});
  evidence.accountAfter=await get('SELECT login_attempts,locked_until FROM users WHERE id='+Number(demo.id));
  evidence.accountStateUnchanged=JSON.stringify(evidence.accountBefore)===JSON.stringify(evidence.accountAfter);
  evidence.limitations.push('Update Cart missing in web frontend and backend; full six-step workflow cannot pass.','POST /api/cart deliberately not executed: no supported cleanup endpoint and restarting/resetting SUT prohibited.','Missing-route probes use candidate paths, not documented supported endpoints.','No failed login or lockout timing tests; lockout conclusions are source-based.');
}
(async()=>{
  try { await run(); evidence.executionCompleted=true; }
  catch(e){evidence.error=e.message; evidence.executionCompleted=false;console.error(e.message);process.exitCode=1;}
  finally {
    if(ws){try{await send('Page.navigate',{url:'about:blank'});}catch{} ws.close();}
    if(chrome){chrome.kill(); await sleep(1200);}
    if(profile){const tempRoot=path.resolve(os.tmpdir());const resolved=path.resolve(profile);if(path.dirname(resolved)!==tempRoot || !path.basename(resolved).startsWith('hw05-cart-smoke-'))throw new Error('Unsafe temporary profile path');try{fs.rmSync(resolved,{recursive:true,force:true,maxRetries:3,retryDelay:200});evidence.temporaryProfileRemoved=true;}catch{evidence.temporaryProfileRemoved=false;}}
    evidence.sourceAfter=fingerprint();evidence.sourceUnchanged=JSON.stringify(evidence.sourceBefore)===JSON.stringify(evidence.sourceAfter);
    evidence.finishedAt=new Date().toISOString();
    fs.writeFileSync(output,JSON.stringify(evidence,null,2)+'\n','utf8');
    db.close();
    console.log('Sanitized evidence saved: audit/02-api-analysis-smoke.json');
  }
})();
