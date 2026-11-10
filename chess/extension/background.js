'use strict';
const ENDPOINT='http://127.0.0.1:17463',PHASES=new Set(['lobby','playing','finished','countdown','selecting','searching','paused','unsupported','error']);
function ownPage(sender){return sender.id===chrome.runtime.id&&typeof sender.url==='string'&&sender.url.startsWith(chrome.runtime.getURL(''));}
function chessPage(sender){try{const u=new URL(sender.url);return sender.id===chrome.runtime.id&&sender.frameId===0&&u.protocol==='https:'&&['www.chess.com','chess.com'].includes(u.hostname)&&Number.isInteger(sender.tab?.id);}catch{return false;}}
async function request(path,body,keyOverride){
 const key=keyOverride||(await chrome.storage.local.get('pairKey')).pairKey;
 if(typeof key!=='string'||!/^[a-f0-9]{64}$/.test(key))throw Error('In Tung Tung Client, open Chess.com and choose Connect Chrome.');
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),2000);
 try{
  const response=await fetch(ENDPOINT+path,{method:'POST',headers:{'Content-Type':'application/json','X-Tung-Chess-Key':key},body:JSON.stringify(body),signal:controller.signal,credentials:'omit',cache:'no-store'});
  const data=await response.json();if(!response.ok)throw Error(data.error||'Open Tung Tung Client and reconnect Chrome.');return data;
 }catch(e){if(e.name==='AbortError'||e instanceof TypeError)throw Error('Open Tung Tung Client to connect Auto Next Game.');throw e;}finally{clearTimeout(timer);}
}
async function attachBoards(){
 const tabs=await chrome.tabs.query({url:['https://www.chess.com/*','https://chess.com/*']});
 await Promise.allSettled(tabs.filter(t=>Number.isInteger(t.id)).map(t=>chrome.scripting.executeScript({target:{tabId:t.id,frameIds:[0]},files:['engine.js','content.js']})));
}
async function handle(message,sender){
 if(!message||typeof message!=='object')throw Error('Invalid request.');
 if(message.type==='pair'){
  if(!ownPage(sender))throw Error('Use the extension connection page.');
  const key=message.key;if(typeof key!=='string'||!/^[a-f0-9]{64}$/.test(key))throw Error('Open Connect Chrome in Tung Tung Client.');
  const data=await request('/chess/settings',{},key);await chrome.storage.local.set({pairKey:key});await attachBoards();return data;
 }
 if(message.type==='status'){if(!ownPage(sender))throw Error('Extension page required.');return request('/chess/settings',{});}
 if(!chessPage(sender))throw Error('This connection is only available on Chess.com.');
 const raw=message.payload||{};
 if(typeof raw.clientId!=='string'||!/^[a-z0-9-]{8,80}$/i.test(raw.clientId))throw Error('Reload this Chess.com tab.');
 const clientId='chrome-'+sender.tab.id+'-'+raw.clientId;
 if(message.type==='stop')return request('/chess/stop',{clientId});
 if(message.type!=='poll'||!PHASES.has(raw.phase))throw Error('Invalid board state.');
 return request('/chess/poll',{clientId,phase:raw.phase,code:typeof raw.code==='string'?raw.code.slice(0,40):'',secondsLeft:Number.isInteger(raw.secondsLeft)?Math.min(15,Math.max(0,raw.secondsLeft)):0,claim:raw.claim===true});
}
chrome.runtime.onMessage.addListener((message,sender,respond)=>{handle(message,sender).then(data=>respond({ok:true,data}),error=>respond({ok:false,error:error.message}));return true;});
