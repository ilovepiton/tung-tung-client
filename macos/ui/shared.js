function icon(name, cls = '') {
 const shapes = {
 refresh:'<path d="M20 7A9 9 0 1 0 21 15 M20 2v6h-6"/>',
 play:'<path d="m8 4 12 8-12 8z"/>',
 gear:'<path d="m9 2-.7 3-2 .9-2.7-.9-2 3.5 2.1 2v2.9l-2.1 2 2 3.5 2.7-.9 2 .9.7 3h4l.7-3 2-.9 2.7.9 2-3.5-2.1-2v-2.9l2.1-2-2-3.5-2.7.9-2-.9-.7-3z"/><circle cx="11" cy="12" r="3"/>',
 book:'<path d="M12 5C9 2 5 2 2 3v17c4-1 7-1 10 2 3-3 6-3 10-2V3c-3-1-7-1-10 2z M12 5v17"/>',
 cube:'<path d="m12 2 9 5v10l-9 5-9-5V7z M3 7l9 5 9-5 M12 12v10 M7.5 4.5l9 5"/>',
 home:'<path d="m3 10 9-8 9 8 M5 8v13h14V8 M9 21v-8h6v8"/>',
 scan:'<path d="M8 3H3v5 M16 3h5v5 M3 16v5h5 M21 16v5h-5"/><circle cx="12" cy="12" r="5"/><path d="m16 16 3 3"/>',
 shield:'<path d="m12 2 8 3v6c0 5-5 9-8 11-3-2-8-6-8-11V5z M12 7v10 M8 12h8"/>',
 sliders:'<path d="M4 3v18 M12 3v18 M20 3v18 M1 8h6 M9 16h6 M17 6h6"/>',
 search:'<circle cx="10" cy="10" r="7"/><path d="m15.5 15.5 6 6"/>',
 eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
 camera:'<path d="M3 6h5l2-3h4l2 3h5v15H3z"/><circle cx="12" cy="13" r="5"/>',
 sword:'<path d="m15 3 6-1-1 6-11 11-4-4z M3 13l8 8 M2 22l5-5"/>',
 crosshair:'<circle cx="12" cy="12" r="7"/><path d="M12 1v7 M12 16v7 M1 12h7 M16 12h7"/>',
 crystal:'<path d="m12 2 8 8-8 12-8-12z M4 10h16 M12 2l-3 8 3 12 3-12z"/>',
 heart:'<path d="M12 21 3.5 12.5C-2 7 5 1 9.5 5.5L12 8l2.5-2.5C19 1 26 7 20.5 12.5z"/>',
 bolt:'<path d="M14 1 4 14h7l-1 9 10-13h-7z"/>',
 anchor:'<circle cx="12" cy="4" r="2"/><path d="M12 6v16 M6 10h12 M2 15c0 5 4 7 10 7s10-2 10-7 M2 15l-1 4 M22 15l1 4"/>'
 };
 return `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${shapes[name]||shapes.cube}</svg>`;
}
function moduleImage(id,cls='module-icon'){
 const known=MODULES.find(m=>m.id===id);
 const paths={auto_sell:'items/auto_sell',auto_logout:'items/auto_logout',mace_bot:'mace/mace',auto_vault:'vault/ominous_trial_key'};
 return `<img class="${cls}" src="assets/${paths[id]||'items/'+(known?id:'chunk_finder')}.png" alt="${escapeHTML(known?.name||'Minecraft item')}" draggable="false">`;
}
function moduleIcon(id) {
 if(id==='auto_crystal'||id==='amethyst_esp')return 'crystal';if(id==='auto_anchor')return 'anchor';if(id==='trigger_bot')return 'crosshair';
 if(id==='auto_gapple'||id==='auto_totem')return 'heart';if(id==='auto_weapon'||id==='killaura')return 'sword';
 if(id.includes('camera')||id==='freecam')return 'camera';if(id.includes('esp')||id.includes('detection')||id==='freelook')return 'eye';if(id==='flight')return 'bolt';return 'cube';
}
function asset(name, cls, alt) {
 const bounds = (typeof ASSET_BOUNDS !== 'undefined' && ASSET_BOUNDS[name]) || null;
 if(bounds) return `<svg class="${cls}" viewBox="${bounds.x} ${bounds.y} ${bounds.w} ${bounds.h}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="${escapeHTML(alt)}"><image href="assets/${name}" width="${bounds.width}" height="${bounds.height}"/></svg>`;
 return `<img class="${cls}" src="assets/${name}" alt="${escapeHTML(alt)}">`;
}
function notify(text) { clearTimeout(toastTimer);$('#toast').textContent=text;$('#toast').style.opacity='1';toastTimer=setTimeout(()=>$('#toast').style.opacity='0',2200); }
function navImage(id){if(id==='find-player')return discoveryImage('find-player-v2','nav-png','Player finder compass');const items={home:['nav_bed','Bed'], 'texture-packs':['finder_enchanting','Enchantment table'], 'world-tools':['freecam','Elytra'], findings:['finder_chest','Chest'], friends:['player_detection','Friends compass'], 'find-player':['player_detection','Compass'], pvp:['auto_crystal','End crystal'], microphone:['nav_horn','Goat horn'],settings:['nav_redstone','Redstone']};const item=items[id];return item?discoveryImage('items/'+item[0],'nav-png',item[1]):icon('cube');}
function nav(id,label,ic,y) { return `<button class="nav ${page===id||(page==='module-settings'&&moduleReturnPage===id)||(id==='pvp'&&['pvp-settings','assist-settings','player-settings'].includes(page))||(id==='texture-packs'&&['keyboard-customize','mouse-customize'].includes(page))||(id==='world-tools'&&['trial-chambers','finder-settings','camera-settings'].includes(page))?'active':''}" style="top:${y}px" data-page="${id}">${navImage(id)}<span>${label}</span></button>`; }
function button(label,x,y,w,action,primary=false,disabled=false){return `<button class="button ${primary?'primary':''}" style="position:absolute;left:${x}px;top:${y}px;width:${w}px" ${disabled?'disabled':''} ${action}>${label}</button>`;}
function heading(title,subtitle,y=119){return `<h1 class="title">${title}</h1><div class="subtitle" style="top:${y}px;${y===111?'line-height:18px':''}">${subtitle}</div>`;}
function guideFor(){const available=MODULES.filter(m=>m.available&&GUIDES[m.id]);const module=available.find(m=>m.id===guideId)||available[0];return module?{...module,...GUIDES[module.id]}:null;}
function tutorials(){
 const available=MODULES.filter(m=>m.available&&GUIDES[m.id]);guideScroll=Math.max(0,Math.min(guideScroll,Math.max(0,available.length-10)));const selectedGuide=guideFor();
 let result=heading('Learn the tools.','Concise guides for every implemented module.');
 result+=`<section class="panel tutorial-list" style="left:256px;top:165px;width:206px;height:540px"><span class="field-title" style="left:16px">IMPLEMENTED GUIDES · ${available.length}</span></section>`;
 result+=available.slice(guideScroll,guideScroll+10).map((m,i)=>`<button class="guide-row ${selectedGuide&&selectedGuide.id===m.id?'selected':''}" style="top:${214+i*43}px" data-guide="${m.id}">${moduleImage(m.id,'guide-row-icon')}<span>${escapeHTML(m.name)}</span></button>`).join('');
 result+=button('↑ PREVIOUS',277,666,76,'data-guide-scroll="-1"')+button('NEXT ↓',370,666,76,'data-guide-scroll="1"');
 if(!selectedGuide)return result;
 result+=`<section class="panel tutorial-reader" style="left:480px;top:165px;width:387px;height:540px"><span class="guide-eyebrow">MODULE GUIDE</span>${moduleImage(selectedGuide.id,'guide-title-icon')}<div class="guide-title">${escapeHTML(selectedGuide.name)}</div><div class="guide-tag">${escapeHTML(technicalStatus(selectedGuide))}</div><div class="guide-rule"></div><div class="guide-section-title" style="top:141px">HOW IT WORKS</div><div class="guide-body guide-summary" style="top:170px;height:97px">${escapeHTML(selectedGuide.how)}</div><div class="guide-section-title" style="top:284px">QUICK START</div><div class="guide-body" style="top:312px;height:87px">${escapeHTML(selectedGuide.start)}</div><div class="guide-section-title" style="top:416px">WHAT TO KNOW</div><div class="guide-body" style="top:442px;height:84px">${escapeHTML(selectedGuide.limit)}</div></section>`;
 return result;
}
