/* Shared local friendship and bot controls. The game owns and validates every saved change. */
let friendNameDraft='',friendsScrollTop=0,friendSuggestionIndex=-1;
const FRIEND_OPTIONS=[['aim','Automatic Aim','trigger_bot'],['attack','Melee attacks','auto_weapon'],['explosions','Blast protection','auto_crystal']];
function friendCandidates(query=friendNameDraft){
 const saved=Array.isArray(connectionState.friends)?connectionState.friends:[],online=connected()&&Array.isArray(connectionState.players)?connectionState.players:[];
 const records=new Map();
 for(const p of saved)if(typeof p.name==='string')records.set(p.name.toLowerCase(),{...p,saved:true});
 for(const p of online)if(!p.self&&typeof p.name==='string'){
  const key=p.name.toLowerCase(),old=records.get(key);records.set(key,{...old,...p,face:p.face||old?.face||'',saved:!!old?.saved});
 }
 const q=String(query).trim().toLowerCase();
 return [...records.values()].filter(p=>!q||p.name.toLowerCase().includes(q)).sort((a,b)=>Number(!a.name.toLowerCase().startsWith(q))-Number(!b.name.toLowerCase().startsWith(q))||Number(!!b.observable)-Number(!!a.observable)||a.name.localeCompare(b.name)).slice(0,q?6:4);
}
function friendSuggestionsMarkup(){
 const rows=friendCandidates(),locked=!canMutate()||busy.has('friends');
 return rows.length?rows.map((p,i)=>`<button type="button" role="option" id="friend-option-${i}" aria-selected="${i===friendSuggestionIndex}" class="friend-suggestion ${i===friendSuggestionIndex?'selected':''}" data-friend-select="${escapeHTML(p.name)}" ${locked?'disabled':''}>${playerFace(p,'friend-suggest-face')}<span><strong>${escapeHTML(p.name)}</strong><small>${p.saved?'ALREADY A FRIEND':p.observable?'NEARBY':p.online===false?'SAVED PLAYER':'ONLINE'}</small></span></button>`).join(''):`<span class="friend-no-match">${friendNameDraft.trim()?'No matching known players. You can add this name manually.':'Join Minecraft to see player suggestions.'}</span>`;
}
function friendInputFace(){
 const value=friendNameDraft.trim().toLowerCase(),p=friendCandidates().find(p=>p.name.toLowerCase()===value);
 return playerFace(p||{name:friendNameDraft||'Player'},'friend-input-face');
}
function refreshFriendSuggestions(){
 if(friendSuggestionIndex>=friendCandidates().length)friendSuggestionIndex=-1;
 const list=app.querySelector('#friend-suggestions');if(list)list.innerHTML=friendSuggestionsMarkup();
 const preview=app.querySelector('#friend-input-face');if(preview)preview.innerHTML=friendInputFace();
 const input=app.querySelector('#friend-name');if(input){input.setAttribute('aria-expanded',String(friendCandidates().length>0));if(friendSuggestionIndex>=0)input.setAttribute('aria-activedescendant','friend-option-'+friendSuggestionIndex);else input.removeAttribute('aria-activedescendant');}
 app.querySelectorAll('[data-friend-face]').forEach(el=>{
  const row=(connectionState.friends||[]).find(r=>r.name.toLowerCase()===el.dataset.friendFace.toLowerCase());
  if(row){const markup=playerFace({...row,face:row.face||((connectionState.players||[]).find(p=>p.name.toLowerCase()===row.name.toLowerCase())?.face)||''},'friend-avatar');if(el.innerHTML!==markup)el.innerHTML=markup;}
 });
 bindFriendSuggestions();
}
function bindFriendSuggestions(){
 app.querySelectorAll('[data-friend-select]').forEach(el=>el.onclick=()=>{
  friendNameDraft=el.dataset.friendSelect;friendSuggestionIndex=-1;
  const input=app.querySelector('#friend-name');if(input){input.value=friendNameDraft;input.focus();input.setSelectionRange(input.value.length,input.value.length);}refreshFriendSuggestions();
 });
}
function friendsPage(){
 const rows=connectionState.friends||[],locked=!canMutate()||busy.has('friends'),players=connectionState.players||[];
 return heading('Friends. On your side.','Save a name once. Combat Bot always leaves your friends alone.')+button('ARSENAL',728,82,139,'data-page="pvp"')+`<section class="panel friend-add"><div class="friend-add-heading">${discoveryImage('items/player_detection','mini-png','Compass')}<strong>ADD A FRIEND</strong><span>${rows.length} / 100</span></div><form id="friend-add-form"><label class="sr-only" for="friend-name">Minecraft username</label><span id="friend-input-face">${friendInputFace()}</span><input id="friend-name" role="combobox" aria-autocomplete="list" aria-controls="friend-suggestions" aria-expanded="${friendCandidates().length>0}" maxlength="16" autocomplete="off" spellcheck="false" placeholder="Minecraft username" value="${escapeHTML(friendNameDraft)}" ${locked?'disabled':''}><button type="submit" class="button primary" ${locked?'disabled':''}>ADD FRIEND</button></form><p>Names ignore capitals. You can add someone while they are offline.</p><div id="friend-suggestions" class="friend-suggestions" role="listbox" aria-label="Player name suggestions">${friendSuggestionsMarkup()}</div></section><div class="friends-list" tabindex="0" aria-label="Saved friends">${rows.length?rows.map(row=>`<article class="panel friend-card"><span data-friend-face="${escapeHTML(row.name)}">${playerFace({...row,face:row.face||(players.find(p=>p.name.toLowerCase()===row.name.toLowerCase())?.face)||''},'friend-avatar')}</span><div class="friend-name"><strong>${escapeHTML(row.name)}</strong><small>${row.observable?'NEARBY':row.online?'ONLINE · POSITION UNKNOWN':'OFFLINE'} · Bot always excludes</small></div><button class="friend-remove" data-friend-remove="${escapeHTML(row.name)}" aria-label="Remove ${escapeHTML(row.name)}" ${locked?'disabled':''}>×</button><div class="friend-protections">${FRIEND_OPTIONS.map(([key,label,img])=>`<div>${discoveryImage('items/'+img,'mini-png',label)}<span>${label}</span><button class="toggle ${(row.rules?.[key]!==false)?'on':''}" data-friend-rule="${key}" data-friend-name="${escapeHTML(row.name)}" aria-label="${escapeHTML(label+' protection for '+row.name)}" aria-pressed="${row.rules?.[key]!==false}" ${locked?'disabled':''}></button></div>`).join('')}</div>${row.observable?`<button class="friend-find" data-pin-player="${escapeHTML(row.id)}" ${locked?'disabled':''}>SAVE POSITION ${icon('scan')}</button>`:''}</article>`).join(''):`<section class="panel friends-empty">${discoveryImage('items/player_detection','friends-empty-png','Compass')}<h2>Your team starts here.</h2><p>Add a Minecraft username above.<br>Friends stay saved between sessions.</p></section>`}</div><div class="page-note">Blast protection skips explosions that could hit this friend. Saved positions stay fixed.</div>`;
}
function bindFriends(){
 const input=app.querySelector('#friend-name');if(input){
  input.oninput=()=>{friendNameDraft=input.value;friendSuggestionIndex=-1;refreshFriendSuggestions();};
  input.onkeydown=e=>{
   const candidates=friendCandidates();
   if(['ArrowDown','ArrowUp'].includes(e.key)&&candidates.length){e.preventDefault();friendSuggestionIndex=friendSuggestionIndex<0?(e.key==='ArrowDown'?0:candidates.length-1):(friendSuggestionIndex+(e.key==='ArrowDown'?1:-1)+candidates.length)%candidates.length;refreshFriendSuggestions();app.querySelector('#friend-option-'+friendSuggestionIndex)?.scrollIntoView({block:'nearest'});}
   else if(e.key==='Enter'&&friendSuggestionIndex>=0&&candidates[friendSuggestionIndex]){e.preventDefault();friendNameDraft=candidates[friendSuggestionIndex].name;friendSuggestionIndex=-1;input.value=friendNameDraft;refreshFriendSuggestions();}
   else if(e.key==='Escape'){friendSuggestionIndex=-1;refreshFriendSuggestions();}
  };
 }
 bindFriendSuggestions();
 const change=async(action,name,extra={})=>{if(!/^[A-Za-z0-9_]{1,16}$/.test(name)&&action!=='friend_remove'){notify('Use a Minecraft username: letters, numbers or underscores.');return;}try{await execute('action',{action,name,...extra},'friends');if(action==='friend_add'){friendNameDraft='';friendSuggestionIndex=-1;}render();}catch(e){notify(e.message);}};
 const form=app.querySelector('#friend-add-form');if(form)form.onsubmit=e=>{e.preventDefault();change('friend_add',friendNameDraft.trim());};
 app.querySelectorAll('[data-friend-quick]').forEach(el=>el.onclick=()=>change('friend_add',el.dataset.friendQuick));
 app.querySelectorAll('[data-friend-remove]').forEach(el=>el.onclick=()=>change('friend_remove',el.dataset.friendRemove));
 app.querySelectorAll('[data-friend-rule]').forEach(el=>el.onclick=()=>change('friend_rule',el.dataset.friendName,{rule:el.dataset.friendRule,enabled:el.getAttribute('aria-pressed')!=='true'}));
}
function botOptions(){
 const values=[['botMovement','Move toward the opponent'],['botStrafe','Strafe and retreat'],['botPearls','Pursue with ender pearls'],['botBuild','Place obsidian cover / bases'],['botCrystals','Use end crystals'],['botAnchors','Use respawn anchors'],['botExtinguish','Clear fire after a defeat']];
 return `<div class="bot-options"><div class="bot-totem">${discoveryImage('items/auto_totem','mini-png','Totem')}<span>Auto Totem is included while the bot is armed.<small>Your own Auto Totem switch stays saved.</small></span></div><label class="bot-range">Target range <strong data-value-label="botRange">${settingsValue('botRange')??48} blocks</strong><input type="range" min="8" max="96" step="1" data-setting-range="botRange" data-unit=" blocks" value="${settingsValue('botRange')??48}" ${!canSetting('botRange')?'disabled':''}></label><div class="bot-damage-controls"><label class="bot-range">Minimum target damage <strong data-value-label="botMinDamage">${settingsValue('botMinDamage')??1.5} HP</strong><input type="range" min="0.5" max="12" step="0.5" data-setting-range="botMinDamage" data-unit=" HP" value="${settingsValue('botMinDamage')??1.5}" ${!canSetting('botMinDamage')?'disabled':''}></label><label class="bot-range">Maximum self damage <strong data-value-label="botMaxSelfDamage">${settingsValue('botMaxSelfDamage')??10} HP</strong><input type="range" min="1" max="16" step="0.5" data-setting-range="botMaxSelfDamage" data-unit=" HP" value="${settingsValue('botMaxSelfDamage')??10}" ${!canSetting('botMaxSelfDamage')?'disabled':''}></label></div>${values.map(([key,label])=>`<div class="bot-option"><span>${label}</span><button class="toggle ${settingsValue(key)===true?'on':''}" data-setting-toggle="${key}" aria-label="${label}" aria-pressed="${settingsValue(key)===true}" ${!canSetting(key)?'disabled':''}></button></div>`).join('')}<button class="button bot-friends" data-page="friends">${discoveryImage('items/player_detection','mini-png','Compass')}FRIENDS</button><p>Join a world, press P, then return to Minecraft. Stops after one confirmed defeat. Pearls and attacks use normal server rules.</p></div>`;
}
