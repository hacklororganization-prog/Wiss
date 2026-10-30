#!/usr/bin/env node
'use strict';
/* Voltra server (app.js): community brain for Voltra AI, accounts, extensions store, owner dashboard, e-mail
   - Collects what the ZINGO Fast assistants learn (answers users rated up, designs they kept, fixes that worked),
     curates it (validation, personal-data scrubbing, de-duplication, votes from different users, owner review)
     and serves the shared brain back to every app.
   - Opens a free Cloudflare quick tunnel (cloudflared tunnel --url http://127.0.0.1:PORT) and publishes the
     tunnel address to Firebase Firestore (document zingo/server), where the apps look it up.
   - No npm packages: runs with plain Node.js 18+ (Termux: pkg install nodejs cloudflared).
   Usage:  node app.js            (first run writes config.json and prints your admin token)
           node app.js --no-tunnel   (behind your own domain / reverse proxy: set publicUrl in config.json) */
const http=require('http'),fs=require('fs'),path=require('path'),crypto=require('crypto'),zlib=require('zlib'),{spawn}=require('child_process');

const ROOT=__dirname,DATA=path.join(ROOT,'data'),CFG_FILE=path.join(ROOT,'config.json');
const VERSION='2.0.0';
const log=(...a)=>console.log(new Date().toISOString().slice(11,19),...a);

/* ---------------- config ---------------- */
const DEFAULT_CFG={port:5000,host:'127.0.0.1',tunnel:true,publicUrl:'',
  firebase:{apiKey:'AIzaSyDujpLY1NOWUPrbAYZHG-VPCrVydDQfF1A',projectId:'z-ai-404d2',doc:'zingo/server',email:'',password:''},
  minVotes:2,autoApprove:false,adminToken:'',maxFacts:20000,maxDesigns:3000,maxPending:50000,
  owners:[],trialMs:2592e5,openDashboard:true,google:{webClientId:'',desktopClientId:'',desktopSecret:''},smtp:{user:'',pass:'',from:'Voltra'},verify:{minDownloads:20,share:.05,minRating:4,minRatings:3}};
function loadCfg(){let c={};try{c=JSON.parse(fs.readFileSync(CFG_FILE,'utf8'))}catch(e){}
  const cfg=Object.assign({},DEFAULT_CFG,c,{firebase:Object.assign({},DEFAULT_CFG.firebase,c.firebase||{})});
  let wrote=false;if(!cfg.adminToken){cfg.adminToken=crypto.randomBytes(18).toString('base64url');wrote=true}
  if(wrote||!fs.existsSync(CFG_FILE)){fs.writeFileSync(CFG_FILE,JSON.stringify(cfg,null,2));log('config.json written. Admin token:',cfg.adminToken)}
  if(process.env.PORT)cfg.port=+process.env.PORT;if(process.argv.includes('--no-tunnel'))cfg.tunnel=false;
  return cfg}
const CFG=loadCfg();
const saveCfg=()=>fs.writeFileSync(CFG_FILE,JSON.stringify(CFG,null,2));

/* ---------------- storage (atomic writes, daily backups) ---------------- */
fs.mkdirSync(DATA,{recursive:true});fs.mkdirSync(path.join(DATA,'backup'),{recursive:true});
const F={brain:path.join(DATA,'brain.json'),pending:path.join(DATA,'pending.json')};
const readJson=(f,d)=>{try{return JSON.parse(fs.readFileSync(f,'utf8'))}catch(e){return d}};
const writeAtomic=(f,s)=>{const t=f+'.tmp';fs.writeFileSync(t,s);fs.renameSync(t,f)};
let brain=readJson(F.brain,null)||{format:'zingo-brain',rev:1,updatedAt:Date.now(),facts:[],designs:[]};
let pending=readJson(F.pending,null)||{items:[]};
let nid=1+Math.max(0,...brain.facts.map(x=>x.id||0),...brain.designs.map(x=>x.id||0),...pending.items.map(x=>x.id||0));
let saveT=null,brainCache=null;
const V2=require('./lib_v2.js')({CFG,DATA,log,readJson,writeAtomic,saveCfg});
function persist(changed){if(changed){brain.rev++;brain.updatedAt=Date.now();brainCache=null}clearTimeout(saveT);saveT=setTimeout(()=>{
  try{writeAtomic(F.brain,JSON.stringify(brain));writeAtomic(F.pending,JSON.stringify(pending))}catch(e){log('save failed',e.message)}},1500)}
