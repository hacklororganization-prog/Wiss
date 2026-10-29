'use strict';
/* the only bridge between the page and the desktop: a small, fixed API (no Node.js in the page) */
const {contextBridge,ipcRenderer}=require('electron');
const call=(ch,...a)=>ipcRenderer.invoke(ch,...a);
contextBridge.exposeInMainWorld('ZingoDesktop',Object.freeze({
  isDesktop:true,
  ready:()=>call('zf:ready'),
  setTitle:(title,dirty,p)=>call('zf:set-title',String(title||''),!!dirty,String(p||'')),
  openProject:()=>call('zf:open-project'),
  readProject:p=>call('zf:read-project',String(p)),
  pickSavePath:name=>call('zf:pick-save-path',String(name||'')),
  writeProject:(p,text)=>call('zf:write-project',String(p),String(text)),
  createProject:name=>call('zf:create-project',String(name||'')),
  renameProject:(p,name)=>call('zf:rename-project',String(p),String(name||'')),
  recent:()=>call('zf:recent'),
  exportFile:(name,b64)=>call('zf:export-file',String(name||''),String(b64||'')),
  showInFolder:p=>call('zf:show-in-folder',String(p)),
  openExternal:url=>call('zf:open-external',String(url)),
  closeNow:()=>call('zf:close-now'),
  onOpenFile:fn=>{ipcRenderer.on('zf:open-file',(_e,p)=>{try{fn(p)}catch(e){}})},
  onCloseRequest:fn=>{ipcRenderer.on('zf:close-request',()=>{try{fn()}catch(e){}})},
  /* ZINGO AI */
  dataRead:name=>call('zf:data-read',String(name)),
  dataWrite:(name,text)=>call('zf:data-write',String(name),String(text)),
  secretGet:name=>call('zf:secret-get',String(name)),
  secretSet:(name,value)=>call('zf:secret-set',String(name),String(value||'')),
  net:req=>call('zf:net',{method:req&&req.method,url:req&&req.url,body:req&&req.body,timeout:req&&req.timeout}),
  aiStream:(req,onEvent)=>{const id=req.id,h=(_e,ev)=>{if(ev&&ev.id===id){try{onEvent(ev)}catch(e){}}};ipcRenderer.on('zf:ai-ev',h);
    call('zf:ai-stream',{id,provider:req.provider,base:req.base,model:req.model,key:req.key,system:req.system,messages:req.messages,temperature:req.temperature}).catch(e=>onEvent({id,type:'error',message:String(e&&e.message||e)}));
    return()=>ipcRenderer.removeListener('zf:ai-ev',h)},
  aiAbort:id=>call('zf:ai-abort',id)
}));
