const BETTER_MACE_FEATURES = [
 {
  "id": "bms_attribute_swap",
  "name": "Attribute Swap",
  "field": "autoBreachSwapEnabled",
  "note": "BetterMaceSwap: swap from a sword/axe hit to the selected mace, then restore the original slot.",
  "settings": [
   "breachSwapBackTicks",
   "breachSwapTriggerMode",
   "maceMode",
   "attributeSwapMode",
   "combatTargetPlayers",
   "combatTargetMobs"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_smart_switch",
  "name": "Smart Mace Switch",
  "field": "smartSwitchEnabled",
  "note": "BetterMaceSwap chooses Density while falling and Breach on the ground.",
  "settings": [
   "maceMode"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "auto_shield_breaker",
  "name": "Auto Stun Slam",
  "field": "autoStunSlamEnabled",
  "note": "BetterMaceSwap's original shield combo: axe stun, mace follow-up and sword finish. Uses its own weapon/attack trigger settings; replaces the old Shield Breaker.",
  "settings": [
   "stunSlamTriggerMode",
   "stunSlamReturnSlot",
   "stunSlamMode",
   "noMaceAxeHitDelay",
   "combatTargetPlayers",
   "combatTargetMobs"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_lunge_swap",
  "name": "Lunge Swap",
  "field": "lungeSwapEnabled",
  "note": "BetterMaceSwap's spear/lunge swap and return.",
  "settings": [
   "lungeSwapDelay"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_pearl_catch",
  "name": "Pearl Catch",
  "field": "pearlCatchEnabled",
  "note": "Original pearl throw and wind-charge catch sequence.",
  "settings": [
   "pearlCatchDelayTicks",
   "pearlCatchAngle",
   "pearlCatchReturnAngle",
   "pearlReturnAngle",
   "pearlReturnMode"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_experimental_pearl",
  "name": "Experimental Pearl Timing",
  "field": "experimentalPearlCatch",
  "note": "Experimental flight-timing mode. Also enable Pearl Catch.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_aim_assist",
  "name": "BetterMace Aim Assist",
  "field": "aimAssistEnabled",
  "note": "BetterMaceSwap target tracking with its native Players/Mobs filters, distance, speed and target bone.",
  "settings": [
   "aimSpeed",
   "aimIntensity",
   "aimBone",
   "aimMode",
   "aimTargetPlayers",
   "aimTargetMobs"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "trigger_bot",
  "name": "TriggerBot",
  "field": "triggerBotEnabled",
  "note": "BetterMaceSwap's crosshair attack while falling.",
  "settings": [
   "aimIntensity",
   "combatTargetPlayers",
   "combatTargetMobs"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_auto_chestplate",
  "name": "Auto Chestplate",
  "field": "autoChestplateOnTrigger",
  "note": "Original close-target elytra/chestplate swap and return.",
  "settings": [
   "aimTargetPlayers",
   "aimTargetMobs"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_wind_charge",
  "name": "Right-Click Wind Charge",
  "field": "windOnRightClickEnabled",
  "note": "Original weapon right-click wind-charge use from the hotbar.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_rocket_boost",
  "name": "Rocket Boost",
  "field": "rocketBoostEnabled",
  "note": "Allow the original right-click boost to use fireworks.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_auto_jump",
  "name": "Auto Jump Wind Charge",
  "field": "autoJumpWindCharge",
  "note": "Original jump and ground-directed wind charge.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_air_pots",
  "name": "Air Pots",
  "field": "airPotsEnabled",
  "note": "Original wind-charge sequence after a splash potion in mid-air.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_elytra_boost",
  "name": "Elytra Boost",
  "field": "elytraBoostEnabled",
  "note": "Enable the original elytra boost. Use Arm Elytra Boost to arm one boost.",
  "settings": [
   "elytraBoostRedirectAngle",
   "elytraBoostRedirectTicks"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 },
 {
  "id": "bms_elytra_arm",
  "name": "Arm Elytra Boost",
  "field": "@arm",
  "note": "Arm one native boost. First enable Elytra Boost.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "action"
 },
 {
  "id": "bms_gear_up_1",
  "name": "Gear Up \u00b7 Kit 1",
  "field": "@kit1",
  "note": "Record and equip native inventory kit 1.",
  "settings": [
   "gearUpClicksPerTick",
   "gearUpDelayTicks"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "action"
 },
 {
  "id": "bms_gear_up_2",
  "name": "Gear Up \u00b7 Kit 2",
  "field": "@kit2",
  "note": "Record and equip native inventory kit 2.",
  "settings": [
   "gearUpClicksPerTick",
   "gearUpDelayTicks"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "action"
 },
 {
  "id": "bms_gear_up_3",
  "name": "Gear Up \u00b7 Kit 3",
  "field": "@kit3",
  "note": "Record and equip native inventory kit 3.",
  "settings": [
   "gearUpClicksPerTick",
   "gearUpDelayTicks"
  ],
  "category": "PvP",
  "available": true,
  "controlType": "action"
 },
 {
  "id": "bms_action_bar",
  "name": "Action Bar Messages",
  "field": "showActionBarMessages",
  "note": "Native BetterMaceSwap toggle and Gear Up messages.",
  "settings": [],
  "category": "PvP",
  "available": true,
  "controlType": "toggle"
 }
];
for(const f of BETTER_MACE_FEATURES){const i=MODULES.findIndex(m=>m.id===f.id);if(i>=0)MODULES.splice(i,1,f);else MODULES.push(f);}
function betterMacePage(m){
 const f=BETTER_MACE_FEATURES.find(x=>x.id===m.id);if(!f)return null;
 const state=connectionState.betterMace||{},locked=!canMutate()||!m.known||!m.available||!state.ready;
 const settings=(state.settings||[]).filter(s=>f.settings.includes(s.key));
 const controls=settings.map(s=>`<label class="bms-setting"><span>${escapeHTML(s.label)}</span>${s.type==='boolean'?`<input type="checkbox" data-bms-field="${s.key}" ${s.value?'checked':''} ${locked?'disabled':''}>`:s.type==='enum'?`<select data-bms-field="${s.key}" ${locked?'disabled':''}>${s.choices.map(c=>`<option value="${c}" ${s.value===c?'selected':''}>${escapeHTML(c.replaceAll('_',' '))}</option>`).join('')}</select>`:`<input type="number" data-bms-field="${s.key}" value="${Number(s.value)}" min="${s.min}" max="${s.max}" step="1" ${locked?'disabled':''}>`}</label>`).join('');
 const actions=f.controlType==='action'?`<div class="bms-actions">${f.field.startsWith('@kit')?`<button class="button" data-bms-run="${m.id}" data-bms-op="record" ${locked?'disabled':''}>RECORD KIT</button>`:''}<button class="button primary" data-bms-run="${m.id}" ${locked?'disabled':''}>${f.field.startsWith('@kit')?'EQUIP KIT':'ARM BOOST'}</button></div>`:`<button class="toggle large ${m.enabled?'on':''}" data-toggle-module="${m.id}" aria-label="Toggle ${escapeHTML(m.name)}" aria-pressed="${m.enabled}" ${locked?'disabled':''}></button>`;
 return heading(m.name+'.','Original BetterMaceSwap controls.')+`${button('← ARSENAL',651,96,216,'data-page="pvp"')}<section class="panel bms-panel"><div class="bms-heading">${moduleImage(m.id)}<span>BETTERMACESWAP · ORIGINAL MOD 5.0.0</span>${actions}</div><p>${escapeHTML(m.note)}</p><div class="bms-keybind"><span>KEYBIND</span><button class="button" data-capture-keybind ${locked?'disabled':''}>${capturingKeybind?'PRESS A KEY…':escapeHTML(m.keybind?m.keybindLabel:'ASSIGN A KEY')}</button><button class="button" data-clear-keybind ${locked||!m.keybind?'disabled':''}>CLEAR</button></div><small>${escapeHTML(m.keybindConflict||'Bindings work while Minecraft gameplay has focus. Escape cancels assignment.')}</small><div class="bms-options">${controls||(f.settings.length?'<p>Settings load from BetterMaceSwap when Minecraft connects.</p>':'<p>No additional settings for this feature.</p>')}</div><p class="bms-status">${escapeHTML(locked?(state.error||'Start the updated Tung Tung Fabric profile to control the mod.'):(m.runtimeStatus||'Ready'))}</p></section>`;
}
function bindBetterMace(){
 app.querySelectorAll('[data-bms-field]').forEach(el=>el.onchange=()=>execute('action',{action:'better_mace_setting',field:el.dataset.bmsField,value:el.type==='checkbox'?el.checked:el.type==='number'?Number(el.value):el.value},'bms-setting'));
 app.querySelectorAll('[data-bms-run]').forEach(el=>el.onclick=()=>execute('action',{action:'better_mace_run',module:el.dataset.bmsRun,op:el.dataset.bmsOp||'run'},'module:'+el.dataset.bmsRun));
}
