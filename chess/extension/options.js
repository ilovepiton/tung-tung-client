'use strict';
const status=document.querySelector('#status'),details=document.querySelector('#details'),button=document.querySelector('#connect');
let pair=new URLSearchParams(location.hash.slice(1)).get('pair');
if(pair)history.replaceState(null,'',location.pathname);
async function refresh(){
 button.disabled=true;
 try{
  const result=await chrome.runtime.sendMessage(pair?{type:'pair',key:pair}:{type:'status'});
  if(!result?.ok)throw Error(result?.error||'Connection unavailable.');pair=null;
  document.body.classList.add('connected');status.textContent='Connected to Tung Tung Client';
  const s=result.data;details.textContent=(s.enabled?'Auto Next Game ON':'Auto Next Game OFF')+' · '+s.minutes+(s.increment?' + '+s.increment:' min')+(s.connected?' · '+s.client:' · Open your Chess.com board');
 }catch(e){document.body.classList.remove('connected');status.textContent=e.message;details.textContent='Manage time and Auto Next Game in the Chess.com section of Tung Tung Client.';}
 finally{button.disabled=false;}
}
button.onclick=refresh;refresh();
