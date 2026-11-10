import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const require=createRequire(import.meta.url),{chromium}=require('/Users/klim/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),out=path.join(root,'artifacts/chess-auto-next/ui');await fs.mkdir(out,{recursive:true});
const checks=[],errors=[];const check=(v,label)=>{if(!v)throw Error(label);checks.push(label);};
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',args:['--allow-file-access-from-files','--disable-background-networking']});
try{
 const page=await browser.newPage({viewport:{width:1180,height:760}});page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  window.chessCalls=[];window.chessFixture={available:true,browserReady:true,enabled:false,minutes:3,increment:0,delaySeconds:3,revision:0,connected:false,phase:'disconnected',starts:0};
  window.webkit={messageHandlers:{native:{postMessage(message){
   window.chessCalls.push(message);let data={};
   if(message.action==='getPreferences')data={version:'1.29.7',soundsEnabled:false};
   else if(message.action==='getState')data={connected:false,chess:structuredClone(window.chessFixture)};
   else if(message.action==='chessState')data={chess:structuredClone(window.chessFixture)};
   else if(message.action==='chessSettings'){const p=message.payload;window.chessFixture={...window.chessFixture,...p,revision:window.chessFixture.revision+1};data={chess:structuredClone(window.chessFixture)};}
   else if(message.action.startsWith('chess'))data={chess:structuredClone(window.chessFixture)};
   setTimeout(()=>window.TungNative.reply({id:message.id,ok:true,data}),15);
  }}}};
 });
 await page.goto(pathToFileURL(path.join(root,'macos/ui/index.html')).href);await page.waitForTimeout(350);await page.evaluate(()=>{window.TungNative.navigate('chess');document.querySelector('#continue-intro')?.click();});await page.waitForTimeout(550);
 check(await page.locator('[data-chess-toggle]').isEnabled(),'Chess controls work while Minecraft is disconnected');
 check(await page.locator('[data-chess-time]').count()===11,'Eleven popular clocks plus a custom clock are available');
 check(await page.locator('.workspace-label').textContent()==='CHESS WORKSPACE','Chess has its own sidebar');
 check(await page.locator('.footer').textContent().then(s=>s.includes('CHESS.COM')&&!s.includes('CORE DISCONNECTED')),'Chess footer describes its own connection');
 await page.screenshot({path:path.join(out,'01-chess-off.png')});
 await page.locator('[data-chess-toggle]').click();await page.waitForTimeout(80);
 check(await page.locator('[data-chess-toggle]').getAttribute('aria-pressed')==='true','Auto Next toggle follows the acknowledged desktop setting');
 check(await page.evaluate(()=>connectionState.connected===false),'Chess acknowledgement does not turn the Minecraft connection on');
 for(const [m,i]of [[1,0],[1,1],[2,1],[3,0],[3,2],[5,0],[5,5],[10,0],[10,5],[15,10],[30,0]]){
  await page.locator(`[data-chess-time="${m},${i}"]`).click();await page.waitForTimeout(65);
  check(await page.evaluate(([m,i])=>window.chessFixture.minutes===m&&window.chessFixture.increment===i,[m,i]),`Clock ${m}+${i} reaches the native bridge`);
 }
 await page.locator('[data-chess-custom]').click();await page.locator('#chess-minutes').fill('7');await page.locator('#chess-increment').fill('12');await page.locator('.chess-custom-form [type="submit"]').click();await page.waitForTimeout(80);
 check(await page.evaluate(()=>window.chessFixture.minutes===7&&window.chessFixture.increment===12),'Custom minutes and increment are saved');
 check(await page.locator('[data-chess-custom]').getAttribute('aria-pressed')==='true','Custom time remains visibly selected');
 await page.locator('[data-chess-custom]').click();await page.locator('#chess-minutes').fill('0');
 check(await page.locator('.chess-custom-form [type="submit"]').isDisabled(),'Invalid custom clocks cannot be applied');
 await page.locator('#chess-minutes').fill('15');await page.screenshot({path:path.join(out,'02-custom-clock.png')});await page.locator('[data-chess-custom-cancel]').click();
 for(const n of [1,3,5,10]){await page.locator(`[data-chess-delay="${n}"]`).click();await page.waitForTimeout(65);check(await page.evaluate(n=>window.chessFixture.delaySeconds===n,n),'Post-game pause '+n+'s is saved');}
 await page.locator('.chess-launch [data-chess-window]').click();await page.waitForTimeout(80);check(await page.evaluate(()=>window.chessCalls.some(c=>c.action==='chessWindow')),'Open Chess Window uses the dedicated native window');
 await page.locator('[data-chess-setup]').click();await page.waitForTimeout(80);check(await page.evaluate(()=>window.chessCalls.some(c=>c.action==='chessSetup')),'Chrome setup opens the packaged extension');
 await page.locator('[data-chess-pair]').click();await page.waitForTimeout(80);check(await page.evaluate(()=>window.chessCalls.some(c=>c.action==='chessPair')),'Connect Chrome uses the private pairing action');
 await page.evaluate(()=>{window.chessFixture={...window.chessFixture,connected:true,client:'Chrome',phase:'countdown',secondsLeft:3,minutes:3,increment:0};window.TungNative.update({connected:false,chess:structuredClone(window.chessFixture)});});
 check(await page.locator('.chess-live').textContent().then(s=>s.includes('Next game in 3 seconds')),'Confirmed countdown is shown in the client');
 check(await page.locator('.chess-connect').textContent().then(s=>s.includes('Connected')),'Chrome connection is shown separately from Minecraft');
 await page.screenshot({path:path.join(out,'03-chess-on.png')});
 const layout=await page.evaluate(()=>{
  const r=s=>document.querySelector(s).getBoundingClientRect();const a=r('.chess-auto'),b=r('.chess-pace'),c=r('.chess-breather'),d=r('.chess-launch'),right=r('.chess-aside'),footer=r('.footer');
  return a.bottom<=b.top&&b.bottom<=c.top&&c.bottom<=d.top&&d.bottom<footer.top&&a.right<right.left&&right.bottom<footer.top;
 });check(layout,'Main cards, right connection panel and footer do not overlap');
 await page.evaluate(()=>window.TungNative.update({connected:false,chess:{available:false}}));
 check(await page.locator('[data-chess-toggle]').isDisabled(),'An older desktop host cannot show a working Auto Next toggle');
 check(errors.length===0,'No JavaScript errors');
}catch(e){errors.push(e.stack);}finally{await browser.close();}
await fs.writeFile(path.join(out,'report.json'),JSON.stringify({ok:errors.length===0,checks:checks.length,details:checks,errors,scope:'Actual production UI with an isolated acknowledged native fixture; no accounts or live games.'},null,2)+'\n');
console.log(JSON.stringify({ok:!errors.length,checks:checks.length,errors},null,2));if(errors.length)process.exitCode=1;
