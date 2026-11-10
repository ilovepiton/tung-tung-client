// Read-only guest lobby integration: select clocks, never submit matchmaking.
import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url),{chromium}=require('/Users/klim/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core');
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',args:['--disable-background-networking']});
const checks=[];
try{
 const page=await browser.newPage();
 await page.goto('https://www.chess.com/play/online',{waitUntil:'domcontentloaded',timeout:45000});
 await page.getByRole('button',{name:'Start Game',exact:true}).waitFor({timeout:20000});
 const consent=page.getByRole('button',{name:'Reject All',exact:true});if(await consent.isVisible())await consent.click();
 await page.addScriptTag({path:'chess/engine.js'});
 await page.evaluate(()=>{window.adapter=new TungChess.Adapter(window);document.addEventListener('click',e=>{if(e.target.closest('button')?.textContent.trim()==='Start Game'){e.preventDefault();e.stopImmediatePropagation();throw Error('Matchmaking must never be clicked by this check');}},true);});
 for(const minutes of [1,3,5,10]){
  let result='wait';
  for(let i=0;i<15&&result==='wait';i++){result=await page.evaluate(m=>adapter.ensureTime(m,0),minutes);if(result==='wait')await page.waitForTimeout(450);}
  assert.equal(result,'ready',`Clock ${minutes} min`);
  assert.deepEqual(await page.evaluate(()=>adapter.currentTime()),[minutes,0]);
  assert.equal(await page.evaluate(()=>adapter.read().phase),'lobby');
  checks.push(`Production adapter selected ${minutes} minutes in the current public Chess.com lobby`);
 }
 const report={ok:true,checks:checks.length,details:checks,liveGamesStarted:false,userProfileTouched:false,scope:'Fresh anonymous browser context; only time selectors were clicked.'};
 await fs.writeFile('artifacts/chess-auto-next/public-clock-check.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
}finally{await browser.close();}
