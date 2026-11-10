package dev.amethyst.finder.modules.pvp;

import dev.amethyst.finder.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** An explicitly armed, session-scoped coordinator. All actions go through vanilla gameplay. */
public final class ArsenalBot {
    private final FinderConfig config;
    private final PvpAccess access=new PvpAccess();
    private final BotNavigation navigation=new BotNavigation();
    private Vec3 observedPosition,velocity=Vec3.ZERO,pearlOrigin;
    private int motionClock,pearlPendingUntil,flankUntil;
    private long clientTick,handledTick=-1;
    public void beginClientTick(){clientTick++;}
    public boolean handledThisTick(){return handledTick==clientTick;}
    public void afterMovement(Minecraft c){if(!enabled()||handledThisTick())return;handledTick=clientTick;tick(c);ChunkFinderClient.MODULES.tick(c);ChunkFinderClient.PVP.tick(c);}
    private LocalPlayer self;
    private Object level;
    private Player target;
    private int clock,pearlAfter,supportAfter,cleanupUntil,cleanupQuietSince;
    private boolean ready,cleanup;
    private Vec3 deathPosition;
    private String status="Disabled · press P in game";
    public ArsenalBot(FinderConfig config){this.config=config;}
    public boolean enabled(){return config.isModuleEnabled("combat_bot");}
    public boolean ready(){return enabled()&&ready&&!cleanup&&target!=null&&eligible(Minecraft.getInstance(),target);}
    public Player target(){return ready()?target:null;}
    public boolean autoTotem(){return enabled()&&self!=null&&self.isAlive();}
    public String status(){return status;}
    public Map<String,Object> state(){return Map.of("enabled",enabled(),"ready",ready(),"target",target==null?"":target.getName().getString(),"phase",cleanup?"cleanup":ready()?"combat":enabled()?"waiting":"off","status",status,"autoTotem",autoTotem());}
    public void setEnabled(Minecraft c,boolean on) {
        if(on&&(c.player==null||c.level==null||!c.player.isAlive()||c.player.isSpectator()))throw new IllegalArgumentException("Join a world as a living player before arming Combat Bot");
        clearPlans();target=null;ready=false;cleanup=false;deathPosition=null;clock=pearlAfter=supportAfter=pearlPendingUntil=flankUntil=0;observedPosition=null;velocity=Vec3.ZERO;navigation.reset();
        self=on?c.player:null;level=on?c.level:null;config.setModuleEnabled("combat_bot",on);
        status=on?"Armed · return to Minecraft · friends excluded":"Stopped · press P to arm";
    }
    private void clearPlans(){navigation.reset();observedPosition=null;velocity=Vec3.ZERO;if(ChunkFinderClient.PVP!=null)ChunkFinderClient.PVP.clearBotPlans();}
    private void stop(String reason){ready=false;target=null;cleanup=false;clearPlans();config.setModuleEnabled("combat_bot",false);status=reason;config.save();}
    private boolean eligible(Minecraft c,Player p) {
        return self!=null&&c.level==level&&p!=self&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isCreative()&&p.isAttackable()
            &&!Friends.excludes(config,self,p,"bot")&&self.distanceToSqr(p)<=config.botRange*config.botRange;
    }
    public void observedDeath(Entity entity) {
        if(enabled()&&target!=null&&entity==target)finishTarget();
    }
    private void finishTarget() {
        if(cleanup||target==null)return;
        deathPosition=target.position();status="Target defeated · explosions stopped · clearing reachable fire";
        target=null;ready=false;cleanup=true;cleanupUntil=clock+80;cleanupQuietSince=clock;clearPlans();
    }
    public void tick(Minecraft c) {
        ready=false;
        if(!enabled())return;
        clock++;
        if(c.player!=self||c.level!=level||c.getConnection()==null){stop("Stopped: world or connection changed · press P to rearm");return;}
        if(!self.isAlive()||self.isSpectator()){stop("Stopped: player unavailable · press P after respawning");return;}
        if(target!=null&&(target.getHealth()<=0||target.getRemovalReason()==Entity.RemovalReason.KILLED)){finishTarget();}
        if(c.gui.screen()!=null||c.gui.overlay()!=null||!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed()||self.isHandsBusy()) {status=cleanup?"Cleanup paused · return to Minecraft":"Paused · return to Minecraft";return;}
        if(ChunkFinderClient.MODULES.cameraActive()||config.isModuleEnabled("flight")||config.isModuleEnabled("noclip")||config.isModuleEnabled("super_trident")){status="Paused · turn off detached camera / movement automation";return;}
        if(cleanup) {cleanup(c);return;}
        if(target!=null&&!eligible(c,target)){target=null;clearPlans();}
        if(target==null) {
            double best=Double.MAX_VALUE;
            for(Entity e:c.level.entitiesForRendering())if(e instanceof Player p&&eligible(c,p)) {
                double score=self.distanceToSqr(p)+Math.max(0,p.getHealth()+p.getAbsorptionAmount())*.3+(self.hasLineOfSight(p)?0:64);
                if(score<best){target=p;best=score;}
            }
        }
        if(target==null){status="Waiting for a visible non-friend within "+config.botRange+" blocks";return;}
        if(observedPosition!=null&&clock>motionClock){Vec3 delta=target.position().subtract(observedPosition).scale(1d/(clock-motionClock));if(delta.lengthSqr()<4)velocity=velocity.scale(.35).add(delta.scale(.65));else {velocity=Vec3.ZERO;navigation.reset();flankUntil=0;}}
        observedPosition=target.position();motionClock=clock;
        if(pearlOrigin!=null&&(self.position().distanceToSqr(pearlOrigin)>25||clock>=pearlPendingUntil)){pearlOrigin=null;pearlPendingUntil=0;}
        ready=true;status="Fighting "+target.getName().getString()+" · Auto Totem active";
    }
    public void detail(String text){if(ready())status=target.getName().getString()+" · "+text;}
    public void pauseCombat(){ready=false;}
    public void problem(String text){ready=false;status=text;}
    public Vec3 predictedPosition(int ticks){return target==null?Vec3.ZERO:target.position().add(velocity.scale(Math.clamp(ticks,0,24)));}
    public void flank(){if(clock>=flankUntil)flankUntil=clock+20;}
    public boolean pearlInFlight(){return pearlOrigin!=null&&clock<pearlPendingUntil;}
    public Input movement(Minecraft c,Input manual) {
        if(!ready()||!self.isAlive()||!config.botMovement||pearlInFlight()||c.gui.screen()!=null||c.gui.overlay()!=null||!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed())return manual;
        if(manual.forward()||manual.backward()||manual.left()||manual.right()||manual.jump()||manual.shift())return manual;
        Vec3 opponent=predictedPosition(3),away=self.position().subtract(opponent).multiply(1,0,1).normalize();
        if(away.lengthSqr()<.1)away=new Vec3(0,0,-1);
        boolean retreat=self.getHealth()+self.getAbsorptionAmount()<7;
        double vertical=target.getY()-self.getY();
        double spacing=retreat?6:Math.abs(vertical)>1.5?1.15:2.7;
        Vec3 goal=opponent.add(away.scale(spacing));
        if(config.botStrafe&&Math.abs(vertical)<1.5&&(self.distanceTo(target)<6||clock<flankUntil)) {
            double sign=(clock/22%2==0?1:-1),amount=clock<flankUntil?2.7:1.1;
            goal=goal.add(-away.z*sign*amount,0,away.x*sign*amount);
        }
        var step=navigation.toward(c,goal);if(step.direction().lengthSqr()<.01)return manual;
        double yaw=Math.toRadians(self.getYRot());Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw)),left=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
        double f=step.direction().dot(forward),side=step.direction().dot(left);
        return new Input(f>.38,f<-.38,side>.38,side<-.38,step.jump(),false,!retreat&&self.distanceTo(target)>5);
    }
    private boolean solid(Minecraft c,BlockPos p){return c.level.hasChunkAt(p)&&!c.level.getBlockState(p).getCollisionShape(c.level,p).isEmpty();}
    private boolean walkable(Minecraft c,Vec3 position,boolean stepUp) {
        BlockPos feet=BlockPos.containing(position);
        if(!c.level.hasChunkAt(feet)||!c.level.getFluidState(feet).isEmpty()||!c.level.getFluidState(feet.below()).isEmpty())return false;
        if(solid(c,feet)&&stepUp)feet=feet.above();
        return solid(c,feet.below())&&!solid(c,feet)&&!solid(c,feet.above())&&!c.level.getBlockState(feet).is(Blocks.FIRE)&&!c.level.getBlockState(feet).is(Blocks.SOUL_FIRE);
    }
    public static void look(Minecraft c,Vec3 point) {
        Vec3 d=point.subtract(c.player.getEyePosition());
        c.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));
        c.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    public static void face(Minecraft c,Vec3 point) {
        look(c,point);
        c.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(c.player.getYRot(),c.player.getXRot(),c.player.onGround(),c.player.horizontalCollision));
    }
    private record Throw(float yaw,float pitch,Vec3 landing,double score){}
    public boolean pearl(Minecraft c) throws ReflectiveOperationException {
        if(!ready()||!config.botPearls||pearlInFlight()||clock<pearlAfter||self.distanceTo(target)<6&&Math.abs(self.getY()-target.getY())<3||self.getHealth()+self.getAbsorptionAmount()<7)return false;
        int chosen=slot(Items.ENDER_PEARL);if(chosen<0||self.getCooldowns().isOnCooldown(self.getInventory().getItem(chosen)))return false;
        pearlAfter=clock+8;
        double approximateTicks=Math.min(30,self.distanceTo(target)/1.3);
        Vec3 future=predictedPosition((int)approximateTicks),away=self.position().subtract(future).multiply(1,0,1).normalize();
        Throw best=null;
        for(double radius:new double[]{3,4.5,6})for(double offset:new double[]{0,1.5,-1.5}) {
            Vec3 destination=future.add(away.scale(radius)).add(-away.z*offset,0,away.x*offset);
            BlockPos floor=BlockPos.containing(destination);if(!BotNavigation.standing(c,floor))continue;
            Vec3 delta=destination.add(0,.1,0).subtract(self.getEyePosition());double horizontal=Math.hypot(delta.x,delta.z),speed=1.5,g=.03;
            double discriminant=Math.pow(speed,4)-g*(g*horizontal*horizontal+2*delta.y*speed*speed);if(horizontal<3||discriminant<0)continue;
            double base=Math.toDegrees(Math.atan((speed*speed-Math.sqrt(discriminant))/(g*horizontal)));
            for(double correction:new double[]{0,2,4,-2}) {
                double angle=Math.toRadians(base+correction);Vec3 direction=new Vec3(delta.x/horizontal*Math.cos(angle)*speed,Math.sin(angle)*speed,delta.z/horizontal*Math.cos(angle)*speed);
                Vec3 inherited=self.getDeltaMovement();Vec3 flight=direction.add(inherited.x,self.onGround()?0:inherited.y,inherited.z),point=self.getEyePosition();
                for(int t=1;t<=70;t++) {
                    Vec3 next=point.add(flight);if(!c.level.hasChunkAt(BlockPos.containing(next)))break;
                    var hit=c.level.clip(new ClipContext(point,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,self));
                    if(hit.getType()!=HitResult.Type.MISS) {
                        Vec3 landing=hit.getLocation().add(0,.02,0);BlockPos feet=BlockPos.containing(landing);
                        if(hit.getDirection()!=Direction.UP||!BotNavigation.standing(c,feet)||landing.distanceToSqr(destination)>9)break;
                        boolean danger=false;
                        for(Entity entity:c.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal&&entity.isAlive()&&entity.position().distanceToSqr(landing)<25){danger=true;break;}
                        if(danger)break;
                        double distance=landing.distanceTo(predictedPosition(t));if(distance<2||distance>7)break;
                        double score=Math.abs(distance-3)+landing.distanceTo(destination)*.3+t*.015;
                        if(best==null||score<best.score)best=new Throw((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),(float)-Math.toDegrees(angle),landing,score);break;
                    }
                    point=next;flight=flight.scale(.99).add(0,-g,0);
                }
            }
        }
        if(best==null){detail("No clear pearl landing near predicted opponent · navigating around obstacles");return false;}
        int previous=self.getInventory().getSelectedSlot();
        try {self.getInventory().setSelectedSlot(chosen);self.setYRot(best.yaw);self.setXRot(best.pitch);
            self.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(best.yaw,best.pitch,self.onGround(),self.horizontalCollision));
            boolean used=access.useItem(c);pearlAfter=clock+20;if(used){pearlOrigin=self.position();pearlPendingUntil=clock+80;detail("Pearl pursuit · predicted player landing, not a crystal");}return used;
        }finally{if(self.getInventory().getSelectedSlot()==chosen)self.getInventory().setSelectedSlot(previous);}
    }
    private int slot(net.minecraft.world.item.Item item){for(int i=0;i<9;i++)if(self.getInventory().getItem(i).is(item))return i;return -1;}
    private void cleanup(Minecraft c) {
        if(!config.botExtinguish||clock>=cleanupUntil){stop("Target defeated · stopped · fire outside reach may remain");return;}
        if(clock%3!=0)return;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(deathPosition).offset(-4,-2,-4),BlockPos.containing(deathPosition).offset(4,2,4))) {
            if(!c.level.hasChunkAt(pos)||(!c.level.getBlockState(pos).is(Blocks.FIRE)&&!c.level.getBlockState(pos).is(Blocks.SOUL_FIRE))||!self.isWithinBlockInteractionRange(pos,0))continue;
            Vec3 end=Vec3.atCenterOf(pos).add(0,-.55,0);
            var hit=c.level.clip(new ClipContext(self.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,self));
            if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(pos.below()))continue;
            cleanupQuietSince=clock;c.gameMode.startDestroyBlock(pos.immutable(),Direction.UP);status="Target defeated · extinguishing reachable fire";return;
        }
        // Death, item and block packets can arrive in different client ticks. Allow the
        // explosion's fire updates and our extinguish acknowledgement to settle first.
        if(clock-cleanupQuietSince<12){status="Target defeated · waiting for nearby block updates";return;}
        stop("Target defeated · reachable fire cleared · press P for the next fight");
    }
}
