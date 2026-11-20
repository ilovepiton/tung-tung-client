import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const client=fs.readFileSync('macos/ui/client.js','utf8');
const snippet=client.slice(client.indexOf('function moduleMatchesSearch('),client.indexOf('function liveFindings('));
const ctx=vm.createContext({console,Math,page:'pvp',query:'',category:'All modules',scrollRow:0,connectionState:{},canMutate:()=>false,settingsValue:()=>0,canSetting:()=>false,connected:()=>false,heading:()=>'',button:()=>'',icon:()=>'',escapeHTML:s=>s,discoveryImage:()=>'',moduleCard:m=>`<article data-module="${m.id}">${m.name}</article>`});
vm.runInContext(fs.readFileSync('macos/ui/catalog.js','utf8')+'\nfunction modules(){return MODULES.map(m=>({...m,known:false,enabled:false}));}\n'+snippet+fs.readFileSync('macos/ui/mace.js','utf8'),ctx);
for(const q of ['auto shield brake','Auto Shield Breaker','shieldbreak','shield breaker','авто щит']){
 ctx.query=q;ctx.scrollRow=0;const html=vm.runInContext('modulePage()',ctx);assert.match(html,/data-module="auto_shield_breaker"/);assert.doesNotMatch(html,/data-module="auto_totem"/);
}
ctx.query='';ctx.scrollRow=0;const html=vm.runInContext('modulePage()',ctx);assert.ok(html.indexOf('data-module="auto_shield_breaker"')<html.indexOf('data-module="auto_totem"'));
const mace=vm.runInContext('macePage()',ctx);assert.match(mace,/data-toggle-module="auto_shield_breaker"/);assert.match(mace,/data-module-settings="auto_shield_breaker"/);
assert.match(mace,/aria-label="Toggle Auto Shield Breaker"[^>]*disabled/);
console.log('PASS: 5 search variants; first Arsenal card; Mace PvP toggle and settings; offline toggle disabled');
