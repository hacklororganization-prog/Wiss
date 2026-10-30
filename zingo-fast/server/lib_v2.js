'use strict';
/* Voltra server, part 2: accounts, extensions store, e-mail (Gmail SMTP), owner dashboard data.
   Loaded by app.js; Node built-ins only. */
const fs=require('fs'),path=require('path'),crypto=require('crypto'),tls=require('tls');

module.exports=function v2(ctx){
const {CFG,DATA,log,readJson,writeAtomic}=ctx;
const FB=CFG.firebase;
const now=()=>Date.now();
const clean=(s,n)=>String(s==null?'':s).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g,'').slice(0,n);
const EXT_DIR=path.join(DATA,'ext');fs.mkdirSync(EXT_DIR,{recursive:true});
const F={users:path.join(DATA,'users.json'),ext:path.join(DATA,'extensions.json'),mail:path.join(DATA,'mail.json')};
let users=readJson(F.users,null)||{users:{}};
let ext=readJson(F.ext,null)||{items:[]};
let mail=readJson(F.mail,null)||{log:[]};
let saveT=null;
function save(){clearTimeout(saveT);saveT=setTimeout(flush,800)}
function flush(){clearTimeout(saveT);try{writeAtomic(F.users,JSON.stringify(users));writeAtomic(F.ext,JSON.stringify(ext));writeAtomic(F.mail,JSON.stringify(mail))}catch(e){log('save failed',e.message)}}

/* ---------------- identity: Firebase ID tokens are checked with Google (accounts:lookup) ---------------- */
const tokCache=new Map();
async function whoami(req){const h=String(req.headers.authorization||'');const m=/^Bearer\s+([\w.-]{100,4096})$/.exec(h);if(!m)return null;
  const k=crypto.createHash('sha256').update(m[1]).digest('base64url');const c=tokCache.get(k);if(c&&c.exp>now())return c.u;
  const r=await fetch((process.env.IDTK_BASE||'https://identitytoolkit.googleapis.com')+'/v1/accounts:lookup?key='+FB.apiKey,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:m[1]}),signal:AbortSignal.timeout(10000)});
  if(!r.ok)return null;const j=await r.json().catch(()=>({}));const a=j.users&&j.users[0];if(!a||!a.localId)return null;
  const g=(a.providerUserInfo||[]).find(p=>p.providerId==='google.com')||{};
  const u={uid:a.localId,email:String(a.email||'').toLowerCase(),name:clean(a.displayName||g.displayName||'',60),photo:clean(a.photoUrl||g.photoUrl||'',500),google:!!g.providerId,verified:!!a.emailVerified};
  tokCache.set(k,{u,exp:now()+10*60e3});if(tokCache.size>5000)tokCache.clear();return u}
const isOwner=email=>!!email&&(CFG.owners||[]).map(x=>String(x).toLowerCase()).includes(email);
function userOf(a){let u=users.users[a.uid];
  if(!u){u=users.users[a.uid]={uid:a.uid,email:a.email,name:a.name||a.email.split('@')[0],photo:a.photo,created:now(),trialStart:now(),lastSeen:now(),pro:null,tester:isOwner(a.email),verified:false,banned:false,google:a.google};save();log('new account',a.email)}
  else{u.lastSeen=now();if(a.email)u.email=a.email;if(!u.name&&a.name)u.name=a.name;if(a.photo&&!u.photoSet)u.photo=a.photo;u.google=u.google||a.google;if(isOwner(u.email))u.tester=true;save()}
  return u}
const proOf=u=>u.pro&&u.pro.until>now()?u.pro:null;
function stats(uid){const mine=ext.items.filter(x=>x.uid===uid&&x.status==='live');let dl=0,rs=0,rn=0;mine.forEach(x=>{dl+=x.downloads;const v=Object.values(x.ratings||{});rn+=v.length;v.forEach(s=>rs+=s)});return{items:mine.length,downloads:dl,ratings:rn,avg:rn?rs/rn:0}}
/* verified creator: many downloads for the size of the community and good ratings (or set by the owner) */
function isVerified(u){if(!u)return false;if(u.verified==='yes')return true;if(u.verified==='no')return false;const s=stats(u.uid),V=CFG.verify||{},n=Object.keys(users.users).length;
  return s.downloads>=Math.max(V.minDownloads||20,Math.ceil((V.share||.05)*n))&&s.ratings>=(V.minRatings||3)&&s.avg>=(V.minRating||4)}
