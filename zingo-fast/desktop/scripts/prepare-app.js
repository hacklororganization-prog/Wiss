/* copies the release build of the web app (../app/index.html, produced by build.py) into the desktop package */
const fs=require('fs'),path=require('path'),{execFileSync}=require('child_process');
const appDir=path.join(__dirname,'..','..','app'),out=path.join(__dirname,'..','app');
const py=process.platform==='win32'?'python':'python3';
execFileSync(py,['build.py','index.dev.html','index.html'],{cwd:appDir,stdio:'inherit'});
fs.mkdirSync(out,{recursive:true});
fs.copyFileSync(path.join(appDir,'index.html'),path.join(out,'index.html'));
console.log('app copied ->',path.join(out,'index.html'));
