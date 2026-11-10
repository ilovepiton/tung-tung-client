'use strict';
async function send(type,payload){const result=await chrome.runtime.sendMessage({type,payload});if(!result?.ok)throw Error(result?.error||'Connection paused.');return result.data;}
TungChess.boot({poll:payload=>send('poll',payload),stop:clientId=>send('stop',{clientId})});
