(function(){
 'use strict';
 const pending=new Map();let sequence=0;
 window.TungChessNativeReply=function(reply){const request=pending.get(reply.id);if(!request)return;pending.delete(reply.id);clearTimeout(request.timer);reply.ok?request.resolve(reply.data):request.reject(Error(reply.error||'Connection paused.'));};
 function send(action,payload){return new Promise((resolve,reject)=>{const id='chess-'+(++sequence),timer=setTimeout(()=>{pending.delete(id);reject(Error('Client connection paused.'));},2500);pending.set(id,{resolve,reject,timer});try{window.webkit.messageHandlers.tungChess.postMessage({id,action,payload});}catch(e){clearTimeout(timer);pending.delete(id);reject(e);}});}
 TungChess.boot({poll:payload=>send('poll',payload),stop:clientId=>send('stop',{clientId})});
})();
