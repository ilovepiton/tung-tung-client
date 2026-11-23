package dev.amethyst.finder.integration;

import com.google.gson.JsonElement;
import dev.amethyst.finder.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** Controls the unmodified, pinned BetterMaceSwap JAR. Native configuration is authoritative. */
public final class BetterMaceModules {
 public record Feature(String id,String name,String field,String note,List<String> settings) {}
 public record Option(String key,String label,String kind,int min,int max,List<String> choices) {}
 private static Feature f(String id,String name,String field,String note,String...settings){return new Feature(id,name,field,note,List.of(settings));}
 public static final List<Feature> FEATURES=List.of(
  f("bms_attribute_swap","Attribute Swap","autoBreachSwapEnabled","BetterMaceSwap: swap from a sword/axe hit to the selected mace, then restore the original slot.","breachSwapBackTicks","breachSwapTriggerMode","maceMode","attributeSwapMode","combatTargetPlayers","combatTargetMobs"),
  f("bms_smart_switch","Smart Mace Switch","smartSwitchEnabled","BetterMaceSwap chooses Density while falling and Breach on the ground.","maceMode"),
  f("auto_shield_breaker","Auto Stun Slam","autoStunSlamEnabled","Right-click once with a mace at a blocking player: usable hotbar axe hit, then the native mace follow-up. Native left-click triggers also remain available. Holding right-click does not repeat.","stunSlamTriggerMode","stunSlamReturnSlot","stunSlamMode","noMaceAxeHitDelay","combatTargetPlayers","combatTargetMobs"),
  f("bms_lunge_swap","Lunge Swap","lungeSwapEnabled","BetterMaceSwap's spear/lunge swap and return.","lungeSwapDelay"),
  f("bms_pearl_catch","Pearl Catch","pearlCatchEnabled","Original pearl throw and wind-charge catch sequence.","pearlCatchDelayTicks","pearlCatchAngle","pearlCatchReturnAngle","pearlReturnAngle","pearlReturnMode"),
  f("bms_experimental_pearl","Experimental Pearl Timing","experimentalPearlCatch","Experimental flight-timing mode. Also enable Pearl Catch."),
  f("bms_aim_assist","BetterMace Aim Assist","aimAssistEnabled","BetterMaceSwap target tracking with its native Players/Mobs filters, distance, speed and target bone.","aimSpeed","aimIntensity","aimBone","aimMode","aimTargetPlayers","aimTargetMobs"),
  f("trigger_bot","TriggerBot","triggerBotEnabled","BetterMaceSwap's crosshair attack while falling.","aimIntensity","combatTargetPlayers","combatTargetMobs"),
  f("bms_auto_chestplate","Auto Chestplate","autoChestplateOnTrigger","Original close-target elytra/chestplate swap and return.","aimTargetPlayers","aimTargetMobs"),
  f("bms_wind_charge","Right-Click Wind Charge","windOnRightClickEnabled","Original weapon right-click wind-charge use from the hotbar."),
  f("bms_rocket_boost","Rocket Boost","rocketBoostEnabled","Allow the original right-click boost to use fireworks."),
  f("bms_auto_jump","Auto Jump Wind Charge","autoJumpWindCharge","Original jump and ground-directed wind charge."),
  f("bms_air_pots","Air Pots","airPotsEnabled","Original wind-charge sequence after a splash potion in mid-air."),
  f("bms_elytra_boost","Elytra Boost","elytraBoostEnabled","Enable the original elytra boost. Use Arm Elytra Boost to arm one boost.","elytraBoostRedirectAngle","elytraBoostRedirectTicks"),
  f("bms_elytra_arm","Arm Elytra Boost","@arm","Arm one native boost. First enable Elytra Boost."),
  f("bms_gear_up_1","Gear Up · Kit 1","@kit1","Record and equip native inventory kit 1.","gearUpClicksPerTick","gearUpDelayTicks"),
  f("bms_gear_up_2","Gear Up · Kit 2","@kit2","Record and equip native inventory kit 2.","gearUpClicksPerTick","gearUpDelayTicks"),
  f("bms_gear_up_3","Gear Up · Kit 3","@kit3","Record and equip native inventory kit 3.","gearUpClicksPerTick","gearUpDelayTicks"),
  f("bms_action_bar","Action Bar Messages","showActionBarMessages","Native BetterMaceSwap toggle and Gear Up messages.")
 );
 public static final Map<String,Option> OPTIONS;
 static {
  var m=new LinkedHashMap<String,Option>();
  number(m,"breachSwapBackTicks","Swap-back delay · ticks",1,5);number(m,"lungeSwapDelay","Lunge delay · ticks",0,3);number(m,"noMaceAxeHitDelay","Axe-only delay · ticks",0,3);
  number(m,"pearlCatchDelayTicks","Pearl catch delay · ticks",1,5);number(m,"pearlCatchAngle","Wind-charge angle",60,90);number(m,"pearlReturnAngle","Return view angle",-90,90);
  number(m,"aimSpeed","Aim speed",20,100);number(m,"aimIntensity","Target distance · blocks",1,25);number(m,"elytraBoostRedirectAngle","Elytra redirect angle",-90,50);number(m,"elytraBoostRedirectTicks","Redirect delay · ticks",1,10);
  number(m,"gearUpClicksPerTick","Gear Up clicks per tick",1,9);number(m,"gearUpDelayTicks","Gear Up click delay · ticks",0,20);
  choice(m,"breachSwapTriggerMode","Attribute swap trigger","WEAPONS","ALL");choice(m,"maceMode","Mace enchantment","DENSITY","BREACH");choice(m,"attributeSwapMode","Attribute swap mode","ALL","WEAPONS_ONLY");
  choice(m,"stunSlamTriggerMode","Stun Slam trigger","WEAPONS","ALL");choice(m,"stunSlamMode","Stun Slam mode","NORMAL","SAFE","UNSAFE");choice(m,"pearlReturnMode","Return slot","PREVIOUS","SWORD","AXE","ELYTRA");
  choice(m,"aimBone","Aim target bone","EYE","CHEST","LEGS");choice(m,"aimMode","Aim timing","WHILE_FALLING","ALWAYS");
  for(String key:List.of("combatTargetPlayers","combatTargetMobs","aimTargetPlayers","aimTargetMobs","stunSlamReturnSlot","pearlCatchReturnAngle"))m.put(key,new Option(key,switch(key){case "combatTargetPlayers","aimTargetPlayers"->"Target players";case "combatTargetMobs","aimTargetMobs"->"Target mobs";case "stunSlamReturnSlot"->"Restore return slot";default->"Restore view angle";},"boolean",0,0,List.of()));
  OPTIONS=Collections.unmodifiableMap(m);
 }
 private static void number(Map<String,Option> m,String key,String title,int min,int max){m.put(key,new Option(key,title,"integer",min,max,List.of()));}
 private static void choice(Map<String,Option> m,String key,String title,String...options){m.put(key,new Option(key,title,"enum",0,0,List.of(options)));}
 private static String error="BetterMaceSwap is not installed";
 private static boolean initialized,keysUnbound;
 private BetterMaceModules(){}
 public static Feature find(String id){return FEATURES.stream().filter(f->f.id.equals(id)).findFirst().orElse(null);}
 public static boolean handles(String id){return find(id)!=null;}
 public static boolean replaces(String id){return Set.of("auto_shield_breaker","trigger_bot").contains(id);}
 public static boolean action(String id){var f=find(id);return f!=null&&f.field.startsWith("@");}
 public static boolean available(){return FabricLoader.getInstance().getModContainer("bettermaceswap").map(m->m.getMetadata().getVersion().getFriendlyString().equals("5.0.0+mc26.3")).orElse(false);}
 private static Object config() throws ReflectiveOperationException {
  if(!available())throw new IllegalStateException("Install bundled BetterMaceSwap 5.0.0 for Minecraft 26.3 and restart Minecraft");
  return Class.forName("com.ritesh.config.ModConfig").getMethod("get").invoke(null);
 }
 private static void save(Object cfg)throws ReflectiveOperationException{cfg.getClass().getMethod("save").invoke(null);}
 public static void validateSchema(Object cfg)throws ReflectiveOperationException {
  for(Feature f:FEATURES)if(!f.field.startsWith("@")&&cfg.getClass().getField(f.field).getType()!=boolean.class)throw new IllegalStateException("Unsupported BetterMaceSwap boolean field");
  for(Option o:OPTIONS.values()){
   Class<?> type=cfg.getClass().getField(o.key).getType();
   if(o.kind.equals("integer")&&type!=int.class||o.kind.equals("boolean")&&type!=boolean.class||o.kind.equals("enum")&&!type.isEnum())throw new IllegalStateException("Unsupported BetterMaceSwap setting "+o.key);
   if(type.isEnum()&&!Arrays.stream(type.getEnumConstants()).map(v->((Enum<?>)v).name()).toList().equals(o.choices))throw new IllegalStateException("Unsupported BetterMaceSwap choices "+o.key);
  }
 }
 /** Closed field schema; shared by the desktop mutation and real-mod configuration tests. */
 public static void applySetting(Object cfg,String key,JsonElement value)throws ReflectiveOperationException {
  Option o=OPTIONS.get(key);if(o==null||value==null||!value.isJsonPrimitive())throw new IllegalArgumentException("Choose a known BetterMaceSwap setting");
  var v=value.getAsJsonPrimitive();Field field=cfg.getClass().getField(key);
  if(o.kind.equals("boolean")){if(!v.isBoolean())throw new IllegalArgumentException("Choose on or off");field.setBoolean(cfg,v.getAsBoolean());}
  else if(o.kind.equals("integer")){
   if(!v.isNumber())throw new IllegalArgumentException("Choose a whole number");double n=v.getAsDouble();if(!Double.isFinite(n)||n!=Math.rint(n)||n<o.min||n>o.max)throw new IllegalArgumentException("Choose a number from "+o.min+" to "+o.max);field.setInt(cfg,(int)n);
  }else{
   if(!v.isString()||!o.choices.contains(v.getAsString()))throw new IllegalArgumentException("Choose one of the available modes");
   Object item=Arrays.stream(field.getType().getEnumConstants()).filter(x->((Enum<?>)x).name().equals(v.getAsString())).findFirst().orElseThrow();field.set(cfg,item);
  }
 }
 public static void tick(Minecraft client){
  if(!available())return;
  try{
   Object cfg=config();if(!initialized){validateSchema(cfg);initialized=true;}
   if(!ChunkFinderClient.CONFIG.betterMaceManaged){
    for(Feature f:FEATURES)if(!f.field.startsWith("@"))cfg.getClass().getField(f.field).setBoolean(cfg,false);
    for(String id:List.of("auto_shield_breaker","trigger_bot"))ChunkFinderClient.CONFIG.setModuleEnabled(id,false);
    ChunkFinderClient.CONFIG.betterMaceManaged=true;save(cfg);ChunkFinderClient.CONFIG.save();
   }
   if(ChunkFinderClient.CONFIG.isModuleEnabled("aim_cursor")&&cfgBoolean(cfg,"aimAssistEnabled")){cfg.getClass().getField("aimAssistEnabled").setBoolean(cfg,false);save(cfg);}
   if(!keysUnbound){
    for(Field field:Class.forName("com.ritesh.keybind.KeyBindings").getFields())if(field.getType()==KeyMapping.class&&field.get(null) instanceof KeyMapping mapping)mapping.setKey(InputConstants.UNKNOWN);
    KeyMapping.resetMapping();keysUnbound=true;
   }
   if(ChunkFinderClient.CONFIG.streamerMode)disableAll(client);
   error="";
  }catch(ReflectiveOperationException|RuntimeException|LinkageError e){error="BetterMaceSwap not ready · "+e.getClass().getSimpleName();}
 }
 public static boolean enabled(String id){var f=find(id);if(f==null||f.field.startsWith("@"))return false;try{return cfgBoolean(config(),f.field);}catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}}
 private static boolean cfgBoolean(Object c,String key)throws ReflectiveOperationException{return c.getClass().getField(key).getBoolean(c);}
 public static void setEnabled(Minecraft client,String id,boolean value){
  Feature f=find(id);if(f==null)throw new IllegalArgumentException("Unknown BetterMaceSwap feature");
  try{
   Object cfg=config();validateSchema(cfg);
   if(f.field.startsWith("@")){if(value)run(client,id,"run");return;}
   if(value&&id.equals("bms_aim_assist")&&ChunkFinderClient.CONFIG.isModuleEnabled("aim_cursor"))throw new IllegalStateException("Turn Automatic Aim off before enabling the separate BetterMace aim helper");
   if(value&&(id.equals("bms_experimental_pearl")&&!cfgBoolean(cfg,"pearlCatchEnabled")))throw new IllegalArgumentException("Enable Pearl Catch first");
   if(value&&ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled())ChunkFinderClient.MACE.setEnabled(client,false);
   if(value&&ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled())ChunkFinderClient.BOT.setEnabled(client,false);
   cfg.getClass().getField(f.field).setBoolean(cfg,value);
   if(replaces(id))ChunkFinderClient.CONFIG.setModuleEnabled(id,false);
   save(cfg);if(!value)reset(client);
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot apply BetterMaceSwap feature",e);}
 }
 public static void changeSetting(String key,JsonElement value){try{Object cfg=config();applySetting(cfg,key,value);save(cfg);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot save BetterMaceSwap setting",e);}}
 public static void disableAll(Minecraft client){
  try{Object cfg=config();boolean changed=false;for(Feature f:FEATURES)if(!f.field.startsWith("@")&&!f.id.equals("bms_action_bar")&&cfgBoolean(cfg,f.field)){cfg.getClass().getField(f.field).setBoolean(cfg,false);changed=true;}if(changed){save(cfg);reset(client);}}
  catch(ReflectiveOperationException|RuntimeException|LinkageError e){error="Cannot pause BetterMaceSwap";}
 }
 private static void reset(Minecraft client)throws ReflectiveOperationException{if(client.player!=null)Class.forName("com.ritesh.feature.Coordinator").getMethod("fullReset",net.minecraft.client.player.LocalPlayer.class).invoke(null,client.player);}
 public static void run(Minecraft client,String id,String operation){
  if(client.player==null||client.level==null||client.gameMode==null||client.player.isSpectator()||!client.player.isAlive())throw new IllegalStateException("Join a world as a living player first");
  if(ChunkFinderClient.CONFIG.streamerMode)throw new IllegalStateException("Turn Streamer Mode off first");
  if(ChunkFinderClient.MODULES.totemSwapPending()||ChunkFinderClient.PVP.ownsInventory())throw new IllegalStateException("Wait for the current inventory operation");
  try{
   config();
   if(id.startsWith("bms_gear_up_")&&id.matches("bms_gear_up_[123]")){
    if(!Set.of("record","run").contains(operation))throw new IllegalArgumentException("Choose Record or Equip");int kit=Integer.parseInt(id.substring(id.length()-1));
    Class.forName("com.ritesh.feature.GearUp").getMethod(operation.equals("record")?"record":"start",Minecraft.class,int.class).invoke(null,client,kit);
   }else if(id.equals("bms_elytra_arm")&&operation.equals("run")){
    if(!enabled("bms_elytra_boost"))throw new IllegalStateException("Enable Elytra Boost first");Class.forName("com.ritesh.feature.Coordinator").getMethod("armElytraBoost").invoke(null);
   }else throw new IllegalArgumentException("Choose an existing BetterMaceSwap action");
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot run BetterMaceSwap action",e);}
 }
 /** Native combo from one physical use gesture; handler supplies the verified axe and original mace slot. */
 public static void manualStun(Minecraft client,net.minecraft.world.entity.player.Player target,int maceSlot,Runnable axeAttack){
  if(!enabled("auto_shield_breaker"))throw new IllegalStateException("Enable Auto Stun Slam first");
  try{
   if(!cfgBoolean(config(),"combatTargetPlayers"))throw new IllegalStateException("BetterMace target players is off");
   Class<?> slam=Class.forName("com.ritesh.feature.StunSlam");
   var guard=slam.getMethod("setInSlamCombo",boolean.class);
   guard.invoke(null,true);try{axeAttack.run();}finally{guard.invoke(null,false);}
   Object mode=config().getClass().getField("stunSlamMode").get(config());
   int delay=((Enum<?>)mode).name().equals("SAFE")?1:0;
   slam.getMethod("queueMaceSlam",net.minecraft.world.entity.Entity.class,int.class,int.class,int.class).invoke(null,target,maceSlot,maceSlot,delay);
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Native Stun Slam could not be queued",e);}
 }
 /** Upstream's substring search also matches pickaxes. Only real usable axes may break a shield. */
 public static int usableAxe(net.minecraft.world.entity.player.Player player){
  for(int slot=0;slot<9;slot++){
   var item=player.getInventory().getItem(slot);
   if(item.is(net.minecraft.tags.ItemTags.AXES)&&!item.isEmpty()&&(!item.isDamageableItem()||item.getDamageValue()<item.getMaxDamage()-1))return slot;
  }return -1;
 }
 public static boolean inventoryBusy(){try{return available()&&(boolean)Class.forName("com.ritesh.feature.GearUp").getMethod("isRunning").invoke(null);}catch(ReflectiveOperationException|RuntimeException e){return false;}}
 public static String status(String id){if(!available())return "Install BetterMaceSwap 5.0.0 for 26.3";if(!error.isEmpty())return error;if(action(id))return id.equals("bms_elytra_arm")?"Arm one boost · enable Elytra Boost first":"Record or equip this kit in settings";if(id.equals("auto_shield_breaker")&&enabled(id))return ChunkFinderClient.PVP.shieldBreakerStatus();return enabled(id)?"BetterMaceSwap 5.0.0 · ON":"BetterMaceSwap 5.0.0 · OFF";}
 public static Map<String,Object> state(){
  var result=new LinkedHashMap<String,Object>();result.put("installed",available());result.put("version","5.0.0+mc26.3");result.put("error",error);
  var features=new ArrayList<Map<String,Object>>();for(Feature f:FEATURES)features.add(Map.of("id",f.id,"name",f.name,"action",action(f.id),"settings",f.settings));result.put("features",features);
  var settings=new ArrayList<Map<String,Object>>();try{Object cfg=config();for(Option o:OPTIONS.values()){
   Object value=cfg.getClass().getField(o.key).get(cfg);if(value instanceof Enum<?> e)value=e.name();
   settings.add(Map.of("key",o.key,"label",o.label,"type",o.kind,"value",value,"min",o.min,"max",o.max,"choices",o.choices));
  }result.put("ready",true);}catch(ReflectiveOperationException|RuntimeException|LinkageError e){result.put("ready",false);}
  result.put("settings",settings);return result;
 }
}
