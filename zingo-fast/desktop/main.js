'use strict';
/* ZINGO Fast desktop shell.
   The whole simulator is app/index.html; this process only adds what a browser cannot do well:
   native open / save dialogs, a projects folder in Documents, recent projects, .zingo file association
   and an "unsaved changes" check when the window is closed. */
const {app,BrowserWindow,ipcMain,dialog,shell,Menu,session}=require('electron');
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