function meJson(u){return{uid:u.uid,email:u.email,name:u.name,photo:u.photo,trialStart:u.trialStart,trialMs:CFG.trialMs||2592e5,pro:proOf(u),tester:!!u.tester,verified:isVerified(u),banned:!!u.banned,now:now()}}
function creatorJson(u){if(!u)return{name:'?',email:'',photo:'',verified:false};const s=stats(u.uid);return{uid:u.uid,name:u.name,email:u.email,photo:u.photo,verified:isVerified(u),items:s.items,downloads:s.downloads,avg:+s.avg.toFixed(2),ratings:s.ratings}}

/* ---------------- extensions ---------------- */
const CATS=['arduino','parts','bots','circuits','tools','themes','learn'];
const KINDS=['part','circuit','sketch','bot','pack'];
const MAX_EXT=20*1024*1024;
function rating(x){const v=Object.values(x.ratings||{});return{n:v.length,avg:v.length?v.reduce((a,b)=>a+b,0)/v.length:0}}
function listing(x){const r=rating(x),c=users.users[x.uid];return{id:x.id,name:x.name,desc:x.desc,cat:x.cat,kind:x.kind,ver:x.ver,size:x.size,icon:x.icon||'',downloads:x.downloads,rating:+r.avg.toFixed(2),ratings:r.n,created:x.created,updated:x.updated,creator:{uid:x.uid,name:c?c.name:'?',photo:c?c.photo:'',verified:isVerified(c)}}}
/* rank: Bayesian average of the stars + log of downloads, so a few 5-star votes do not beat a proven extension */
function score(x){const r=rating(x),C=3.5,m=5;return((r.avg*r.n+C*m)/(r.n+m))*1.0+Math.log10(1+x.downloads)*1.2}
function search(q,cat,sort,page){q=String(q||'').toLowerCase().trim();let a=ext.items.filter(x=>x.status==='live');
  if(cat&&CATS.includes(cat))a=a.filter(x=>x.cat===cat);
  if(q){const w=q.split(/\s+/).filter(Boolean);a=a.filter(x=>{const s=(x.name+' '+x.desc+' '+(users.users[x.uid]||{}).name).toLowerCase();return w.every(t=>s.includes(t))})}
  if(sort==='new')a.sort((x,y)=>y.updated-x.updated);else if(sort==='downloads')a.sort((x,y)=>y.downloads-x.downloads);else if(sort==='rating')a.sort((x,y)=>rating(y).avg-rating(x).avg||y.downloads-x.downloads);else a.sort((x,y)=>score(y)-score(x));
  const p=Math.max(0,+page||0),per=30;return{total:a.length,page:p,items:a.slice(p*per,p*per+per).map(listing),cats:CATS}}
function validExt(b){const name=clean(b.name,60).trim(),desc=clean(b.desc,2000).trim();if(name.length<3)return'name too short';if(desc.length<10)return'description too short';
  if(!CATS.includes(b.cat))return'bad category';if(!KINDS.includes(b.kind))return'bad kind';if(!b.payload||typeof b.payload!=='object')return'payload required';
  const js=JSON.stringify(b.payload);if(js.length>MAX_EXT)return'larger than 20 MB';if(b.icon&&!/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(b.icon)||String(b.icon||'').length>200000)return'bad icon';
  return null}
function upload(u,b,id){const err=validExt(b);if(err)return{ok:false,error:err};const js=JSON.stringify(b.payload);
  let x=id?ext.items.find(e=>e.id===id):null;if(id&&(!x||x.uid!==u.uid))return{ok:false,error:'not yours'};
  if(!x){if(ext.items.filter(e=>e.uid===u.uid&&now()-e.created<864e5).length>=10)return{ok:false,error:'daily upload limit'};
    x={id:crypto.randomBytes(6).toString('base64url'),uid:u.uid,created:now(),downloads:0,dlBy:[],ratings:{},status:'live'};ext.items.push(x)}
  Object.assign(x,{name:clean(b.name,60).trim(),desc:clean(b.desc,2000).trim(),cat:b.cat,kind:b.kind,ver:clean(b.ver||'1.0.0',20),icon:b.icon||'',size:js.length,updated:now(),sha:crypto.createHash('sha256').update(js).digest('hex').slice(0,16)});
  writeAtomic(path.join(EXT_DIR,x.id+'.json'),js);save();log('extension',id?'updated':'uploaded',x.name,'by',u.email);return{ok:true,item:listing(x)}}
function download(x,who){const k=crypto.createHash('sha256').update('dl|'+who).digest('base64url').slice(0,12);if(!x.dlBy.includes(k)){x.dlBy.push(k);x.downloads++;if(x.dlBy.length>200000)x.dlBy=x.dlBy.slice(-100000);save()}
  try{return JSON.parse(fs.readFileSync(path.join(EXT_DIR,x.id+'.json'),'utf8'))}catch(e){return null}}

