import fs from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
vm.runInThisContext(await fs.readFile(new URL('../engine.js',import.meta.url),'utf8'));
const checks=[];const check=(value,label)=>{assert.ok(value,label);checks.push(label);};
const base={enabled:true,allowed:true,binding:'chrome-1-client0001',minutes:3,increment:0,delaySeconds:3,revision:0};
function fixture(){
 let now=10000,serial=0,view={phase:'playing',own:true,path:'/game/live/100',gameKey:'100'},ready='ready';
 const saved=new Map(),calls={navigate:0,start:0,time:[]};
 const adapter={read:()=>({...view}),navigate(){calls.navigate++;view={phase:'loading',own:false,path:'/play/online',gameKey:''};},ensureTime(m,i){calls.time.push([m,i]);return ready;},start(m,i){calls.start++;calls.startedWith=[m,i];return true;}};
 const storage={getItem:k=>saved.get(k)||null,setItem:(k,v)=>saved.set(k,v)};
 const make=()=>new TungChess.Engine({adapter,storage,now:()=>now,random:()=>`client000${++serial}`});
 return{adapter,storage,calls,make,engine:make(),setView:v=>view=v,advance:n=>now+=n,setReady:r=>ready=r};
}
for(const outcome of ['win','loss','draw','resignation','timeout','aborted']){
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:false,path:'/game/live/100',gameKey:'100',outcome});
 check(f.engine.step(base).secondsLeft===3,`${outcome}: confirmed own game gets a cancellable countdown`);
 f.advance(3000);f.engine.step(base);check(f.calls.navigate===1,`${outcome}: exactly one lobby handoff`);
 check(f.engine.step(base).phase==='selecting'&&f.calls.start===0,`${outcome}: waits for the real lobby to finish loading`);
 f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});f.engine.step(base);f.engine.step(base);
 check(f.calls.start===1&&String(f.calls.startedWith)==='3,0',`${outcome}: one queue request with the chosen clock`);
 const reloaded=f.make();reloaded.step(base);check(f.calls.start===1,`${outcome}: reloading after submission never duplicates the request`);
}
{
 const f=fixture();f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});for(let i=0;i<5;i++)f.engine.step(base);
 check(!f.calls.start&&!f.calls.navigate,'Enabling in a fresh lobby never starts an unsolicited first game');
 f.setView({phase:'finished',own:false,path:'/game/live/999',gameKey:'999'});f.engine.step(base);f.advance(5000);f.engine.step(base);
 check(!f.calls.start&&!f.calls.navigate,'Spectating a finished game cannot trigger matchmaking');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:false,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(2000);f.engine.step({...base,enabled:false,revision:1});f.advance(5000);f.engine.step({...base,enabled:false,revision:1});
 check(f.calls.navigate===0,'Switching off cancels an active countdown');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:false,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(3000);f.engine.step({...base,minutes:10,revision:1});
 check(f.calls.navigate===0,'Changing the time resets the current countdown');
 f.advance(3000);f.engine.step({...base,minutes:10,revision:1});f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});f.engine.step({...base,minutes:10,revision:1});
 check(String(f.calls.startedWith)==='10,0','The latest selected time is used after the result');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:true,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(3000);f.engine.step(base);f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});f.setReady('unavailable');f.advance(21000);f.engine.step(base);
 check(f.calls.start===0,'An unavailable clock never falls back to another time');
 f.setReady('ready');f.engine.step(base);check(f.calls.start===0,'A failed selection stays paused until manual intervention');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:true,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(3000);f.engine.step(base);f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});
 f.engine.step({...base,binding:'chrome-2-cloned-tab'});check(f.calls.start===0,'A cloned tab cannot reuse the original tab’s pending match');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:true,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(3000);f.engine.step(base);f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});
 f.advance(46000);f.engine.step(base);check(f.calls.start===0,'Expired handoffs cannot queue a late surprise match');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:true,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(3000);f.engine.step(base);f.setView({phase:'playing',own:true,path:'/game/live/101',gameKey:'101'});f.engine.step(base);
 check(f.calls.start===0&&!f.engine.data.pending,'A manually started game replaces the pending auto-start');
}
{
 const f=fixture();f.setView({phase:'playing',own:true,path:'/play/online',gameKey:''});f.engine.step(base);
 for(let n=0;n<3;n++){
  f.setView({phase:'finished',own:false,path:'/play/online',gameKey:''});f.engine.step(base);f.advance(3000);f.engine.step(base);
  f.setView({phase:'lobby',own:false,path:'/play/online',gameKey:''});f.engine.step(base);
  f.setView({phase:'playing',own:true,path:'/play/online',gameKey:''});f.engine.step(base);
 }
 check(f.calls.start===3,'Repeated games in an SPA without URL changes each get a new match identity');
}
{
 const f=fixture();f.engine.step(base);f.setView({phase:'finished',own:true,path:'/game/live/100',gameKey:'100'});f.engine.step(base);f.advance(4000);f.engine.step(null);
 check(f.calls.navigate===0,'Losing the desktop connection pauses the countdown');
 f.engine.step({...base,allowed:false});check(f.calls.navigate===0,'A different leased board cannot auto-start this tab');
}
for(const [label,wanted]of [['1 min (Bullet)',[1,0]],['3 min (Blitz)',[3,0]],['15 + 10',[15,10]],['5 | 5',[5,5]],['10 мин (Рапид)',[10,0]]])check(JSON.stringify(TungChess.parseTime(label))===JSON.stringify(wanted),'Public time-control label parsed: '+label);
check(TungChess.parseTime('Start Game')===null,'Ordinary buttons are never interpreted as clock selectors');
const report={ok:true,checks:checks.length,details:checks,liveGamesStarted:false,userProfileTouched:false};
await fs.mkdir('artifacts/chess-auto-next',{recursive:true});await fs.writeFile('artifacts/chess-auto-next/engine-checks.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({...report,details:undefined}));
