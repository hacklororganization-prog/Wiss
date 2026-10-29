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
  onCloseRequest:fn=>{ipcRenderer.on('zf:close-request',()=>{try{fn()}catch(e){}})}
}));
