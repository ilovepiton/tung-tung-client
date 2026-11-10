/* Shared by the owned Chess window and the Chrome content script. No moves or chess engine. */
(function(root){
 'use strict';
 const KEY='tung-chess:auto-next:v1',TTL=45000;
 const validSettings=s=>s&&typeof s.enabled==='boolean'&&typeof s.allowed==='boolean'&&typeof s.binding==='string'&&Number.isInteger(s.minutes)&&s.minutes>=1&&s.minutes<=180&&Number.isInteger(s.increment)&&s.increment>=0&&s.increment<=180&&Number.isInteger(s.delaySeconds)&&s.delaySeconds>=1&&s.delaySeconds<=15;
 class Engine{
  constructor({adapter,storage,now=Date.now,random=()=>crypto.randomUUID()}){
   this.adapter=adapter;this.storage=storage;this.now=now;this.random=random;this.deadline=0;this.countdownKey='';this.lastRevision=-1;this.storageError=false;
   try{this.data=JSON.parse(storage.getItem(KEY)||'{}');if(!this.data||typeof this.data!=='object'||Array.isArray(this.data))this.data={};}
   catch{this.data={};this.storageError=true;}
   if(typeof this.data.clientId!=='string'||!/^[a-z0-9-]{8,80}$/i.test(this.data.clientId))this.data={clientId:random()};
   this.save();
  }
  save(){try{this.storage.setItem(KEY,JSON.stringify(this.data));return true;}catch{this.storageError=true;return false;}}
  status(phase,code='',secondsLeft=0){return{phase:phase==='loading'?'lobby':phase,code,secondsLeft};}
  step(settings){
   const board=this.adapter.read(),now=this.now();
   if(this.storageError)return this.status('error','storage-unavailable');
   if(!validSettings(settings)){this.deadline=0;return this.status('paused','connection-lost');}
   if(board.phase==='playing'&&board.own){
    if(!this.data.activeNow||!this.data.activeKey||this.data.activePath!==board.path||board.gameKey&&this.data.activeKey!==board.gameKey){this.data.activeKey=board.gameKey||this.random();this.data.activePath=board.path;}
    delete this.data.pending;this.data.seenOwn=true;this.data.activeNow=true;this.deadline=0;this.save();return this.status('playing');
   }
   if(board.phase!=='loading'){this.data.activeNow=false;this.save();}
   if(board.phase==='unsupported'){this.data.seenOwn=false;this.save();}
   if(!settings.enabled){this.deadline=0;delete this.data.pending;this.save();return this.status(board.phase);}
   if(!settings.allowed){this.deadline=0;return this.status(board.phase==='unsupported'?'unsupported':'paused');}
   if(this.lastRevision!==settings.revision){this.deadline=0;this.lastRevision=settings.revision;}
   const pending=this.data.pending;
   if(pending){
    if(typeof pending.created!=='number'||now-pending.created>TTL||now<pending.created||pending.binding!==settings.binding){delete this.data.pending;this.save();return this.status('error','expired');}
    if(board.phase==='unsupported'){delete this.data.pending;this.save();return this.status('unsupported');}
    if(pending.failed)return this.status('error',pending.failed);
    if(pending.submittedAt){
     if(board.phase==='lobby'&&now-pending.submittedAt>10000)return this.status('error','start-unavailable');
     return this.status('searching');
    }
    if(board.phase==='searching')return this.status('searching');
    if(board.phase!=='lobby')return this.status('selecting');
    const selected=this.adapter.ensureTime(settings.minutes,settings.increment);
    if(selected!=='ready'){
     if(now-pending.created>20000){pending.failed=selected==='unavailable'?'time-unavailable':'select-timeout';this.save();return this.status('error',pending.failed);}
     return this.status('selecting');
    }
    // Re-read immediately before submitting; a manually started match wins the race.
    if(this.adapter.read().phase!=='lobby')return this.status('selecting');
    pending.submittedAt=now||1;pending.minutes=settings.minutes;pending.increment=settings.increment;
    if(!this.save())return this.status('error','storage-unavailable');
    if(!this.adapter.start(settings.minutes,settings.increment)){pending.failed='start-unavailable';this.save();return this.status('error',pending.failed);}
    return this.status('searching');
   }
   if(board.phase!=='finished'){this.deadline=0;return this.status(board.phase);}
   const observedOwn=this.data.seenOwn&&this.data.activePath===board.path&&(!board.gameKey||board.gameKey===this.data.activeKey);
   if(!board.own&&!observedOwn)return this.status('unsupported');
   if(!this.data.activeKey){this.data.activeKey=board.gameKey||this.random();this.data.activePath=board.path;this.save();}
   const game=board.gameKey||this.data.activeKey;
   if(this.data.finishedKey===game)return this.status('finished');
   if(!this.deadline||this.countdownKey!==game){this.deadline=now+settings.delaySeconds*1000;this.countdownKey=game;}
   if(now<this.deadline)return this.status('countdown','',Math.ceil((this.deadline-now)/1000));
   this.data.finishedKey=game;this.data.pending={created:now,binding:settings.binding,key:game};
   if(!this.save())return this.status('error','storage-unavailable');
   this.adapter.navigate();return this.status('selecting');
  }
 }
 const visible=e=>!!e&&e.isConnected&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden'&&getComputedStyle(e).display!=='none';
 const text=e=>(e?.textContent||'').replace(/\s+/g,' ').trim();
 const parseTime=value=>{
  const s=String(value||'').trim();let m=s.match(/^(\d+)\s*(?:\+|\|)\s*(\d+)(?:\s|$)/);if(m)return[+m[1],+m[2]];
  m=s.match(/^(\d+)\s*(?:min|minutes?|мин(?:\.|ут[а-я]*)?)(?:\b|\s|\()/i);return m?[+m[1],0]:null;
 };
 class Adapter{
  constructor(win){this.win=win;this.doc=win.document;this.actionAt=0;}
  elements(selector){return[...this.doc.querySelectorAll(selector)].filter(visible);}
  buttons(){return this.elements('button,[role="button"]');}
  read(){
   const path=this.win.location.pathname,gameKey=path.match(/^\/game\/(?:live\/)?(\d+)\/?$/)?.[1]||'';
   if(!/^\/(?:play(?:\/online)?\/?|game\/(?:live\/)?\d+\/?)$/.test(path))return{phase:'unsupported',own:false,path,gameKey};
   const ending=this.elements('[data-cy="game-over-modal"],.game-over-modal-content,.game-over-modal-component,.game-over-component,.game-over-dialog,.game-over-dialog-content,.game-over-header-component,.game-over-modal,.game-over-message-component');
   const controls=this.elements('[data-cy="resign-button"],.resign-button-component,.draw-button-component,[data-cy="abort-button"]');
   const own=controls.some(e=>e.matches('.resign-button-component,.draw-button-component'))||controls.some(e=>!e.closest('[disabled],[aria-disabled="true"],[data-disabled="true"],.resign-button-disabled,.draw-button-disabled'))||this.buttons().some(e=>!e.disabled&&e.getAttribute('data-disabled')!=='true'&&!e.closest('.resign-button-disabled,.draw-button-disabled')&&/^(?:resign|сдаться|abort game|прервать партию)$/i.test(e.getAttribute('aria-label')||text(e)));
   if(own)return{phase:'playing',own:true,path,gameKey};
   const score=this.elements('.game-result,.result-row').some(e=>/^(?:1-0|0-1|½-½|1\/2-1\/2)$/.test(text(e)));
   if(ending.length||score){const ownResult=ending.some(e=>/(?:you (?:won|lost|win|lose)|вы (?:выиграли|проиграли|победили)|you drew)/i.test(text(e)));return{phase:'finished',own:ownResult,path,gameKey};}
   if(this.elements('.seek-player-component,.seek-game-component,[data-cy="searching-game"],.new-game-index-seeking').length||this.buttons().some(e=>/^(?:cancel|отмена)$/i.test(text(e))&&e.closest('[class*="seek"],[class*="search"]')))return{phase:'searching',own:false,path,gameKey};
   const start=this.startButton();return{phase:start?'lobby':'loading',own:false,path,gameKey};
  }
  picker(){return this.buttons().find(b=>b.matches('[class*="dropdown-button"],[data-cy="time-control-selector"]')&&parseTime(text(b)));}
  currentTime(){return parseTime(text(this.picker()));}
  ensureTime(minutes,increment){
   const picker=this.picker();if(!picker)return'wait';
   const current=this.currentTime(),options=this.elements('.time-selector-button-button,[data-cy="time-control-option"]');
   if(current&&current[0]===minutes&&current[1]===increment){if(options.length){if(Date.now()-this.actionAt>400){picker.click();this.actionAt=Date.now();}return'wait';}return'ready';}
   if(Date.now()-this.actionAt<400)return'wait';
   const match=options.find(b=>{const t=parseTime(text(b));return t&&t[0]===minutes&&t[1]===increment;});
   if(match){match.click();this.actionAt=Date.now();return'wait';}
   const custom=this.elements('form.time-selector-custom-component')[0];
   if(custom){
    const inputs=[...custom.querySelectorAll('input[type="number"]')];if(inputs.length!==3)return'unavailable';
    const setter=Object.getOwnPropertyDescriptor(this.win.HTMLInputElement.prototype,'value').set;
    for(const [i,v]of [minutes,0,increment].entries()){setter.call(inputs[i],String(v));inputs[i].dispatchEvent(new this.win.Event('input',{bubbles:true}));inputs[i].dispatchEvent(new this.win.Event('change',{bubbles:true}));}
    const submit=custom.querySelector('button[type="submit"]');if(!visible(submit)||submit.disabled)return'unavailable';
    submit.click();this.actionAt=Date.now();return'wait';
   }
   if(options.length){
    const more=this.buttons().find(b=>/^(?:more time controls|more|другие контроли времени|больше)$/i.test(text(b)));
    if(more){more.click();this.actionAt=Date.now();return'wait';}return'unavailable';
   }
   picker.click();this.actionAt=Date.now();return'wait';
  }
  startButton(){return this.buttons().find(b=>!b.disabled&&b.getAttribute('data-disabled')!=='true'&&(/^(?:start game|начать игру|начать партию)$/i.test(text(b))||b.matches('[data-cy="start-game-button"]')));}
  start(minutes,increment){const t=this.currentTime(),button=this.startButton();if(this.read().phase!=='lobby'||!button||!t||t[0]!==minutes||t[1]!==increment)return false;button.click();return true;}
  navigate(){this.win.location.assign('https://www.chess.com/play/online');}
 }
 function boot(transport,win=root){
  if(win.top!==win||!['www.chess.com','chess.com'].includes(win.location.hostname)||win.location.protocol!=='https:')return;
  if(win.__tungChessRunning)return;win.__tungChessRunning=true;
  let storage;try{storage=win.sessionStorage;}catch{storage={getItem(){throw Error();},setItem(){throw Error();}};}
  const engine=new Engine({adapter:new Adapter(win),storage}),doc=win.document;
  let settings=null,current={phase:'lobby',secondsLeft:0,code:''},busy=false,claim=false,localPaused=false;
  const host=doc.createElement('div');host.id='tung-chess-status';const shadow=host.attachShadow({mode:'closed'});
  shadow.innerHTML='<style>:host{position:fixed;right:18px;bottom:18px;z-index:2147483646;font:12px/1.5 system-ui;color:#f2f5ed}.box{display:flex;align-items:center;gap:12px;background:#172019;border:1px solid #617e45;border-radius:14px;padding:11px 15px;box-shadow:0 8px 32px #0006}.dot{width:7px;height:7px;border-radius:50%;background:#9bd564}.text{display:flex;flex-direction:column}b{font-size:12px}small{font-size:10px;color:#b5c7a7}button{font:600 11px system-ui;border:1px solid #60784b;border-radius:7px;padding:7px 10px;background:#293922;color:#f0f5eb;cursor:pointer}</style><div class="box"><i class="dot"></i><div class="text"><b>Auto Next Game</b><small></small></div><button type="button">PAUSE</button></div>';
  const label=shadow.querySelector('small'),button=shadow.querySelector('button');
  button.addEventListener('click',async e=>{if(!e.isTrusted)return;if(settings?.allowed===false){claim=true;await tick();}else{localPaused=true;try{await transport.stop(engine.data.clientId);}catch{}settings=null;current=engine.step(null);host.remove();}});
  function badge(){
   if(!settings?.enabled||current.phase==='unsupported'){host.remove();return;}
   if(!host.isConnected)doc.documentElement.append(host);
   const time=settings.increment?settings.minutes+' + '+settings.increment:settings.minutes+' min';
   const labels={playing:'Playing · next game after the result',finished:'Game finished',lobby:'Start your first game',countdown:'Next game in '+current.secondsLeft+'s',selecting:'Choosing '+time,searching:'Finding your next opponent',paused:settings.allowed?'Client connection paused':'Another board is following',unsupported:'Available in your own live games',error:'Paused · check Auto Next Game in the client'};
   label.textContent=time+' · '+(labels[current.phase]||'Ready');button.textContent=settings.allowed===false?'USE THIS BOARD':'PAUSE';
  }
  async function tick(){
   if(busy)return;busy=true;
   try{
    const raw=engine.adapter.read();
    const eligible=raw.phase!=='finished'||raw.own||engine.data.seenOwn&&engine.data.activePath===raw.path&&(!raw.gameKey||raw.gameKey===engine.data.activeKey);
    const phase=!eligible?'unsupported':raw.phase==='playing'?'playing':['countdown','selecting','searching','error'].includes(current.phase)?current.phase:raw.phase==='loading'?'lobby':raw.phase;
    settings=await transport.poll({clientId:engine.data.clientId,phase,code:current.code,secondsLeft:current.secondsLeft,claim});claim=false;
    if(localPaused){current=engine.step({...settings,enabled:false});if(!settings.enabled)localPaused=false;host.remove();}
    else{current=engine.step(settings);badge();}
   }catch{settings=null;current=engine.step(null);host.remove();}finally{busy=false;}
  }
  tick();const timer=win.setInterval(tick,750);win.addEventListener('pagehide',()=>win.clearInterval(timer),{once:true});
  return{engine,tick};
 }
 root.TungChess={Engine,Adapter,parseTime,boot};
})(typeof globalThis!=='undefined'?globalThis:window);
