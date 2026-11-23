import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const features=JSON.parse(fs.readFileSync('packaging/better-mace-features.json'));
const settings=[{key:'aimMode',label:'Aim timing',type:'enum',value:'ALWAYS',choices:['ALWAYS','WHILE_FALLING']},{key:'aimSpeed',label:'Aim speed',type:'integer',min:20,max:100,value:40},{key:'aimTargetMobs',label:'Target mobs',type:'boolean',value:false}];
const ctx=vm.createContext({connectionState:{betterMace:{ready:true,settings}},canMutate:()=>true,busy:new Set(),capturingKeybind:false,escapeHTML:s=>String(s),heading:s=>s,button:()=>'',moduleImage:()=>'',app:{querySelectorAll:()=>[]}});
vm.runInContext(fs.readFileSync('macos/ui/catalog.js','utf8')+fs.readFileSync('macos/ui/better-mace.js','utf8'),ctx);
assert.equal(vm.runInContext('BETTER_MACE_FEATURES.length',ctx),19);
for(const f of features){ctx.m={...f,known:true,enabled:false};const html=vm.runInContext('betterMacePage(m)',ctx);assert.match(html,/data-capture-keybind/);assert.match(html,/ORIGINAL MOD 5.0.0/);assert.equal(vm.runInContext(`MODULES.filter(m=>m.id===${JSON.stringify(f.id)}).length`,ctx),1);if(f.field.startsWith('@'))assert.match(html,/data-bms-run/);else assert.match(html,/data-toggle-module/);}
ctx.m={...features.find(f=>f.id==='bms_aim_assist'),known:true};const html=vm.runInContext('betterMacePage(m)',ctx);for(const key of ['aimMode','aimSpeed','aimTargetMobs'])assert.ok(html.includes(`data-bms-field="${key}"`));assert.match(html,/value="ALWAYS" selected/);assert.doesNotMatch(html,/<input type="checkbox"[^>]*checked/);
ctx.connectionState.betterMace.ready=false;assert.match(vm.runInContext('betterMacePage(m)',ctx),/data-toggle-module="bms_aim_assist"[^>]*disabled/);
console.log('PASS: 19 unique cards, real setting controls, action buttons, keybinds, disconnected locking');
const client=fs.readFileSync('macos/ui/client.js','utf8');
Object.assign(ctx,{page:'pvp',arsenalProvider:'better-mace',query:'',category:'All modules',scrollRow:0,connected:()=>false,icon:()=>'',moduleCard:m=>`<article data-id="${m.id}">${m.name}</article>`});
vm.runInContext('function modules(){return MODULES;}'+client.slice(client.indexOf('function moduleMatchesSearch('),client.indexOf('function liveFindings(')),ctx);
let group=vm.runInContext('modulePage()',ctx);assert.match(group,/BetterMaceSwap/);assert.match(group,/19 MODULES/);assert.match(group,/data-id="bms_attribute_swap"/);assert.doesNotMatch(group,/data-id="auto_totem"/);
ctx.arsenalProvider='all';ctx.query='BetterMaceSwap';ctx.scrollRow=0;group=vm.runInContext('modulePage()',ctx);assert.match(group,/19 MODULES/);assert.doesNotMatch(group,/data-id="auto_totem"/);
console.log('PASS: dedicated Arsenal group exposes 19 original functions; provider search finds the same 19 cards');

assert.equal(vm.runInContext("MODULES.find(m=>m.id==='aim_cursor').name",ctx),'Automatic Aim');assert.equal(vm.runInContext("BETTER_MACE_FEATURES.some(f=>f.id==='aim_cursor')",ctx),false);console.log('PASS: original Automatic Aim remains separate with its original controls');
