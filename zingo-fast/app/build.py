#!/usr/bin/env python3
"""ZINGO Fast build: index.dev.html (source of truth) -> index.html (release).
Release = dev tools REMOVED (not hidden), monetisation sections minified, whole script wrapped in a closure,
sensitive objects frozen. Every replacement is asserted, so a changed source fails the build instead of silently shipping dev code."""
import re,sys
src=sys.argv[1] if len(sys.argv)>1 else 'index.dev.html'; out=sys.argv[2] if len(sys.argv)>2 else 'index.html'
s=open(src,encoding='utf-8').read()
def rep(a,b,cnt=1):
    global s
    assert s.count(a)==cnt,('build: pattern not found/unique',a[:70],s.count(a)); s=s.replace(a,b)
def cut(a,b,keep_b=True,cnt_check=True):
    global s
    i=s.index(a); j=s.index(b,i); s=s[:i]+(s[j:] if keep_b else s[j+len(b):])
def sub(rx,to,flags=0,exp=1):
    global s
    s,n=re.subn(rx,to,s,flags=flags); assert n==exp,('build: regex',rx[:60],n)
rep("const DEV_BUILD=true;","const DEV_BUILD=false;")
cut("/* ---- DEV ONLY: console self-tests","/* ===== END SECTION: entitlement ===== */")
cut(" function force(k){"," const ready=init();")
rep("if(env.dev)api._dev={grant,revoke,enc,dec,force};","if(!env.dev)Object.freeze(api);")
i=s.index("/* DEVELOPER ONLY (removed when DEV_BUILD=false)"); j=s.index("}:null;\n",i)+len("}:null;\n"); s=s[:i]+"const DevProvider=null;\n"+s[j:]
rep("const devSim=()=>DEV_BUILD&&Store.get('zf_dev_sim')==='1';","const devSim=()=>false;")
sub(r"  const dev=DEV_BUILD\?.*?:'';\n","  const dev='';\n",re.S)
sub(r"   if\(b\.dataset\.f\)\{const k=b\.dataset\.f,d=Entitlement\._dev;[^\n]*\n","")
sub(r"  el\.addEventListener\('change',e=>\{if\(e\.target\.id==='pwDev'\)[^\n]*\n","")
rep("[data-r],[data-l],[data-f],#pwGo","[data-r],[data-l],#pwGo")
s='\n'.join(l for l in s.split('\n') if '.pw-dev' not in l)
sub(r"\s*dev_label:.*?dev_wipe:'[^']*'","",re.S,exp=2)
rep("/* ===== END SECTION: free tier ===== */","[BILLING,BILLING.plans,BILLING.plans.daily,BILLING.plans.weekly,BILLING.plans.monthly,BILLING.links,FEATURES,FREE_TIER].forEach(Object.freeze);\n/* ===== END SECTION: free tier ===== */")
# minify the monetisation sections: drop comments (string-aware), trailing spaces, blank runs
def strip_comments(t):
    o=[];i=0;q=None;n=len(t)
    while i<n:
        c=t[i]
        if q:
            o.append(c)
            if c=='\\': o.append(t[i+1]); i+=1
            elif c==q: q=None
        elif c in '\'"`': q=c;o.append(c)
        elif c=='/' and t[i+1:i+2]=='*':
            j=t.index('*/',i+2); i=j+1
        else: o.append(c)
        i+=1
    return ''.join(o)
for name in ['entitlement','paywall','feature gating','free tier','trial lifecycle']:
    a=s.index('/* ===== SECTION: '+name); b=s.index('/* ===== END SECTION: '+name); b=s.index('*/',b)+2
    sec=strip_comments(s[a:b]); sec=re.sub(r'[ \t]+\n','\n',sec); sec=re.sub(r'\n{2,}','\n',sec)
    s=s[:a]+sec+s[b:]
# closure: nothing but window.ZingoBilling / window.ZingoNative stays reachable from the console
rep('<script>\n"use strict";','<script>\n(function(){"use strict";')
i=s.rindex('</script>'); s=s[:i]+'})();\n'+s[i:]
for bad in ['selfTest','_dev','pw-dev','dev_label','zf_dev_sim','DEVELOPER ONLY','DevProvider=DEV']:
    assert bad not in s,('dev code leaked into release:',bad)
open(out,'w',encoding='utf-8').write(s)
print('built',out,len(s),'bytes')