/* ---------------- e-mail through Gmail SMTP (smtp.gmail.com:465, app password) ---------------- */
function smtpSend(to,subject,text,html){const S=CFG.smtp||{};if(!S.user||!S.pass)return Promise.reject(new Error('smtp.user / smtp.pass not set in config.json'));
  return new Promise((ok,bad)=>{const sock=tls.connect(S.port||465,S.host||'smtp.gmail.com',{servername:S.host||'smtp.gmail.com'});let buf='',step=0,done=false;
    const b64=s=>Buffer.from(s,'utf8').toString('base64'),wrap=s=>s.replace(/.{1,76}/g,'$&\r\n');
    const bnd='v'+crypto.randomBytes(8).toString('hex'),from=S.from||'Voltra';
    const body=['From: =?UTF-8?B?'+b64(from)+'?= <'+S.user+'>','To: <'+to+'>','Subject: =?UTF-8?B?'+b64(subject)+'?=','MIME-Version: 1.0','Date: '+new Date().toUTCString(),'Message-ID: <'+crypto.randomBytes(10).toString('hex')+'@voltra>',
      'Content-Type: multipart/alternative; boundary="'+bnd+'"','','--'+bnd,'Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: base64','',wrap(b64(text)),'--'+bnd,'Content-Type: text/html; charset=UTF-8','Content-Transfer-Encoding: base64','',wrap(b64(html||text.replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])).replace(/\n/g,'<br>'))),'--'+bnd+'--',''].join('\r\n');
    const seq=[null,'EHLO voltra','AUTH LOGIN',b64(S.user),b64(S.pass),'MAIL FROM:<'+S.user+'>','RCPT TO:<'+to+'>','DATA',body+'\r\n.','QUIT'];
    const expect=[220,250,334,334,235,250,250,354,250,221];
    const fail=e=>{if(done)return;done=true;try{sock.destroy()}catch(_){}bad(e)};
    sock.setTimeout(30000,()=>fail(new Error('smtp timeout')));sock.on('error',fail);
    sock.on('data',d=>{buf+=d.toString('utf8');let i;while((i=buf.indexOf('\r\n'))>=0){const line=buf.slice(0,i);buf=buf.slice(i+2);if(/^\d{3}-/.test(line))continue;
      const code=+line.slice(0,3);if(code!==expect[step])return fail(new Error('SMTP '+line.slice(0,160)));step++;
      if(step>=seq.length){done=true;sock.end();return ok(true)}sock.write(seq[step]+'\r\n')}})})}
const job={running:false,total:0,sent:0,failed:0,last:''};
function recipients(target){const all=Object.values(users.users).filter(u=>u.email&&!u.banned&&!u.noMail);
  if(target==='all')return all;if(target==='verified')return all.filter(isVerified);if(target==='pro')return all.filter(u=>proOf(u));if(target==='free')return all.filter(u=>!proOf(u));if(target==='testers')return all.filter(u=>u.tester);
  const t=String(target||'').toLowerCase();return all.filter(u=>u.uid===target||u.email===t)}
async function mailJob(list,subject,text){job.running=true;job.total=list.length;job.sent=0;job.failed=0;
  for(const u of list){const personal=text.replace(/\{name\}/g,u.name||'').replace(/\{email\}/g,u.email);
    try{await smtpSend(u.email,subject,personal);job.sent++}catch(e){job.failed++;job.last=e.message;log('mail failed',u.email,e.message);if(/535|auth/i.test(e.message))break}
    await new Promise(r=>setTimeout(r,1200))}   /* Gmail allows about 500 messages a day: one every ~1 s is safe */
  mail.log.unshift({t:now(),subject,count:job.sent,failed:job.failed});mail.log=mail.log.slice(0,200);save();job.running=false}

