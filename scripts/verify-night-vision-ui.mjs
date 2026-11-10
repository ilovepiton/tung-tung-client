import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(import.meta.url);
const {chromium}=require('/Users/klim/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core');
const out=path.join(root,'artifacts/night-vision-1.29.3/ui');
await fs.mkdir(out,{recursive:true});
const checks=[],errors=[];
const check=(value,label)=>{if(!value)throw Error(label);checks.push(label);};
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',args:['--allow-file-access-from-files','--disable-background-networking']});
try {
 const page=await browser.newPage({viewport:{width:1180,height:760}});
 page.on('pageerror',error=>errors.push(error.message));
 await page.addInitScript(()=>{
  window.calls=[];window.pending=[];
  window.webkit={messageHandlers:{native:{postMessage(message){
   window.calls.push(message);
   if(message.action==='module'){window.pending.push(message);return;}
   const data=message.action==='getPreferences'?{version:'1.29.3',soundsEnabled:false}:message.action==='getState'?window.fixture||{connected:false}:{};
   setTimeout(()=>window.TungNative.reply({id:message.id,ok:true,data}),0);
  }}}};
 });
 await page.goto(pathToFileURL(path.join(root,'macos/ui/index.html')).href);
 await page.locator('#intro-close').click();
 await page.waitForTimeout(550);
 await page.evaluate(()=>window.TungNative.navigate('world-tools'));
 await page.locator('[data-category="Visual"]').click();
 const toggle=()=>page.locator('[data-toggle-module="night_vision"]');
 check(await page.locator('[data-module-card="night_vision"]').count()===1,'Night Vision appears in Visual modules');
 check(await toggle().isDisabled(),'Disconnected controls stay disabled');
 await page.evaluate(()=>{
  window.fixture={connected:true,connectionGeneration:'night-vision-ui',server:'Offline QA',modules:MODULES.map(module=>({...module,enabled:false,available:true})),settings:{},findings:[]};
  window.TungNative.update(structuredClone(window.fixture));
 });
 check(await toggle().isEnabled(),'Connected core advertises usable Night Vision');
 await toggle().click();
 await page.waitForTimeout(50);
 check(await page.evaluate(()=>window.pending.length===1&&window.pending[0].payload.id==='night_vision'&&window.pending[0].payload.enabled===true),'Switch sends the correct Minecraft module command');
 check(!(await toggle().getAttribute('class')).split(' ').includes('on'),'Pending command does not optimistically switch on');
 await page.evaluate(()=>{
  const request=window.pending.shift();
  window.fixture.modules=window.fixture.modules.map(module=>module.id==='night_vision'?{...module,enabled:true,runtimeStatus:'Full brightness · local lighting'}:module);
  window.TungNative.reply({id:request.id,ok:true,data:structuredClone(window.fixture)});
 });
 await page.waitForTimeout(80);
 check((await toggle().getAttribute('class')).split(' ').includes('on'),'Acknowledged game state switches Night Vision on');
 const image=page.locator('[data-module-card="night_vision"] img');
 check(await image.evaluate(element=>element.complete&&element.naturalWidth>0),'Night Vision icon loads');
 await page.screenshot({path:path.join(out,'night-vision-on.png')});
 await toggle().click();await page.waitForTimeout(30);
 await page.evaluate(()=>{
  const request=window.pending.shift();
  window.fixture.modules=window.fixture.modules.map(module=>module.id==='night_vision'?{...module,enabled:false}:module);
  window.TungNative.reply({id:request.id,ok:true,data:structuredClone(window.fixture)});
 });
 await page.waitForTimeout(60);
 check(!(await toggle().getAttribute('class')).split(' ').includes('on'),'Disable acknowledgement restores the off switch');
 await page.evaluate(()=>{
  window.fixture.modules=window.fixture.modules.filter(module=>module.id!=='night_vision');
  window.TungNative.update(structuredClone(window.fixture));
 });
 check(await toggle().isDisabled(),'Older cores cannot enable a missing Night Vision implementation');
 check(errors.length===0,'No JavaScript errors');
} catch(error) {errors.push(error.stack);}
finally {await browser.close();}
await fs.writeFile(path.join(out,'report.json'),JSON.stringify({passed:!errors.length,checks,errors,scope:'Production UI with an isolated acknowledged Minecraft bridge fixture; game rendering tested separately.'},null,2)+'\n');
console.log(JSON.stringify({passed:!errors.length,checks:checks.length,errors},null,2));
if(errors.length)process.exitCode=1;
