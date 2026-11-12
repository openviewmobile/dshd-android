import { chromium } from 'playwright-core';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const ROOT=path.dirname(new URL(import.meta.url).pathname);
const MIME={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json'};
const srv=http.createServer((req,res)=>{let f=path.join(ROOT,decodeURIComponent(req.url.split('?')[0]));if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404).end();return;}res.writeHead(200,{'Content-Type':MIME[path.extname(f)]||'application/octet-stream'});fs.createReadStream(f).pipe(res);});
await new Promise(r=>srv.listen(0,'127.0.0.1',r)); const port=srv.address().port;
const b=await chromium.launch({channel:'chrome',headless:true,args:['--use-angle=metal','--hide-scrollbars']});
const p=await b.newPage({viewport:{width:1080,height:1920}});
for(const lang of ['zh','en']){
  await p.goto(`http://127.0.0.1:${port}/src/scene.html?lang=${lang}`,{waitUntil:'networkidle'});
  await p.waitForFunction('window.__ready===true');
  const txt=await p.evaluate(()=>document.body.textContent);
  const latin=[...new Set(txt.match(/[A-Za-z][A-Za-z0-9_.:/-]*/g)||[])];
  const cjk=[...new Set(txt.match(/[\u4e00-\u9fff]+/g)||[])];
  console.log(lang,'LATIN:',latin.join(' | '));
  console.log(lang,'CJK count:',cjk.length, cjk.slice(0,12).join(' '));
  if(lang==='en' && cjk.length) throw new Error('English scene contains CJK text');
  if(lang==='zh'){ const allowed=new Set(['DSHD']); const bad=latin.filter(w=>!allowed.has(w)); if(bad.length) throw new Error('Chinese scene contains English: '+bad.join(',')); }
}
console.log('language separation OK');
await b.close(); srv.close();
