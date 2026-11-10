package dev.amethyst.finder.modules.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.*;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import static dev.amethyst.finder.modules.pvp.MaceLoadout.Kind;

/** Session-scoped mace combat coordinator. Only real inputs and vanilla item interactions. */
public final class MaceBot {
 public static final String ID="mace_bot";
 private final FinderConfig config;private final BotNavigation navigation=new BotNavigation();
 private LocalPlayer self;private ClientLevel level;private Player target;
 private long clientTick,handled=-1;private int clock,nextAttack,chargeAfter,pearlAfter,swapAfter,recoverUntil,shieldUntil,requestAt=-100,damageAt=-100,damageCause=-1,enemySwingAt=-100,swingsSeen,chargeRequests,smashRequests,burstChains,pearlRequests;
 private int originalSlot=-1,ownedSlot=-1,lastTargetSwing=-1,launchUntil,burstLevel;private double lastHealth,enemyPeak;
 private boolean ready,emergency,incoming,ownedShield,mouseUseKnown,mouseUseDown,smashWasFalling;
 private String useBinding,status="Off · choose a keybind in settings",phase="off";private Vec3 previousEnemy,velocity=Vec3.ZERO,goal,pearlOrigin,enemyPearl;
 private int pearlUntil,enemyPearlUntil;private Map<String,Integer> flightEnchantments;private Move move;private List<MaceLoadout.Weapon> kit=List.of();
 private record Move(int source,int target,ItemStack beforeSource,ItemStack beforeTarget,int expires){}
 public MaceBot(FinderConfig config){this.config=config;config.setModuleEnabled(ID,false);}
 public boolean enabled(){return config.isModuleEnabled(ID);}public boolean handledThisTick(){return handled==clientTick;}public void beginClientTick(){clientTick++;}
 public void afterMovement(Minecraft c){if(enabled()&&!handledThisTick()){handled=clientTick;tick(c);}}
 public boolean ownsInventory(){return enabled()&&move!=null&&!emergency;}
 public boolean reservesInventorySlot(int slot){return enabled()&&move!=null&&(move.source==slot||move.target==slot);}
 public boolean autoTotem(){return enabled()&&self!=null&&self.isAlive()&&emergency;}
 public String status(){return status;}
 public void setEnabled(Minecraft c,boolean on){
  if(on&&(c.player==null||c.level==null||c.getConnection()==null||!c.player.isAlive()||c.player.isSpectator()))throw new IllegalArgumentException("Join a world as a living player before arming Mace PvP");
  releaseShield(c,true);restoreSlot();move=null;target=null;ready=false;navigation.reset();velocity=Vec3.ZERO;previousEnemy=null;goal=pearlOrigin=enemyPearl=null;
  self=on?c.player:null;level=on?c.level:null;clock=nextAttack=chargeAfter=pearlAfter=swapAfter=recoverUntil=launchUntil=shieldUntil=pearlUntil=enemyPearlUntil=0;originalSlot=ownedSlot=-1;requestAt=damageAt=enemySwingAt=-100;damageCause=lastTargetSwing=-1;burstLevel=0;smashWasFalling=false;emergency=incoming=false;
  flightEnchantments=null;lastHealth=on?c.player.getHealth()+c.player.getAbsorptionAmount():0;enemyPeak=0;config.setModuleEnabled(ID,on);phase=on?"search":"off";status=on?"Armed · mace / axe / sword / shield · no Elytra":"Stopped";
  if(on&&ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled())ChunkFinderClient.BOT.setEnabled(c,false);
  if(on&&ChunkFinderClient.PVP!=null)ChunkFinderClient.PVP.clearBotPlans();
 }
 private void stop(Minecraft c,String why){setEnabled(c,false);status=why;config.save();}
 public void entityEvent(Minecraft c,Entity e,byte event){if(!enabled())return;if(e==self&&event==35){recoverUntil=clock+80;emergency=true;releaseShield(c,true);move=null;phase="survival";status="Totem activated · refill, retreat and recover";}if(e==target&&event==3)stop(c,"Target defeated · Mace PvP stopped");}
 public void damage(int entityId,int cause){if(enabled()&&target!=null&&target.getId()==entityId){damageAt=clock;damageCause=cause;}}
 public void mouseButton(Minecraft c,int button,int action){if((action==0||action==1)&&c.options.keyUse.matches(InputConstants.Type.MOUSE.getOrCreate(button))){mouseUseKnown=true;mouseUseDown=action==1;useBinding=c.options.keyUse.saveString();}}
 public void keyboard(Minecraft c,int code,int action){if((action==0||action==1)&&c.options.keyUse.matches(InputConstants.Type.KEYBOARD.getOrCreate(code))){mouseUseKnown=true;mouseUseDown=action==1;useBinding=c.options.keyUse.saveString();}}
 private boolean manualUse(Minecraft c){return mouseUseKnown&&Objects.equals(useBinding,c.options.keyUse.saveString())?mouseUseDown:!ownedShield&&c.options.keyUse.isDown();}
 private boolean eligible(Player p){return p!=self&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isCreative()&&p.isAttackable()&&!Friends.excludes(config,self,p,"bot")&&withinTrackingRange(p);}
 private boolean withinTrackingRange(Player p){
  double range=config.maceRange*config.maceRange;
  // A confirmed wind launch can briefly exceed the initial 3-D search radius.
  // Keep this opponent during the arc, but never extend ground acquisition or reach.
  if(p==target&&!self.onGround()&&Math.abs(p.getY()-self.getY())<=64)
   return p.position().subtract(self.position()).multiply(1,0,1).lengthSqr()<=range;
  return self.distanceToSqr(p)<=range;
 }
 private boolean visible(Player p){return self.hasLineOfSight(p);}
 private void pickTarget(Minecraft c){
  if(target!=null&&!eligible(target)){target=null;previousEnemy=null;velocity=Vec3.ZERO;navigation.reset();}
  if(target==null){double best=Double.MAX_VALUE;for(Entity e:level.entitiesForRendering())if(e instanceof Player p&&eligible(p)&&visible(p)){double score=self.distanceToSqr(p);if(score<best){target=p;enemyPeak=p.getY();lastTargetSwing=-1;best=score;}}}
 }
 private ItemStack at(int slot){return slot==40?self.getOffhandItem():self.getInventory().getItem(slot);}
 private boolean inventorySafe(){return self.containerMenu==self.inventoryMenu&&self.inventoryMenu.getCarried().isEmpty();}
 private void select(int slot){if(originalSlot<0)originalSlot=self.getInventory().getSelectedSlot();self.getInventory().setSelectedSlot(slot);ownedSlot=slot;}
 private void restoreSlot(){if(self!=null&&originalSlot>=0&&ownedSlot>=0&&self.getInventory().getSelectedSlot()==ownedSlot)self.getInventory().setSelectedSlot(originalSlot);ownedSlot=originalSlot=-1;}
 private boolean settleMove(){
  if(move==null)return true;var m=move;
  if(ItemStack.matches(at(m.target),m.beforeSource)&&ItemStack.matches(at(m.source),m.beforeTarget)){move=null;swapAfter=clock+2;return true;}
  if(clock>=m.expires){move=null;swapAfter=clock+20;status="Inventory move not confirmed · keeping safe combat";return false;}
  status="Waiting for server inventory confirmation";return false;
 }
 private boolean swap(Minecraft c,int source,int targetSlot){
  if(!inventorySafe()||clock<swapAfter||move!=null)return false;
  int clicked=source==40?targetSlot+36:source<9?source+36:source,button=source==40?40:targetSlot;
  move=new Move(source,targetSlot,at(source).copy(),at(targetSlot).copy(),clock+30);
  self.connection.send(new ServerboundContainerClickPacket(self.inventoryMenu.containerId,self.inventoryMenu.getStateId(),(short)clicked,(byte)button,ContainerInput.SWAP,new Int2ObjectOpenHashMap<>(),HashedStack.create(self.inventoryMenu.getCarried(),self.connection.decoratedHashOpsGenenerator())));
  return false;
 }
 private boolean equip(Minecraft c,MaceLoadout.Weapon weapon,boolean offhand){
  if(weapon==null||!weapon.usable(config.maceDurabilityReserve)||move!=null||clock<swapAfter)return false;
  if(offhand){if(weapon.slot()==40)return true;return swap(c,weapon.slot(),40);}
  if(weapon.slot()<9){select(weapon.slot());return true;}
  int targetSlot=self.getInventory().getSelectedSlot();for(int i=0;i<9;i++)if(at(i).isEmpty()){targetSlot=i;break;}select(targetSlot);return swap(c,weapon.slot(),targetSlot);
 }
 private MaceLoadout.Weapon item(Kind kind){return kit.stream().filter(w->w.kind()==kind&&w.usable(config.maceDurabilityReserve)).findFirst().orElse(null);}
 private MaceLoadout.Weapon weapon(Kind kind){if(kind==Kind.MACE&&flightEnchantments!=null&&!self.onGround()){for(var w:kit)if(w.kind()==Kind.MACE&&w.enchants().equals(flightEnchantments)&&w.usable(config.maceDurabilityReserve)&&w.validMace())return w;}return MaceLoadout.best(kit,kind,config.maceDurabilityReserve,kind==Kind.MACE&&self.onGround()?6:self.fallDistance,target==null?0:target.getArmorValue(),multiple(),incoming||emergency,target!=null&&target.isOnFire());}
 private boolean allyNear(Vec3 point,double radius){for(Entity e:level.entitiesForRendering())if(e instanceof Player p&&p!=self&&p.isAlive()&&Friends.excludes(config,self,p,"bot")&&p.position().distanceToSqr(point)<radius*radius)return true;return false;}
 private boolean multiple(){if(target==null)return false;int count=0;for(Entity e:level.entitiesForRendering())if(e instanceof Player p&&eligible(p)&&p.distanceToSqr(target)<2.25)count++;return count>1&&!allyNear(target.position(),2.5);}
 private Vec3 predict(int ticks){return target.position().add(velocity.scale(Math.clamp(ticks,0,12)));}
 private void observe(Minecraft c){
  Vec3 now=target.position();if(previousEnemy!=null){Vec3 delta=now.subtract(previousEnemy);if(delta.lengthSqr()<9)velocity=velocity.scale(.4).add(delta.scale(.6));else{velocity=Vec3.ZERO;navigation.reset();}}previousEnemy=now;
  enemyPeak=target.onGround()?target.getY():Math.max(enemyPeak,target.getY());
  if(target.isSwinging()&&(int)(target.getSwingAnimation(0)*100)<lastTargetSwing){enemySwingAt=clock;swingsSeen++;}lastTargetSwing=(int)(target.getSwingAnimation(0)*100);
  Vec3 future=predict(4);incoming=target.getMainHandItem().is(Items.MACE)&&target.getY()>self.getY()+1.2&&velocity.y<-.08&&new Vec3(future.x,0,future.z).distanceToSqr(new Vec3(self.getX(),0,self.getZ()))<16;
  if(clock>=enemyPearlUntil)enemyPearl=null;if(clock%8==0)for(Entity e:level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl pearl&&pearl.getOwner()==target){enemyPearl=MaceProjectiles.observedPearlLanding(c,pearl);enemyPearlUntil=clock+16;break;}
 }
 private int armorEnchant(String name){int sum=0;for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))sum+=MaceLoadout.enchantments(self.getItemBySlot(slot)).getOrDefault(name,0);return sum;}
 private double health(){return self.getHealth()+self.getAbsorptionAmount();}
 private double fallRisk(){double extra=Math.max(0,-self.getDeltaMovement().y)*4;return MacePhysics.fallDamage(self.fallDistance+extra,armorEnchant("protection"),MaceLoadout.enchantments(self.getItemBySlot(EquipmentSlot.FEET)).getOrDefault("feather_falling",0));}
 private boolean lethalIncoming(){if(!incoming)return false;var w=MaceLoadout.read(-1,target.getMainHandItem());double f=Math.max(1.6,Math.max(target.fallDistance,enemyPeak-target.getY())+target.getY()-self.getY());double raw=MacePhysics.smashDamage(w.damage(),f,w.level("density"));return MacePhysics.reduced(raw,self.getArmorValue(),self.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS),w.level("breach"),armorEnchant("protection"))>=health()-2;}
 public void tick(Minecraft c){
  ready=false;if(!enabled())return;clock++;
  if(self!=c.player||level!=c.level||c.getConnection()==null){stop(c,"World changed · rearm Mace PvP");return;}
  if(!self.isAlive()||self.isSpectator()){stop(c,"Player unavailable · rearm after respawning");return;}
  if(target!=null&&(target.getHealth()<=0||target.getRemovalReason()==Entity.RemovalReason.KILLED)){stop(c,"Target defeated · Mace PvP stopped");return;}
  pickTarget(c);kit=MaceLoadout.scan(self);if(target!=null)observe(c);else incoming=false;
  double hp=health();if(lastHealth>hp+4)recoverUntil=Math.max(recoverUntil,clock+12);lastHealth=hp;
  emergency=hp<=config.maceRetreatHealth||clock<recoverUntil||lethalIncoming()||(!self.onGround()&&fallRisk()>=hp-2);
  if(emergency){move=null;releaseShield(c,false);}
  ChunkFinderClient.MODULES.tick(c); // Totem refills precede every offensive decision.
  if(self.onGround()&&launchUntil<=clock&&!smashWasFalling)flightEnchantments=null;
  if(c.gui.screen()!=null||c.gui.overlay()!=null||!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed()||c.isPaused()){releaseShield(c,true);mouseUseKnown=false;goal=null;phase="paused";status="Paused · return to Minecraft";return;}
  if(self.isFallFlying()||ChunkFinderClient.MODULES.cameraActive()||config.isModuleEnabled("flight")||config.isModuleEnabled("noclip")||config.isModuleEnabled("super_trident")){releaseShield(c,true);phase="paused";status="Turn off flight / detached camera / Trident automation · Elytra is never used";return;}
  if(target==null){releaseShield(c,false);goal=null;phase="search";status="Waiting for a visible non-friend";return;}
  if(!inventorySafe()||!settleMove()){releaseShield(c,false);phase="inventory";return;}
  if(manualUse(c)||self.isUsingItem()&&!ownedShield){phase="manual";status="Paused for your item use";goal=null;return;}
  if(item(Kind.MACE)==null||weapon(Kind.MACE)==null){releaseShield(c,false);phase="equipment";status="No usable, compatible mace in inventory · charges preserved";goal=null;return;}
  if(pearlOrigin!=null){if(self.position().distanceToSqr(pearlOrigin)>25||clock>=pearlUntil){pearlOrigin=null;navigation.reset();}else{phase="pearl";status="Pearl in flight · waiting for actual teleport";goal=null;return;}}
  ready=true;goal=predict(3);
  if(incoming||emergency){
   phase="survival";goal=retreatGoal();status=incoming?"Incoming mace · dodge the projected landing":"Survival · totem, spacing and safe landing";
   if((lethalIncoming()||emergency&&hp<config.maceRetreatHealth||fallRisk()>=hp-2)&&(pearl(c,true)||move!=null))return;
   // A verified descending hit is also a possible safe landing, never an airborne random swing.
   if(!incoming&&canSmash(c)&&smash(c))return;
   if(incoming||hp<=config.maceRetreatHealth||clock<recoverUntil)return;
  }
  if(ownedShield){
   if(!self.isUsingItem()||!self.getUseItem().is(Items.SHIELD)||self.getCooldowns().isOnCooldown(self.getOffhandItem())){releaseShield(c,false);phase="dodge";status="Shield unavailable · dodge instead";goal=retreatGoal();return;}
   if(clock<shieldUntil&&!target.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)&&!incoming){c.options.keyUse.setDown(true);phase="defend";status="Blocking a visible melee approach";return;}
   releaseShield(c,false);
  }
  if(clock-requestAt<=14&&smashWasFalling){
   boolean damage=damageAt>=requestAt&&damageCause==self.getId();
   if(MacePhysics.confirmedBurst(burstLevel,true,damage,self.getDeltaMovement().y>.2)){burstChains++;requestAt=-100;smashWasFalling=false;phase="ascending";status="Wind Burst confirmed · align for another descent";}
   else {phase="recover";status=damage?"Smash confirmed · monitoring actual movement":"Smash requested · waiting for server damage";if(!self.onGround())airGoal(c);return;}
  }else if(smashWasFalling){smashWasFalling=false;status="No confirmed Wind Burst · safe landing";}
  if(launchUntil>clock){
   if(self.getDeltaMovement().y>.2){launchUntil=0;phase="ascending";status="Wind-charge ascent confirmed";}
   else {phase="launch";status="One wind charge sent · waiting for upward motion";equip(c,weapon(Kind.MACE),false);return;}
  }
  if(!self.onGround()){
   phase=self.getDeltaMovement().y>0?"ascending":"descending";airGoal(c);equip(c,weapon(Kind.MACE),false);
   if(canSmash(c)&&smash(c))return;status=phase.equals("ascending")?"Track opponent during ascent · no random airborne attacks":"Align descent or take a safe landing";return;
  }
  if(shield(c)||move!=null)return;
  if(charge(c)||move!=null)return;
  if(self.distanceTo(target)>8&&health()>12&&(pearl(c,false)||move!=null))return;
  phase="melee";groundGoal(c);
  var selected=target.isBlocking()?weapon(Kind.AXE):weapon(Kind.SWORD);if(selected==null)selected=weapon(Kind.AXE);if(selected==null)selected=weapon(Kind.MACE);
  if(target.isBlocking()&&selected!=null&&selected.kind()!=Kind.AXE){goal=retreatGoal();status="Enemy shield raised · preserve attacks until an axe is available";return;}
  if(selected!=null&&equip(c,selected,false)){
   var held=MaceLoadout.read(-1,self.getMainHandItem());if(held.kind()!=selected.kind())return;
   if(held.kind()==Kind.SWORD&&allyNear(target.position(),2))return;
   if(attack(c,.92)){status=held.kind()==Kind.AXE&&target.isBlocking()?"Axe counter against a raised shield":"Cooldown-timed "+held.kind().toString().toLowerCase(Locale.ROOT)+" counterattack";return;}
  }
  status="Spacing and cooldown · conserve wind charges";
 }
 private boolean canSmash(Minecraft c){return !self.onGround()&&self.getDeltaMovement().y<-.05&&MaceItem.canSmashAttack(self)&&visible(target)&&self.isWithinAttackRange(new ItemStack(Items.MACE),target.getBoundingBox(),0)&&!allyNear(target.position(),4);}
 private boolean smash(Minecraft c){
  if(target.isBlocking()){phase="landing";status="Shield covers the landing · do not waste a smash";goal=retreatGoal();return false;}
  var mace=weapon(Kind.MACE);if(!equip(c,mace,false)||!self.getMainHandItem().is(Items.MACE))return false;
  // Full melee charge is preferred. A falling last-chance window may use a legal partial smash.
  double threshold=self.fallDistance>3?.65:.9;
  if(attack(c,threshold)){smashRequests++;requestAt=clock;burstLevel=mace.level("wind_burst");smashWasFalling=true;phase="smash";status="Descending "+(mace.level("density")>0?"Density "+mace.level("density"):mace.level("breach")>0?"Breach "+mace.level("breach"):"mace")+" strike · awaiting server result";return true;}return false;
 }
 private boolean attack(Minecraft c,double threshold){
  if(clock<nextAttack||self.getAttackStrengthScale(.5f)<threshold||!self.isWithinAttackRange(self.getMainHandItem(),target.getBoundingBox(),0)||!visible(target)||target.hurtTime>4)return false;
  Vec3 lead=predict(2).add(0,target.getBbHeight()*.65,0),at=target.getBoundingBox().getCenter();var b=target.getBoundingBox();Vec3 point=new Vec3(Math.clamp(lead.x,b.minX+.05,b.maxX-.05),Math.clamp(lead.y,b.minY+.1,b.maxY-.1),Math.clamp(lead.z,b.minZ+.05,b.maxZ-.05));
  var block=level.clip(new ClipContext(self.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,self));if(block.getType()!=HitResult.Type.MISS)return false;
  for(Entity e:level.entitiesForRendering())if(e instanceof Player p&&p!=self&&p!=target&&Friends.excludes(config,self,p,"bot")&&p.getBoundingBox().inflate(.1).clip(self.getEyePosition(),point).isPresent())return false;
  self.setSprinting(false);ArsenalBot.face(c,point);c.gameMode.attack(self,target);self.swing(InteractionHand.MAIN_HAND,self.getMainHandItem().getAttackAnimation(),false);nextAttack=clock+4;return true;
 }
 private boolean charge(Minecraft c){
  var wind=item(Kind.WIND);int count=kit.stream().filter(w->w.kind()==Kind.WIND).mapToInt(w->w.stack().getCount()).sum();
  if(!config.maceWindCharges||wind==null||clock<chargeAfter||self.distanceTo(target)<2.2||self.distanceTo(target)>5.5||Math.abs(self.getY()-target.getY())>2||target.isBlocking()||allyNear(self.position(),4))return false;
  if(!MacePhysics.launchAllowed(self.onGround(),MaceProjectiles.headroom(c,7),!emergency&&health()>config.maceRetreatHealth+3,visible(target),MaceProjectiles.safeLanding(c,predict(8)),launchUntil>clock,count,config.maceChargeReserve,config.maceChargeCooldown,config.maceChargeCooldown))return false;
  if(!equip(c,wind,false)||!self.getMainHandItem().is(Items.WIND_CHARGE)||self.getCooldowns().isOnCooldown(self.getMainHandItem()))return false;
  flightEnchantments=weapon(Kind.MACE).enchants();releaseShield(c,false);ArsenalBot.face(c,new Vec3(self.getX(),self.getY()-.5,self.getZ()));
  var result=c.gameMode.useItem(self,InteractionHand.MAIN_HAND);chargeAfter=clock+config.maceChargeCooldown;
  if(result.consumesAction()){chargeRequests++;launchUntil=clock+12;phase="launch";status="One deliberate wind-charge launch";return true;}return false;
 }
 private boolean pearl(Minecraft c,boolean retreat){
  if(!config.macePearls||clock<pearlAfter||health()<=5&&!self.getOffhandItem().is(Items.TOTEM_OF_UNDYING))return false;
  var pearl=item(Kind.PEARL);if(pearl==null||self.getCooldowns().isOnCooldown(pearl.stack()))return false;
  var flight=MaceProjectiles.escape(c,target,retreat);pearlAfter=clock+20;if(flight==null)return false;
  if(!equip(c,pearl,false)||!self.getMainHandItem().is(Items.ENDER_PEARL))return false;
  releaseShield(c,false);self.setYRot(flight.yaw());self.setXRot(flight.pitch());self.connection.send(new ServerboundMovePlayerPacket.Rot(flight.yaw(),flight.pitch(),self.onGround(),self.horizontalCollision));
  if(c.gameMode.useItem(self,InteractionHand.MAIN_HAND).consumesAction()){pearlRequests++;pearlAfter=clock+60;pearlOrigin=self.position();pearlUntil=clock+80;phase="pearl";status=retreat?"Emergency pearl toward checked safe ground":"Pearl reposition toward the opponent";return true;}return false;
 }
 private boolean shield(Minecraft c){
  var shield=item(Kind.SHIELD);boolean axe=target.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES),window=self.distanceTo(target)<3.8&&self.getAttackStrengthScale(.5f)<.85&&(clock-enemySwingAt>8||clock-enemySwingAt<3);
  if(!config.maceShield||shield==null||!MacePhysics.shieldAllowed(emergency,incoming,axe,self.getCooldowns().isOnCooldown(shield.stack()),window)||manualUse(c))return false;
  if(!equip(c,shield,true))return false;
  if(!self.getOffhandItem().is(Items.SHIELD)||self.getCooldowns().isOnCooldown(self.getOffhandItem()))return false;
  ArsenalBot.face(c,target.getEyePosition());if(c.gameMode.useItem(self,InteractionHand.OFF_HAND).consumesAction()){ownedShield=true;c.options.keyUse.setDown(true);shieldUntil=clock+6;phase="defend";status="Shield during melee recovery · axe/mace threats use a dodge";return true;}return false;
 }
 private void releaseShield(Minecraft c,boolean force){
  if(!ownedShield)return;boolean manual=!force&&manualUse(c);if(!manual&&c.player==self&&c.gameMode!=null&&self.isUsingItem()&&self.getUseItem().is(Items.SHIELD))c.gameMode.releaseUsingItem(self);
  if(!manual)c.options.keyUse.setDown(false);ownedShield=false;
 }
 private Vec3 retreatGoal(){Vec3 away=self.position().subtract(predict(4)).multiply(1,0,1).normalize();if(away.lengthSqr()<.1)away=new Vec3(1,0,0);double side=clock/12%2==0?1:-1;return self.position().add(away.scale(5)).add(-away.z*side*2,0,away.x*side*2);}
 private void groundGoal(Minecraft c){Vec3 away=self.position().subtract(predict(3)).multiply(1,0,1).normalize();if(away.lengthSqr()<.1)away=new Vec3(0,0,-1);double spacing=target.getMainHandItem().is(Items.MACE)?3.1:2.7;goal=predict(3).add(away.scale(spacing));if(config.maceStrafe){double side=clock/18%2==0?1:-1;goal=goal.add(-away.z*side*.8,0,away.x*side*.8);}if(enemyPearl!=null&&enemyPearl.distanceToSqr(self.position())<16)goal=retreatGoal();}
 private void airGoal(Minecraft c){int time=MacePhysics.descentTicks(Math.max(0,self.getY()-target.getY()),self.getDeltaMovement().y);Vec3 desired=predict(Math.min(12,time));Vec3 floor=MaceProjectiles.ground(c,desired,Math.min(48,(int)Math.abs(self.getY()-target.getY())+12));if(floor!=null&&!target.isBlocking())goal=desired;else{Vec3 safe=MaceProjectiles.ground(c,self.position(),48);goal=safe==null?retreatGoal():safe;phase="landing";}}
 public Input movement(Minecraft c,Input manual){
  if(!enabled()||!ready||!config.maceMovement||goal==null||pearlOrigin!=null||c.gui.screen()!=null||!c.mouseHandler.isMouseGrabbed())return manual;
  if(manual.forward()||manual.backward()||manual.left()||manual.right()||manual.jump()||manual.shift())return manual;
  Vec3 d;boolean jump=false;
  if(self.onGround()){var step=navigation.toward(c,goal);d=step.direction();jump=step.jump();}else d=goal.subtract(self.position()).multiply(1,0,1).normalize();
  if(d.lengthSqr()<.01)return manual;double yaw=Math.toRadians(self.getYRot());Vec3 f=new Vec3(-Math.sin(yaw),0,Math.cos(yaw)),left=new Vec3(Math.cos(yaw),0,Math.sin(yaw));double forward=d.dot(f),side=d.dot(left);
  return new Input(forward>.25,forward<-.25,side>.25,side<-.25,jump,false,!emergency&&!ownedShield&&self.onGround()&&self.distanceTo(target)>5);
 }
 public Map<String,Object> state(){var out=new LinkedHashMap<String,Object>();out.put("enabled",enabled());out.put("phase",phase);out.put("status",status);out.put("target",target==null?"":target.getName().getString());out.put("survival",emergency);out.put("incomingMace",incoming);out.put("equipment",MaceLoadout.state(self));out.put("windCharges",chargeRequests);out.put("smashes",smashRequests);out.put("windBurstChains",burstChains);out.put("pearls",pearlRequests);out.put("movingItem",move!=null);out.put("owningShield",ownedShield);return out;}
}