/* ---------------- HTTP routes (return true when handled) ---------------- */
async function route(req,res,u,ip,{send,readBody,isAdmin,limited}){
  const p=u.pathname,M=req.method;
  /* accounts */
  if(p==='/api/me'||p.startsWith('/api/me/')){const a=await whoami(req);if(!a)return send(req,res,401,{ok:false,error:'sign in required'}),true;const me=userOf(a);
    if(M==='GET'&&p==='/api/me')return send(req,res,200,Object.assign({ok:true},meJson(me))),true;
    if(M==='POST'&&p==='/api/me/profile'){const b=await readBody(req,300000);if(b.name)me.name=clean(b.name,60).trim()||me.name;if(typeof b.photo==='string'&&(b.photo===''||/^data:image\/(png|jpeg|webp);base64,/.test(b.photo)&&b.photo.length<200000)){me.photo=b.photo;me.photoSet=true}save();return send(req,res,200,Object.assign({ok:true},meJson(me))),true}
    /* testing switch shown in the app's subscription screen (testers only) */
    if(M==='POST'&&p==='/api/me/test'){if(!me.tester)return send(req,res,403,{ok:false,error:'testers only'}),true;const b=await readBody(req,4096);
      if(b.mode==='pro')me.pro={plan:'monthly',until:now()+30*864e5,by:'test'};else if(b.mode==='free'){me.pro=null;me.trialStart=now()-(CFG.trialMs||2592e5)-1000}else if(b.mode==='trial'){me.pro=null;me.trialStart=now()}
      save();return send(req,res,200,Object.assign({ok:true},meJson(me))),true}
    return send(req,res,404,{ok:false}),true}
  /* extensions store */
  if(M==='GET'&&p==='/api/ext')return send(req,res,200,Object.assign({ok:true},search(u.searchParams.get('q'),u.searchParams.get('cat'),u.searchParams.get('sort'),u.searchParams.get('page')))),true;
  let m=/^\/api\/ext\/([\w-]{6,12})(\/download|\/rate)?$/.exec(p);
  if(m){const x=ext.items.find(e=>e.id===m[1]&&(e.status==='live'));
    if(M==='GET'&&!m[2]){if(!x)return send(req,res,404,{ok:false}),true;return send(req,res,200,{ok:true,item:Object.assign(listing(x),{creator:creatorJson(users.users[x.uid])})}),true}
    if(M==='GET'&&m[2]==='/download'){if(!x)return send(req,res,404,{ok:false}),true;const a=await whoami(req).catch(()=>null);const pl=download(x,a?a.uid:ip);if(!pl)return send(req,res,410,{ok:false}),true;return send(req,res,200,{ok:true,item:listing(x),payload:pl}),true}
    if(M==='POST'&&m[2]==='/rate'){const a=await whoami(req);if(!a||!x)return send(req,res,a?404:401,{ok:false}),true;const me=userOf(a);if(me.banned)return send(req,res,403,{ok:false}),true;
      if(x.uid===me.uid)return send(req,res,400,{ok:false,error:'cannot rate your own extension'}),true;const b=await readBody(req,1024);const s=Math.round(+b.stars);if(!(s>=1&&s<=5))return send(req,res,400,{ok:false}),true;
      x.ratings[me.uid]=s;save();return send(req,res,200,{ok:true,item:listing(x)}),true}}
  if(p==='/api/ext'&&M==='POST'||(m=/^\/api\/ext\/([\w-]{6,12})$/.exec(p))&&(M==='PUT'||M==='DELETE')){const a=await whoami(req);if(!a)return send(req,res,401,{ok:false,error:'sign in required'}),true;const me=userOf(a);if(me.banned)return send(req,res,403,{ok:false,error:'account blocked'}),true;
    if(M==='DELETE'){const i=ext.items.findIndex(e=>e.id===m[1]&&e.uid===me.uid);if(i<0)return send(req,res,404,{ok:false}),true;ext.items.splice(i,1);try{fs.unlinkSync(path.join(EXT_DIR,m[1]+'.json'))}catch(e){}save();return send(req,res,200,{ok:true}),true}
    if(limited('up:'+me.uid,20,3600e3))return send(req,res,429,{ok:false,error:'slow down'}),true;
    const b=await readBody(req,MAX_EXT+400000);return send(req,res,200,upload(me,b,M==='PUT'?m[1]:null)),true}
  if(M==='GET'&&p==='/api/ext-mine'){const a=await whoami(req);if(!a)return send(req,res,401,{ok:false}),true;return send(req,res,200,{ok:true,items:ext.items.filter(e=>e.uid===a.uid).map(x=>Object.assign(listing(x),{status:x.status}))}),true}
  if(M==='GET'&&(m=/^\/api\/creator\/([\w-]{6,128})$/.exec(p))){const c=users.users[m[1]];if(!c)return send(req,res,404,{ok:false}),true;
    return send(req,res,200,{ok:true,creator:creatorJson(c),items:ext.items.filter(e=>e.uid===c.uid&&e.status==='live').map(listing)}),true}
  /* owner dashboard API */
  if(p.startsWith('/api/admin/v2/')){if(!isAdmin(req))return send(req,res,401,{ok:false,error:'admin token required'}),true;const q=p.slice(14);
    if(M==='GET'&&q==='overview'){const U=Object.values(users.users),day=now()-864e5;
      return send(req,res,200,{ok:true,users:U.length,active24:U.filter(x=>x.lastSeen>day).length,pro:U.filter(proOf).length,new24:U.filter(x=>x.created>day).length,
        ext:ext.items.length,extLive:ext.items.filter(x=>x.status==='live').length,downloads:ext.items.reduce((a,x)=>a+x.downloads,0),verified:U.filter(isVerified).length,mailJob:job,mailLog:mail.log.slice(0,20),smtp:!!(CFG.smtp&&CFG.smtp.user&&CFG.smtp.pass),smtpUser:(CFG.smtp||{}).user||''}),true}
    if(M==='GET'&&q==='users'){const s=String(u.searchParams.get('q')||'').toLowerCase();
      const list=Object.values(users.users).filter(x=>!s||(x.email+' '+x.name).toLowerCase().includes(s)).sort((a,b)=>b.lastSeen-a.lastSeen).slice(0,500)
        .map(x=>({uid:x.uid,email:x.email,name:x.name,photo:x.photo&&x.photo.length<1000?x.photo:'',created:x.created,lastSeen:x.lastSeen,trialStart:x.trialStart,pro:proOf(x),tester:!!x.tester,banned:!!x.banned,verified:isVerified(x),verifiedSet:x.verified||'',stats:stats(x.uid)}));
      return send(req,res,200,{ok:true,users:list}),true}
    if(M==='POST'&&q==='user'){const b=await readBody(req,8192);const x=users.users[b.uid];if(!x)return send(req,res,404,{ok:false}),true;
      if(b.tester!=null)x.tester=!!b.tester;if(b.banned!=null)x.banned=!!b.banned;if(b.verified!=null)x.verified=b.verified==='yes'||b.verified==='no'?b.verified:false;
      if(b.proDays!=null){const d=+b.proDays;x.pro=d>0?{plan:'monthly',until:now()+d*864e5,by:'owner'}:null}if(b.resetTrial)x.trialStart=now();save();return send(req,res,200,{ok:true}),true}
    if(M==='GET'&&q==='ext')return send(req,res,200,{ok:true,items:ext.items.slice().sort((a,b)=>b.updated-a.updated).map(x=>Object.assign(listing(x),{status:x.status,email:(users.users[x.uid]||{}).email||''}))}),true;
    if(M==='POST'&&q==='ext'){const b=await readBody(req,4096);const i=ext.items.findIndex(e=>e.id===b.id);if(i<0)return send(req,res,404,{ok:false}),true;
      if(b.action==='hide')ext.items[i].status='hidden';else if(b.action==='show')ext.items[i].status='live';else if(b.action==='delete'){try{fs.unlinkSync(path.join(EXT_DIR,b.id+'.json'))}catch(e){}ext.items.splice(i,1)}save();return send(req,res,200,{ok:true}),true}
    if(M==='POST'&&q==='mail'){const b=await readBody(req,200000);if(!(CFG.smtp&&CFG.smtp.user&&CFG.smtp.pass))return send(req,res,400,{ok:false,error:'Gmail sender not set (Messages tab)'}),true;if(job.running)return send(req,res,409,{ok:false,error:'a mailing is already running'}),true;
      const subject=clean(b.subject,200).trim(),text=clean(b.text,50000).trim();if(!subject||!text)return send(req,res,400,{ok:false,error:'subject and text required'}),true;
      const list=recipients(b.to);if(!list.length)return send(req,res,400,{ok:false,error:'no recipients'}),true;mailJob(list,subject,text);return send(req,res,200,{ok:true,count:list.length}),true}
    if(M==='POST'&&q==='smtp'){const b=await readBody(req,4096);CFG.smtp=Object.assign({},CFG.smtp||{},{user:clean(b.user,200).trim(),from:clean(b.from||'Voltra',60)});if(b.pass)CFG.smtp.pass=String(b.pass).replace(/\s+/g,'');ctx.saveCfg();return send(req,res,200,{ok:true}),true}
    if(M==='POST'&&q==='mailtest'){try{await smtpSend(CFG.smtp.user,'Voltra test','Gmail sending works.');return send(req,res,200,{ok:true}),true}catch(e){return send(req,res,200,{ok:false,error:e.message}),true}}
    return send(req,res,404,{ok:false}),true}
  return false}

return{route,flush,counts:()=>({users:Object.keys(users.users).length,ext:ext.items.filter(x=>x.status==='live').length})}};