function backup(){const d=new Date().toISOString().slice(0,10),f=path.join(DATA,'backup','brain-'+d+'.json');
  try{if(!fs.existsSync(f))fs.writeFileSync(f,JSON.stringify(brain));const all=fs.readdirSync(path.join(DATA,'backup')).filter(x=>x.startsWith('brain-')).sort();all.slice(0,-14).forEach(x=>fs.unlinkSync(path.join(DATA,'backup',x)))}catch(e){}}
setInterval(backup,6*3600e3);backup();

/* ---------------- text tools (same normalisation as the app) ---------------- */
const STOP=new Set(['ما','ماذا','من','هو','هي','في','علي','عن','الي','هل','كيف','لماذا','هذا','هذه','و','او','ان','مع','لا','the','a','an','of','is','to','in','and','or','for','with','on','how','what','why','can','you','i','my','it','please']);
const norm=s=>String(s||'').toLowerCase().replace(/[ً-ْـ]/g,'').replace(/[أإآ]/g,'ا').replace(/ى/g,'ي').replace(/ة/g,'ه');
const toks=s=>norm(s).split(/[^\p{L}\p{N}#+]+/u).filter(w=>w&&w.length>1&&!STOP.has(w));
const jacc=(a,b)=>{const A=new Set(a),B=new Set(b);if(!A.size||!B.size)return 0;let k=0;A.forEach(x=>{if(B.has(x))k++});return k/(A.size+B.size-k)};
const clean=(s,n)=>String(s==null?'':s).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g,'').slice(0,n);
const scrub=s=>s.replace(/[\w.+-]+@[\w-]+\.[\w.]+/g,'[email]').replace(/(?:\+|00)?\d[\d\s-]{8,}\d/g,'[number]').replace(/sk-[A-Za-z0-9_-]{12,}/g,'[key]').replace(/AIza[0-9A-Za-z_-]{20,}/g,'[key]');
const keyOf=(kind,q)=>crypto.createHash('sha256').update(kind+'|'+norm(q).replace(/\s+/g,' ').trim()).digest('base64url').slice(0,22);
const clientId=(c,ip)=>crypto.createHash('sha256').update('zingo|'+String(c||'')+'|'+(c?'':ip)).digest('base64url').slice(0,16);

/* ---------------- curation ---------------- */
/* questions that only make sense for one user's circuit (the app's quick buttons) are never shared */
const GENERIC=['check my circuit: find every mistake or risk, explain the root cause and fix it.','explain how this circuit works, step by step, with the current and voltage of each part.','your changes were applied but there are problems. fix them.',
  'افحص دائرتي: اكتشف كل خطا او خطر، واشرح السبب الجذري، واصلحه.','اشرح كيف تعمل هذه الدائره خطوه بخطوه مع التيار والجهد في كل قطعه.','طبقت تغييراتك لكن توجد مشاكل. اصلحها.'];
const noIds=s=>String(s).replace(/`?#\d+(?:\.[^\s`,)]+)?`?/g,'').replace(/\(\s*\)/g,'').replace(/[ \t]{2,}/g,' ');
function validate(it){if(!it||typeof it!=='object')return null;const kind=['chat','fix','design'].includes(it.kind)?it.kind:null;if(!kind)return null;
  const q=noIds(scrub(clean(it.q,600))).trim();if(q.length<4||GENERIC.includes(norm(q).replace(/\s+/g,' ')))return null;const o={kind,q,lang:/^(ar|en)$/.test(it.lang)?it.lang:'en'};
  if(kind==='design'){o.name=scrub(clean(it.name||q,80)).trim();o.a=scrub(clean(it.a,1500));o.dkind=it.dkind==='part'?'part':'circuit';
    let data=it.data;try{const js=JSON.stringify(data);if(!data||typeof data!=='object'||js.length>60000)return null;data=JSON.parse(js)}catch(e){return null}o.data=data}
  else{o.a=noIds(scrub(clean(it.a,6000))).trim();if(o.a.length<8)return null}
  if((o.q+o.a).split(/https?:\/\//).length>6)return null;   /* link spam */
  return o}
function findSimilar(list,o){const k=keyOf(o.kind,o.q);let hit=list.find(x=>x.k===k);if(hit)return hit;const qt=toks(o.q);
  for(const x of list){if(x.kind!==o.kind)continue;if(jacc(qt,x._t||(x._t=toks(x.q)))>=.8)return x}return null}
function publishItem(p){const pub={id:p.id,kind:p.kind,q:p.q,a:p.a,lang:p.lang,votes:p.voters.length,up:p.voters.length,t:p.t};
  if(p.kind==='design'){Object.assign(pub,{name:p.name,dkind:p.dkind,data:p.data});const old=brain.designs.findIndex(x=>x.id===p.id);if(old>=0)brain.designs[old]=pub;else brain.designs.push(pub)}
  else{const old=brain.facts.findIndex(x=>x.id===p.id);if(old>=0)brain.facts[old]=pub;else brain.facts.push(pub)}
  const cap=(arr,n)=>{if(arr.length>n){arr.sort((a,b)=>(b.votes-a.votes)||(b.t-a.t));arr.length=n}};cap(brain.facts,CFG.maxFacts);cap(brain.designs,CFG.maxDesigns)}
function contribute(items,cid){let added=0,voted=0,promoted=0;
  for(const raw of items.slice(0,100)){const o=validate(raw);if(!o)continue;
    let p=findSimilar(pending.items,o);
    if(!p){p=Object.assign({id:nid++,k:keyOf(o.kind,o.q),voters:[],down:[],t:Date.now(),approved:false},o);pending.items.push(p);added++}
    else if(o.a&&o.a.length>(p.a||'').length*1.15&&!p.approved){p.a=o.a;if(o.data)p.data=o.data}   /* keep the richer answer until it is approved */
    if(!p.voters.includes(cid)){p.voters.push(cid);voted++}
    p.t=Date.now();
    if(p.approved||CFG.autoApprove||p.voters.length>=CFG.minVotes){p.approved=true;publishItem(p);promoted++}}
  if(pending.items.length>CFG.maxPending){pending.items.sort((a,b)=>(b.voters.length-a.voters.length)||(b.t-a.t));pending.items.length=CFG.maxPending}
  if(added||voted)persist(promoted>0);return{added,voted,promoted}}

/* ---------------- abuse limits ---------------- */
const rate=new Map();
function limited(key,max,winMs){const now=Date.now();let r=rate.get(key);if(!r||now-r.t>winMs){r={t:now,n:0};rate.set(key,r)}r.n++;return r.n>max}
setInterval(()=>{const now=Date.now();rate.forEach((v,k)=>{if(now-v.t>3600e3)rate.delete(k)})},600e3);

/* ---------------- HTTP API ---------------- */
function send(req,res,code,obj,extra){const body=JSON.stringify(obj);const h=Object.assign({'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Access-Control-Allow-Origin':'*'},extra||{});
  if(body.length>2048&&/\bgzip\b/.test(req.headers['accept-encoding']||'')){h['Content-Encoding']='gzip';res.writeHead(code,h);res.end(zlib.gzipSync(body))}else{res.writeHead(code,h);res.end(body)}}
function readBody(req,max){return new Promise((ok,bad)=>{let n=0;const ch=[];req.on('data',d=>{n+=d.length;if(n>max){bad(new Error('too large'));req.destroy()}else ch.push(d)});req.on('end',()=>{try{ok(JSON.parse(Buffer.concat(ch).toString('utf8')||'{}'))}catch(e){bad(new Error('bad json'))}});req.on('error',bad)})}
const ipOf=req=>String(req.headers['cf-connecting-ip']||req.headers['x-forwarded-for']||req.socket.remoteAddress||'').split(',')[0].trim();
const isAdmin=req=>{const t=String(req.headers['x-admin-token']||'');return t.length>10&&crypto.timingSafeEqual(Buffer.from(crypto.createHash('sha256').update(t).digest()),Buffer.from(crypto.createHash('sha256').update(CFG.adminToken).digest()))};
function publicBrain(){if(!brainCache)brainCache={format:'zingo-brain',rev:brain.rev,updatedAt:brain.updatedAt,facts:brain.facts,designs:brain.designs};return brainCache}

const server=http.createServer(async(req,res)=>{
  const u=new URL(req.url,'http://x'),ip=ipOf(req);
  if(req.method==='OPTIONS'){res.writeHead(204,{'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET,POST,PUT,DELETE','Access-Control-Allow-Headers':'Content-Type,X-Admin-Token,Authorization','Access-Control-Max-Age':'86400'});res.end();return}
  if(limited('ip:'+ip,120,60e3))return send(req,res,429,{ok:false,error:'too many requests'});
  try{
    if(req.method==='GET'&&u.pathname==='/api/health')return send(req,res,200,Object.assign({ok:true,app:'zingo-brain',name:'voltra',version:VERSION,rev:brain.rev,facts:brain.facts.length,designs:brain.designs.length,pending:pending.items.filter(x=>!x.approved).length},V2.counts()));
    if(req.method==='GET'&&u.pathname==='/api/brain'){const r=+u.searchParams.get('rev')||0;if(r===brain.rev)return send(req,res,200,{rev:brain.rev,unchanged:true});return send(req,res,200,publicBrain())}
    if(req.method==='POST'&&u.pathname==='/api/contribute'){const b=await readBody(req,1024*1024);if(!Array.isArray(b.items))return send(req,res,400,{ok:false,error:'items required'});
      const cid=clientId(b.client,ip);if(limited('c:'+cid,30,3600e3)||limited('cip:'+ip,60,3600e3))return send(req,res,429,{ok:false,error:'slow down'});
      const r=contribute(b.items,cid);log('contribute',cid.slice(0,6),JSON.stringify(r));return send(req,res,200,Object.assign({ok:true,rev:brain.rev},r))}
    if(await V2.route(req,res,u,ip,{send,readBody,isAdmin,limited}))return;
    if(req.method==='GET'&&(u.pathname==='/admin'||u.pathname==='/admin/')){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer'});res.end(fs.readFileSync(path.join(ROOT,'admin.html')));return}
    /* owner tools (header X-Admin-Token) */
    if(u.pathname.startsWith('/api/admin/')){if(!isAdmin(req))return send(req,res,401,{ok:false,error:'admin token required'});
      if(req.method==='GET'&&u.pathname==='/api/admin/pending'){const lim=Math.min(500,+u.searchParams.get('limit')||100);
        return send(req,res,200,{items:pending.items.filter(x=>!x.approved).sort((a,b)=>b.voters.length-a.voters.length).slice(0,lim).map(x=>({id:x.id,kind:x.kind,q:x.q,a:x.a,name:x.name,votes:x.voters.length,t:x.t}))})}
      const b=req.method==='POST'?await readBody(req,64*1024):{};const id=+b.id;
      if(req.method==='POST'&&u.pathname==='/api/admin/approve'){const p=pending.items.find(x=>x.id===id);if(!p)return send(req,res,404,{ok:false});p.approved=true;publishItem(p);persist(true);return send(req,res,200,{ok:true,rev:brain.rev})}
      if(req.method==='POST'&&(u.pathname==='/api/admin/reject'||u.pathname==='/api/admin/remove')){
        pending.items=pending.items.filter(x=>x.id!==id);const n0=brain.facts.length+brain.designs.length;brain.facts=brain.facts.filter(x=>x.id!==id);brain.designs=brain.designs.filter(x=>x.id!==id);
        persist(n0!==brain.facts.length+brain.designs.length);return send(req,res,200,{ok:true,rev:brain.rev})}
      if(req.method==='POST'&&u.pathname==='/api/admin/config'){if(b.minVotes!=null)CFG.minVotes=Math.max(1,Math.min(50,+b.minVotes||2));if(b.autoApprove!=null)CFG.autoApprove=!!b.autoApprove;
        saveCfg();return send(req,res,200,{ok:true,minVotes:CFG.minVotes,autoApprove:CFG.autoApprove})}
      return send(req,res,404,{ok:false})}
    if(req.method==='GET'&&u.pathname==='/'){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});
      res.end('<!doctype html><meta charset="utf-8"><title>ZINGO AI brain</title><body style="font:15px system-ui;padding:24px;background:#0b1219;color:#e7eef6"><h2>ZINGO AI brain server</h2><p>brain v'+brain.rev+' · '+brain.facts.length+' facts · '+brain.designs.length+' designs · '+pending.items.filter(x=>!x.approved).length+' waiting for votes</p></body>');return}
    send(req,res,404,{ok:false,error:'not found'})}
  catch(e){send(req,res,e.message==='too large'?413:400,{ok:false,error:e.message})}});
server.headersTimeout=15000;server.requestTimeout=30000;
server.on('error',e=>{if(e.code==='EADDRINUSE'){log('Port '+CFG.port+' is already in use: the Voltra server is probably already running.');log('Stop the old one with:  pkill -f "node app.js"   then start again.');process.exit(3)}
  log('server error:',e.message);process.exit(1)});

/* ---------------- Firebase: owner sign-in + Firestore document zingo/server ---------------- */
const FB=CFG.firebase;let tok={id:'',refresh:'',exp:0};
async function fbToken(){if(!FB.email||!FB.password)return'';if(tok.id&&Date.now()<tok.exp-120e3)return tok.id;
  if(tok.refresh){const r=await fetch('https://securetoken.googleapis.com/v1/token?key='+FB.apiKey,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'grant_type=refresh_token&refresh_token='+encodeURIComponent(tok.refresh)});
    if(r.ok){const j=await r.json();tok={id:j.id_token,refresh:j.refresh_token,exp:Date.now()+(+j.expires_in||3600)*1000};return tok.id}}
  const r=await fetch('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key='+FB.apiKey,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:FB.email,password:FB.password,returnSecureToken:true})});
  const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error('Firebase sign-in failed: '+((j.error&&j.error.message)||r.status));tok={id:j.idToken,refresh:j.refreshToken,exp:Date.now()+(+j.expiresIn||3600)*1000};return tok.id}
async function publish(url,online){
  const base=process.env.FIRESTORE_BASE||'https://firestore.googleapis.com/v1';
  const target=base+'/projects/'+FB.projectId+'/databases/(default)/documents/'+FB.doc+'?key='+FB.apiKey;
  const G=CFG.google||{};
  const body={fields:{url:{stringValue:url||''},online:{booleanValue:!!online},rev:{integerValue:String(brain.rev)},version:{stringValue:VERSION},gWeb:{stringValue:G.webClientId||''},gDesk:{stringValue:G.desktopClientId||''},gDeskS:{stringValue:G.desktopSecret||''},updatedAt:{timestampValue:new Date().toISOString()}}};
  for(let i=0;i<4;i++){try{const id=await fbToken();const h={'Content-Type':'application/json'};if(id)h.Authorization='Bearer '+id;
      const r=await fetch(target,{method:'PATCH',headers:h,body:JSON.stringify(body)});if(r.ok){log('published to Firebase:',online?url:'(offline)');return true}
      const tx=await r.text();log('Firebase write refused ('+r.status+'):',tx.slice(0,200));if(r.status===403&&!FB.email)log('-> set firebase.email / firebase.password in config.json (see README).');if(r.status<500)return false}
    catch(e){log('Firebase error:',e.message)}await new Promise(r=>setTimeout(r,3000*(i+1)))}
  return false}

/* ---------------- Cloudflare quick tunnel ---------------- */
let tunnelUrl='',cf=null,backoff=5000,stopping=false;
async function reachable(url){for(let i=0;i<20;i++){try{const r=await fetch(url+'/api/health',{signal:AbortSignal.timeout(8000)});if(r.ok)return true}catch(e){}await new Promise(r=>setTimeout(r,3000))}return false}
function startTunnel(){if(stopping)return;log('starting cloudflared…');
  try{cf=spawn(process.env.CLOUDFLARED||'cloudflared',['tunnel','--url','http://127.0.0.1:'+CFG.port,'--no-autoupdate'],{stdio:['ignore','pipe','pipe']})}
  catch(e){log('cannot start cloudflared:',e.message);return}
  let seen=false;const onData=d=>{const m=/https:\/\/[a-z0-9-]+\.trycloudflare\.com/i.exec(String(d));if(m&&!seen){seen=true;tunnelUrl=m[0];backoff=5000;log('tunnel:',tunnelUrl);
      reachable(tunnelUrl).then(ok=>{if(!ok)log('tunnel not reachable yet; publishing anyway');publish(tunnelUrl,true)})}};
  cf.stdout.on('data',onData);cf.stderr.on('data',onData);
  cf.on('error',e=>{log('cloudflared error:',e.code==='ENOENT'?'cloudflared is not installed (Termux: pkg install cloudflared)':e.message)});
  cf.on('exit',code=>{cf=null;if(stopping)return;log('cloudflared exited ('+code+'), restarting in',backoff/1000,'s');tunnelUrl='';publish('',false);setTimeout(startTunnel,backoff);backoff=Math.min(backoff*2,60000)})}

/* ---------------- start / stop ---------------- */
server.listen(CFG.port,CFG.host,()=>{log('Voltra server on http://'+CFG.host+':'+CFG.port,'· brain v'+brain.rev,'·',brain.facts.length,'facts');
  openDashboard();
  if(CFG.tunnel)startTunnel();else if(CFG.publicUrl)publish(CFG.publicUrl.replace(/\/+$/,''),true);
  setInterval(()=>{const u=CFG.tunnel?tunnelUrl:CFG.publicUrl;if(u)publish(u.replace(/\/+$/,''),true)},10*60e3)});   /* heartbeat: apps can see the server is alive */
/* the owner dashboard opens in the browser (Chrome) on start: Termux, Linux, macOS or Windows */
function openDashboard(){const url='http://127.0.0.1:'+CFG.port+'/admin#'+CFG.adminToken;log('Dashboard: http://127.0.0.1:'+CFG.port+'/admin');
  if(!CFG.openDashboard||process.argv.includes('--no-browser'))return;
  const tries=process.env.PREFIX&&/com\.termux/.test(process.env.PREFIX)?[['am',['start','-a','android.intent.action.VIEW','-d',url,'-p','com.android.chrome']],['termux-open-url',[url]]]
    :process.platform==='win32'?[['cmd',['/c','start','chrome',url]],['cmd',['/c','start','',url]]]:process.platform==='darwin'?[['open',['-a','Google Chrome',url]],['open',[url]]]:[['google-chrome',[url]],['xdg-open',[url]]];
  const run=i=>{if(i>=tries.length)return;try{const c=spawn(tries[i][0],tries[i][1],{stdio:'ignore',detached:true});c.on('error',()=>run(i+1));c.on('exit',code=>{if(code)run(i+1)});c.unref()}catch(e){run(i+1)}};run(0)}
async function shutdown(){if(stopping)return;stopping=true;log('stopping…');V2.flush();clearTimeout(saveT);try{writeAtomic(F.brain,JSON.stringify(brain));writeAtomic(F.pending,JSON.stringify(pending))}catch(e){}
  try{await Promise.race([publish('',false),new Promise(r=>setTimeout(r,4000))])}catch(e){}if(cf)cf.kill();server.close();process.exit(0)}
process.on('SIGINT',shutdown);process.on('SIGTERM',shutdown);
