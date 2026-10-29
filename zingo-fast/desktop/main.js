'use strict';
/* ZINGO Fast desktop shell.
   The whole simulator is app/index.html; this process only adds what a browser cannot do well:
   native open / save dialogs, a projects folder in Documents, recent projects, .zingo file association
   and an "unsaved changes" check when the window is closed. */
const {app,BrowserWindow,ipcMain,dialog,shell,Menu,session,safeStorage}=require('electron');
const path=require('path');
const fs=require('fs');

const EXT='.zingo';
const MAX_FILE=50*1024*1024;
const projectsDir=()=>path.join(app.getPath('documents'),'ZINGO Fast');
const recentFile=()=>path.join(app.getPath('userData'),'recent.json');

let win=null,rendererReady=false,closing=false,pendingOpen=null;
/* the renderer may only touch files the user picked in a dialog, created here, or opened from Explorer / the recent list */
const allowed=new Set();
const allow=p=>{const r=path.resolve(p);allowed.add(r.toLowerCase());return r};
const isAllowed=p=>typeof p==='string'&&allowed.has(path.resolve(p).toLowerCase());

/* ---------- helpers ---------- */
const nameOf=p=>path.basename(p).replace(/\.(zingo|json)$/i,'');
const cleanName=s=>String(s||'').replace(/[\\/:*?"<>|\u0000-\u001f]+/g,' ').replace(/\s+/g,' ').trim().slice(0,80).replace(/[. ]+$/,'')||'ZINGO';
function uniquePath(dir,base,ext){let p=path.join(dir,base+ext),i=2;while(fs.existsSync(p))p=path.join(dir,base+' ('+(i++)+')'+ext);return p}
/* atomic write: a crash or full disk never leaves a half-written project behind */
function writeAtomic(p,data){const tmp=p+'.tmp-'+process.pid;fs.writeFileSync(tmp,data);fs.renameSync(tmp,p)}
function readRecent(){try{const a=JSON.parse(fs.readFileSync(recentFile(),'utf8'));return Array.isArray(a)?a.filter(x=>x&&typeof x.path==='string'):[]}catch(e){return[]}}
function addRecent(p){try{const r=path.resolve(p);const l=readRecent().filter(x=>path.resolve(x.path).toLowerCase()!==r.toLowerCase());l.unshift({path:r,name:nameOf(r)});
  fs.mkdirSync(path.dirname(recentFile()),{recursive:true});writeAtomic(recentFile(),JSON.stringify(l.slice(0,12),null,1));app.addRecentDocument(r)}catch(e){}}
function dropRecent(p){try{const r=path.resolve(p).toLowerCase();writeAtomic(recentFile(),JSON.stringify(readRecent().filter(x=>path.resolve(x.path).toLowerCase()!==r),null,1))}catch(e){}}
function readProjectFile(p){const r=path.resolve(p);if(!/\.(zingo|json)$/i.test(r))return null;const st=fs.statSync(r);if(!st.isFile()||st.size>MAX_FILE)return null;
  return{path:r,name:nameOf(r),text:fs.readFileSync(r,'utf8')}}
const fileArg=argv=>{for(const a of argv.slice(1)){if(typeof a==='string'&&/\.zingo$/i.test(a)&&fs.existsSync(a))return path.resolve(a)}return null};
function sendOpen(p){if(!p)return;allow(p);if(win&&rendererReady)win.webContents.send('zf:open-file',p);else pendingOpen=p}
const ensureDir=()=>{const d=projectsDir();fs.mkdirSync(d,{recursive:true});return d};

/* ---------- single instance: a second launch (double-clicked .zingo) goes to the open window ---------- */
if(!app.requestSingleInstanceLock()){app.quit()}
else{
app.on('second-instance',(_e,argv)=>{if(win){if(win.isMinimized())win.restore();win.focus()}sendOpen(fileArg(argv))});

function createWindow(){
  win=new BrowserWindow({width:1440,height:900,minWidth:1024,minHeight:640,show:false,backgroundColor:'#0b1219',title:'ZINGO Fast',
    icon:path.join(__dirname,'build','icon.png'),autoHideMenuBar:true,
    webPreferences:{preload:path.join(__dirname,'preload.js'),contextIsolation:true,sandbox:true,nodeIntegration:false,webSecurity:true,spellcheck:false,devTools:!app.isPackaged}});
  win.once('ready-to-show',()=>{win.maximize();win.show()});
  win.loadFile(path.join(__dirname,'app','index.html'));
  /* nothing navigates away from the app; links open in the default browser / mail client */
  win.webContents.on('will-navigate',e=>e.preventDefault());
  win.webContents.setWindowOpenHandler(({url})=>{if(/^(https?:|mailto:)/i.test(url))shell.openExternal(url);return{action:'deny'}});
  win.webContents.on('render-process-gone',()=>{rendererReady=false});
  win.on('close',e=>{
    if(closing||!rendererReady||win.webContents.isCrashed())return;
    e.preventDefault();win.webContents.send('zf:close-request')});
  win.on('closed',()=>{win=null;rendererReady=false});
}

/* ---------- IPC: every call checks that it comes from our own page ---------- */
const own=e=>{try{return e.senderFrame&&e.senderFrame.url.startsWith('file://')}catch(_){return false}};
const H=(ch,fn)=>ipcMain.handle(ch,async(e,...a)=>{if(!own(e))throw new Error('denied');return fn(e,...a)});

H('zf:ready',()=>{rendererReady=true;const p=pendingOpen;pendingOpen=null;if(p)setTimeout(()=>win&&win.webContents.send('zf:open-file',p),50);return true});
H('zf:close-now',()=>{closing=true;if(win)win.close();return true});
H('zf:set-title',(_e,title,dirty,p)=>{if(!win)return;win.setTitle((dirty?'• ':'')+String(title||'').slice(0,120)+' — ZINGO Fast');
  if(process.platform==='darwin'){win.setDocumentEdited(!!dirty);if(isAllowed(p))win.setRepresentedFilename(p)}});
H('zf:open-project',async()=>{
  const r=await dialog.showOpenDialog(win,{title:'ZINGO Fast',defaultPath:ensureDir(),properties:['openFile'],filters:[{name:'ZINGO Fast',extensions:['zingo','json']}]});
  if(r.canceled||!r.filePaths[0])return null;const f=readProjectFile(r.filePaths[0]);if(!f)return null;allow(f.path);addRecent(f.path);return f});
H('zf:read-project',(_e,p)=>{if(!isAllowed(p)&&!readRecent().some(x=>path.resolve(x.path).toLowerCase()===path.resolve(String(p)).toLowerCase()))return null;
  try{const f=readProjectFile(p);if(!f)return null;allow(f.path);addRecent(f.path);return f}catch(e){dropRecent(p);return null}});
H('zf:pick-save-path',async(_e,suggested)=>{
  const r=await dialog.showSaveDialog(win,{title:'ZINGO Fast',defaultPath:path.join(ensureDir(),cleanName(String(suggested||'').replace(/\.zingo$/i,''))+EXT),filters:[{name:'ZINGO Fast',extensions:['zingo']}]});
  if(r.canceled||!r.filePath)return null;let p=r.filePath;if(!/\.zingo$/i.test(p))p+=EXT;return{path:allow(p),name:nameOf(p)}});
H('zf:write-project',(_e,p,text)=>{if(!isAllowed(p)||!/\.(zingo|json)$/i.test(p)||typeof text!=='string'||text.length>MAX_FILE)return{ok:false};
  try{writeAtomic(path.resolve(p),text);addRecent(p);return{ok:true}}catch(e){return{ok:false,error:String(e.code||e.message)}}});
H('zf:create-project',(_e,name)=>{try{const p=uniquePath(ensureDir(),cleanName(name),EXT);return{path:allow(p),name:nameOf(p)}}catch(e){return null}});
H('zf:rename-project',(_e,p,name)=>{if(!isAllowed(p))return null;try{const src=path.resolve(p),dst=uniquePath(path.dirname(src),cleanName(name),EXT);
  if(fs.existsSync(src)){fs.renameSync(src,dst);dropRecent(src)}allow(dst);addRecent(dst);return{path:dst,name:nameOf(dst)}}catch(e){return null}});
H('zf:recent',()=>readRecent().filter(x=>{try{return fs.statSync(x.path).isFile()}catch(e){return false}}).slice(0,8).map(x=>{allow(x.path);return{path:x.path,name:x.name}}));
H('zf:export-file',async(_e,name,b64)=>{
  const base=cleanName(String(name||'export').replace(/\.[a-z0-9]+$/i,'')),ext=(/\.([a-z0-9]{1,5})$/i.exec(String(name))||[0,'bin'])[1].toLowerCase();
  const kinds={png:'PNG',svg:'SVG',csv:'CSV',json:'JSON'};
  const r=await dialog.showSaveDialog(win,{title:'ZINGO Fast',defaultPath:path.join(ensureDir(),base+'.'+ext),filters:[{name:kinds[ext]||ext.toUpperCase(),extensions:[ext]}]});
  if(r.canceled||!r.filePath)return null;try{const buf=Buffer.from(String(b64||''),'base64');if(buf.length>MAX_FILE)return null;writeAtomic(r.filePath,buf);return{path:r.filePath,name:path.basename(r.filePath)}}catch(e){return null}});
H('zf:show-in-folder',(_e,p)=>{if(isAllowed(p))shell.showItemInFolder(path.resolve(p));return true});
/* ---------- ZINGO AI: data files (brain, community brain, outbox) ---------- */
const dataDir=()=>path.join(app.getPath('userData'),'ai');
const DATA_NAME=/^[a-z]{2,16}$/;
H('zf:data-read',(_e,name)=>{if(!DATA_NAME.test(String(name)))return null;try{return fs.readFileSync(path.join(dataDir(),name+'.json'),'utf8')}catch(e){return null}});
H('zf:data-write',(_e,name,text)=>{if(!DATA_NAME.test(String(name))||typeof text!=='string'||text.length>40*1024*1024)return{ok:false};
  try{fs.mkdirSync(dataDir(),{recursive:true});writeAtomic(path.join(dataDir(),name+'.json'),text);return{ok:true}}catch(e){return{ok:false}}});

/* ---------- API keys: encrypted with the operating system's key store (DPAPI on Windows) ---------- */
const secretFile=()=>path.join(app.getPath('userData'),'secrets.json');
const readSecrets=()=>{try{return JSON.parse(fs.readFileSync(secretFile(),'utf8'))||{}}catch(e){return{}}};
H('zf:secret-get',(_e,name)=>{const v=readSecrets()[String(name)];if(!v)return'';try{return v.e?safeStorage.decryptString(Buffer.from(v.d,'base64')):''}catch(e){return''}});
H('zf:secret-set',(_e,name,value)=>{name=String(name);if(!/^[a-z_]{2,40}$/.test(name))return false;const all=readSecrets();value=String(value||'');
  if(!value)delete all[name];else{if(!safeStorage.isEncryptionAvailable())return false;all[name]={e:1,d:safeStorage.encryptString(value).toString('base64')}}
  try{writeAtomic(secretFile(),JSON.stringify(all));return true}catch(e){return false}});

/* ---------- network for the brain sync (Firestore lookup + the owner's server); https, or http on this computer only ---------- */
const netOk=u=>/^https:\/\/[^\s]+$/i.test(u)||/^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?(\/[^\s]*)?$/i.test(u);
H('zf:net',async(_e,req)=>{req=req||{};const url=String(req.url||''),method=req.method==='POST'?'POST':'GET';if(!netOk(url))return{ok:false,error:'blocked url'};
  const ctl=new AbortController(),to=setTimeout(()=>ctl.abort(),Math.min(60000,Math.max(2000,+req.timeout||20000)));
  try{const r=await fetch(url,{method,headers:method==='POST'?{'Content-Type':'application/json'}:{},body:method==='POST'?String(req.body||''):undefined,signal:ctl.signal,redirect:'follow'});
    const text=await r.text();if(text.length>40*1024*1024)return{ok:false,error:'too large'};return{ok:r.ok,status:r.status,text:r.ok?text:'',error:r.ok?'':'HTTP '+r.status}}
  catch(e){return{ok:false,error:e.name==='AbortError'?'timeout':String(e.message||e)}}finally{clearTimeout(to)}});

/* ---------- ZINGO AI: model calls run here (no browser CORS limits); events stream back to the page ---------- */
const aiJobs=new Map();
function aiSend(wc,ev){try{if(!wc.isDestroyed())wc.send('zf:ai-ev',ev)}catch(e){}}
/* the Messages API wants user/assistant turns alternating and starting with the user */
function aiTurns(msgs){const out=[];for(const m of msgs||[]){const role=m&&m.role==='assistant'?'assistant':'user',content=String(m&&m.content||'');if(!content)continue;
  if(out.length&&out[out.length-1].role===role)out[out.length-1].content+='\n\n'+content;else out.push({role,content})}
  while(out.length&&out[0].role!=='user')out.shift();return out}
async function aiClaude(job,req,wc){
  const Anthropic=require('@anthropic-ai/sdk');const client=new Anthropic({apiKey:req.key,maxRetries:2});
  const base={model:req.model||'claude-opus-5-5',max_tokens:16000,system:String(req.system||''),messages:aiTurns(req.messages),output_config:{effort:'medium'}};
  /* refusal fallback (server side) is requested by default; if this account or model rejects the parameter, retry without it */
  const run=async withFallback=>{const p=withFallback?Object.assign({betas:['server-side-fallback-2026-07-01'],fallbacks:'default'},base):base;
    const stream=client.beta.messages.stream(p,{signal:job.ctl.signal});
    for await(const ev of stream){if(ev.type==='content_block_delta'&&ev.delta&&ev.delta.type==='text_delta')aiSend(wc,{id:req.id,type:'delta',text:ev.delta.text})}
    return stream.finalMessage()};
  let msg;try{msg=await run(true)}catch(e){if(e instanceof Anthropic.BadRequestError&&/fallback/i.test(String(e.message)))msg=await run(false);else throw e}
  if(msg.stop_reason==='refusal')throw new Error('The model declined this request.');
  return msg.stop_reason||''}
async function aiOpenAI(job,req,wc){
  const url=String(req.base||'').replace(/\/+$/,'')+'/chat/completions';if(!netOk(url))throw new Error('Invalid API address');
  const headers={'Content-Type':'application/json'};if(req.key)headers.Authorization='Bearer '+req.key;
  const r=await fetch(url,{method:'POST',headers,signal:job.ctl.signal,body:JSON.stringify({model:req.model,messages:[{role:'system',content:String(req.system||'')}].concat(req.messages||[]),stream:true,temperature:typeof req.temperature==='number'?req.temperature:.3})});
  if(!r.ok){const tx=await r.text().catch(()=>'');const err=new Error('HTTP '+r.status+(tx?': '+tx.slice(0,300):''));err.status=r.status;throw err}
  let finish='',buf='';const dec=new TextDecoder();
  for await(const chunk of r.body){buf+=dec.decode(chunk,{stream:true});let i;
    while((i=buf.indexOf('\n'))>=0){const line=buf.slice(0,i).trim();buf=buf.slice(i+1);if(!line.startsWith('data:'))continue;const d=line.slice(5).trim();if(d==='[DONE]')continue;
      try{const j=JSON.parse(d),ch=j.choices&&j.choices[0];if(ch){const c=ch.delta&&ch.delta.content;if(c)aiSend(wc,{id:req.id,type:'delta',text:c});if(ch.finish_reason)finish=ch.finish_reason}}catch(e){}}}
  return finish}
H('zf:ai-stream',(e,req)=>{req=req||{};const wc=e.sender,job={ctl:new AbortController()};aiJobs.set(req.id,job);
  (async()=>{try{const finish=req.provider==='claude'?await aiClaude(job,req,wc):await aiOpenAI(job,req,wc);aiSend(wc,{id:req.id,type:'done',finish})}
    catch(err){if(job.ctl.signal.aborted)return;const st=err&&err.status;let m=String(err&&err.message||err);
      if(st===401||st===403)m='The API key was rejected.';else if(st===429)m='Rate limit reached. Wait a moment and retry.';else if(st===404)m='Model or address not found.';else if(st>=500)m='The AI service is having problems. Retry in a moment.';
      aiSend(wc,{id:req.id,type:'error',message:m.slice(0,400)})}
    finally{aiJobs.delete(req.id)}})();
  return true});
H('zf:ai-abort',(_e,id)=>{const j=aiJobs.get(id);if(j)j.ctl.abort();aiJobs.delete(id);return true});

H('zf:open-external',(_e,url)=>{if(typeof url==='string'&&/^(https:|mailto:)/i.test(url))shell.openExternal(url);return true});

app.whenReady().then(()=>{
  Menu.setApplicationMenu(null);
  /* the simulator needs no camera, microphone, location or notifications */
  session.defaultSession.setPermissionRequestHandler((_wc,_perm,cb)=>cb(false));
  pendingOpen=fileArg(process.argv);if(pendingOpen)allow(pendingOpen);
  createWindow();
  app.on('activate',()=>{if(!BrowserWindow.getAllWindows().length)createWindow()});
});
app.on('open-file',(e,p)=>{e.preventDefault();sendOpen(p)});   /* macOS */
app.on('web-contents-created',(_e,wc)=>{wc.on('will-attach-webview',ev=>ev.preventDefault())});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});
}
