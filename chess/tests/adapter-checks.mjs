import fs from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
vm.runInThisContext(await fs.readFile(new URL('../engine.js',import.meta.url),'utf8'));
const checks=[];
function read(path,{active=false,score=''}={}){
 const adapter=new TungChess.Adapter({document:{},location:{pathname:path}});
 adapter.elements=selector=>selector.startsWith('[data-cy="resign')&&active?[{matches:()=>true}]:selector==='.game-result,.result-row'&&score?[{textContent:score}]:[];
 adapter.buttons=()=>[];adapter.startButton=()=>null;return adapter.read();
}
function check(value,label){assert.ok(value,label);checks.push(label);}
for(const path of ['/game/123','/game/live/123']){
 const live=read(path,{active:true,score:'0-1'});
 check(live.phase==='playing'&&live.own&&live.gameKey==='123',path+': own live controls take priority over stale scores');
 const ended=read(path,{score:'0-1'});
 check(ended.phase==='finished'&&!ended.own&&ended.gameKey==='123',path+': a score alone never establishes that this was our game');
}
for(const path of ['/analysis/game/live/123','/game/daily/123','/game/123/analysis'])check(read(path,{active:true,score:'1-0'}).phase==='unsupported',path+': review/daily routes cannot queue a live game');
check(read('/game/123',{score:'1. e4'}).phase==='loading','Move notation is not a terminal score');
const report={ok:true,checks:checks.length,details:checks,liveGamesStarted:false,userProfileTouched:false};
await fs.writeFile('artifacts/chess-auto-next/adapter-checks.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
