package dev.amethyst.finder.modules.pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Collision-limited vanilla pearl trajectories and conservative landing samples. */
public final class MaceProjectiles {
 public record Throw(float yaw,float pitch,Vec3 landing,double score){}
 public static Vec3 ground(Minecraft c,Vec3 around,int maxDrop){
  if(c.level==null||!c.level.hasChunkAt(BlockPos.containing(around)))return null;
  var hit=c.level.clip(new ClipContext(around.add(0,2,0),around.add(0,-maxDrop,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,c.player));
  if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)return null;
  BlockPos feet=hit.getBlockPos().above();return BotNavigation.standing(c,feet)?Vec3.atBottomCenterOf(feet):null;
 }
 public static boolean safeLanding(Minecraft c,Vec3 point){
  Vec3 floor=ground(c,point,12);if(floor==null)return false;
  for(Entity e:c.level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal&&e.position().distanceToSqr(floor)<36)return false;
  return true;
 }
 public static boolean headroom(Minecraft c,int blocks){return c.level.noCollision(c.player,c.player.getBoundingBox().expandTowards(0,blocks,0));}
 public static Throw escape(Minecraft c,Player enemy,boolean retreat){
  Vec3 origin=c.player.getEyePosition(),away=c.player.position().subtract(enemy.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.1)away=new Vec3(1,0,0);
  Throw best=null;int budget=420;
  for(double angle:new double[]{0,.65,-.65,1.25,-1.25,2.2,-2.2}){
   double ca=Math.cos(angle),sa=Math.sin(angle);Vec3 side=new Vec3(away.x*ca-away.z*sa,0,away.x*sa+away.z*ca);
   Vec3 desired=retreat?c.player.position().add(side.scale(10)):enemy.position().add(side.scale(3));Vec3 floor=ground(c,desired,12);if(floor==null)continue;
   Vec3 delta=floor.add(0,.2,0).subtract(origin);double range=delta.horizontalDistance(),speed=1.5,g=.03,disc=Math.pow(speed,4)-g*(g*range*range+2*delta.y*speed*speed);
   if(range<3||range>18||disc<0)continue;
   double base=Math.atan((speed*speed-Math.sqrt(disc))/(g*range));
   for(double extra:new double[]{0,.035}){
    double pitch=base+extra;Vec3 v=new Vec3(delta.x/range*Math.cos(pitch)*speed,Math.sin(pitch)*speed,delta.z/range*Math.cos(pitch)*speed).add(c.player.getDeltaMovement().multiply(1,c.player.onGround()?0:1,1));Vec3 at=origin;
    for(int t=1;t<=70&&budget-->0;t++){
     Vec3 next=at.add(v);if(!c.level.hasChunkAt(BlockPos.containing(next)))break;
     var hit=c.level.clip(new ClipContext(at,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,c.player));
     boolean entityHit=false;for(Entity e:c.level.entitiesForRendering())if(e instanceof LivingEntity&&e!=c.player&&e.getBoundingBox().inflate(.25).clip(at,next).isPresent()){entityHit=true;break;}if(entityHit)break;
     if(hit.getType()!=HitResult.Type.MISS){
      Vec3 landing=hit.getLocation().add(0,.02,0);
      if(hit.getDirection()!=Direction.UP||landing.distanceToSqr(floor)>6||!safeLanding(c,landing))break;
      double distance=landing.distanceTo(enemy.position());if(retreat&&distance<7||!retreat&&(distance<2||distance>6))break;
      double score=retreat?distance-t*.025:6-Math.abs(distance-3)-t*.025;
      if(best==null||score>best.score)best=new Throw((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),(float)-Math.toDegrees(pitch),landing,score);break;
     }at=next;v=v.scale(.99).add(0,-g,0);
    }
   }
  }return best;
 }
 public static Vec3 observedPearlLanding(Minecraft c,Entity pearl){
  Vec3 at=pearl.position(),v=pearl.getDeltaMovement();for(int t=0;t<60;t++){Vec3 next=at.add(v);if(!c.level.hasChunkAt(BlockPos.containing(next)))return null;var hit=c.level.clip(new ClipContext(at,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,pearl));if(hit.getType()!=HitResult.Type.MISS)return hit.getLocation();at=next;v=v.scale(.99).add(0,-.03,0);}return null;
 }
 private MaceProjectiles(){}
}
