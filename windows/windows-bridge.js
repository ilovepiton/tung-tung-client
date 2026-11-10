/* WebView2 provides the same native message contract as the Mac companion. */
if(window.chrome?.webview){
 const nativeWebView=window.chrome.webview;
 window.webkit={messageHandlers:{native:{postMessage(message){nativeWebView.postMessage(message);}}}};
 nativeWebView.addEventListener('message',event=>{
 const message=event.data;
 if(message.kind==='reply')window.TungNative?.reply(message.reply);
 if(message.kind==='state')window.TungNative?.update(message.state);
 if(message.kind==='robloxState')window.TungRoblox?.update(message.state);
 });
}
