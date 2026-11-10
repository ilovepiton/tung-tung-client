package dev.amethyst.finder.modules.pvp;

import dev.amethyst.finder.*;
import dev.amethyst.finder.modules.PvpModules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** A single action owner: independent target prediction, scored blast sites and acknowledged combos. */
public final class BotCombatPlanner {
    private final FinderConfig config;
    private final PvpModules owner;
    private final PvpAccess access=new PvpAccess();
    private final Map<BlockPos,Integer> rejected=new HashMap<>();
    private Plan pending;
    private int clock,scanAfter,supports,lastProgress,lastHurt,lastSword=-30,completedStreak;
    private float lastHealth=Float.NaN;
    private String completed="",reason="Positioning";
    private UUID targetId;
    private Candidate planned;
    private record Damage(double enemy,double self,boolean safe) {double score(){return enemy-self*.55;}}
    private record Candidate(String kind,BlockPos pos,Entity entity,Object hit,Damage damage,BlockPos cover) {
        double score(){return damage.score()-(kind.equals("anchor")?1:kind.equals("base")?2:0);}
    }
    private static final class Plan {
        Candidate action;int stage,at,sequence,tries;long revision;BlockPos cover;
        Plan(Candidate action,int clock){this.action=action;this.at=clock;this.cover=action.cover;}
    }
    public BotCombatPlanner(FinderConfig config,PvpModules owner){this.config=config;this.owner=owner;}
    public void reset(){pending=null;planned=null;targetId=null;rejected.clear();clock=scanAfter=supports=lastProgress=completedStreak=0;completed="";lastHealth=Float.NaN;}
    public boolean tick(Minecraft c) throws ReflectiveOperationException {
        clock++;var bot=ChunkFinderClient.BOT;Player target=bot.target();
        if(target==null){pending=null;planned=null;return false;}
        if(!target.getUUID().equals(targetId)){reset();targetId=target.getUUID();}
        float health=target.getHealth()+target.getAbsorptionAmount();
        if((!Float.isNaN(lastHealth)&&health<lastHealth-.05f)||target.hurtTime>lastHurt){lastProgress=clock;}
        lastHealth=health;lastHurt=target.hurtTime;
        rejected.entrySet().removeIf(e->e.getValue()<clock);
        if(pending!=null) {
            if((pending.action.kind.equals("anchor")&&!config.botAnchors)||(pending.action.kind.equals("crystal")&&!config.botCrystals)){pending=null;}
            else if(advance(c,target)){bot.detail(reason);return true;}
            if(pending!=null){if(melee(c,target))return true;bot.detail(reason);return false;}
        }
        if(bot.pearl(c))return true;
        if(bot.pearlInFlight()){bot.detail("Pearl in flight · following predicted opponent position");return false;}
        if(clock>=scanAfter){scanAfter=clock+2;planned=choose(c,target);}
        if(planned!=null) {
            Candidate action=planned;planned=null;
            if(action.kind.equals("base")) {
                if(config.botBuild&&supports<2&&place(c,action.pos,Items.OBSIDIAN)){supports++;scanAfter=clock+4;bot.detail("One crystal base requested · waiting for usable attack");return true;}
            } else {pending=new Plan(action,clock);boolean acted=advance(c,target);bot.detail(reason);return acted;}
        }
        if(melee(c,target))return true;
        if(clock-lastProgress>30)bot.flank();
        bot.detail(reason);return false;
    }
    private Candidate choose(Minecraft c,Player target) throws ReflectiveOperationException {
        var choices=new ArrayList<Candidate>();var bases=new ArrayList<Candidate>();var bot=ChunkFinderClient.BOT;
        boolean crystals=config.botCrystals&&slot(c,Items.END_CRYSTAL)>=0;
        boolean anchors=config.botAnchors&&slot(c,Items.RESPAWN_ANCHOR)>=0&&slot(c,Items.GLOWSTONE)>=0;
        reason=(!crystals&&!anchors)?"No Crystal / Anchor kit in hotbar · using sword and movement":"No damaging reachable blast site · changing position";
        int evaluated=0;
        if(config.botCrystals)for(Entity entity:c.level.entitiesForRendering())if(entity instanceof EndCrystal&&entity.isAlive()&&!entity.isRemoved()
                &&c.player.isWithinEntityInteractionRange(entity,0)&&c.player.hasLineOfSight(entity)) {
            Damage d=damage(c,target,entity.position(),6,null,null);
            if(d.safe&&d.enemy>=config.botMinDamage)choices.add(new Candidate("crystal",entity.blockPosition(),entity,null,d,null));
            if(++evaluated>=12)break;
        }
        var positions=new ArrayList<BlockPos>();BlockPos origin=BlockPos.containing(bot.predictedPosition(2));
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=1;y++)positions.add(origin.offset(x,y,z));
        positions.sort(Comparator.comparingDouble(p->Vec3.atCenterOf(p).distanceToSqr(bot.predictedPosition(2))));
        int considered=0;
        for(BlockPos pos:positions) {
            if(!c.level.hasChunkAt(pos)||rejected.containsKey(pos)||!c.player.isWithinBlockInteractionRange(pos,0))continue;
            var state=c.level.getBlockState(pos);Vec3 center=Vec3.atCenterOf(pos);
            if(crystals&&(state.is(Blocks.OBSIDIAN)||state.is(Blocks.BEDROCK))&&c.level.getBlockState(pos.above()).isAir()&&c.level.getBlockState(pos.above(2)).isAir()
                    &&clear(c,new AABB(pos.above()).expandTowards(0,1,0))&&crystalReach(c,center.add(0,.5,0))) {
                Object hit=access.rayToBlock(c,pos,center.add(0,.49,0),false);
                if(hit!=null){Damage d=damage(c,target,center.add(0,.5,0),6,null,null);BlockPos cover=null;
                    if(!d.safe&&config.botBuild&&supports<2&&slot(c,Items.OBSIDIAN)>=0){cover=cover(c,center.add(0,.5,0),pos);if(cover!=null)d=damage(c,target,center.add(0,.5,0),6,null,cover);}
                    if(d.safe&&d.enemy>=config.botMinDamage)choices.add(new Candidate("crystal",pos,null,hit,d,cover));if(++considered>=24)break;}
            }
            boolean exists=state.is(Blocks.RESPAWN_ANCHOR);
            if(config.botAnchors&&(anchors||exists&&(access.charges(state)>0||slot(c,Items.GLOWSTONE)>=0))
                    &&(exists||state.isAir()&&clear(c,new AABB(pos)))&&!access.anchorWorks(c,pos)) {
                Object hit=exists?access.rayToBlock(c,pos,center,false):placementHit(c,pos);
                if(hit!=null){
                    Damage d=damage(c,target,center,5,exists?pos:null,null);BlockPos cover=null;
                    if(!d.safe&&config.botBuild&&slot(c,Items.OBSIDIAN)>=0&&supports<2) {
                        cover=cover(c,center,pos);if(cover!=null)d=damage(c,target,center,5,exists?pos:null,cover);
                    }
                    if(d.safe&&d.enemy>=config.botMinDamage)choices.add(new Candidate("anchor",pos,null,hit,d,cover));
                    if(++considered>=24)break;
                }
            }
            // A base is useful only if an actual crystal here passes damage/reach checks.
            if(crystals&&config.botBuild&&supports<2&&state.isAir()&&solid(c,pos.below())&&clear(c,new AABB(pos).expandTowards(0,2,0))&&crystalReach(c,center.add(0,.5,0))) {
                Object hit=access.rayToBlock(c,pos.below(),center.add(0,-.51,0),true);
                if(hit!=null){Damage d=damage(c,target,center.add(0,.5,0),6,null,null);if(d.safe&&d.enemy>=config.botMinDamage)bases.add(new Candidate("base",pos,null,hit,d,null));if(++considered>=24)break;}
            }
        }
        if(choices.isEmpty())choices=bases;
        choices.sort(Comparator.comparingDouble(Candidate::score).reversed());
        if(choices.isEmpty())return null;
        Candidate best=choices.getFirst();
        // Do not let a permanently available crystal base starve a viable Anchor combo.
        if(completedStreak>=1&&!completed.isEmpty())for(Candidate option:choices)
            if(!option.kind.equals(completed)&&!option.kind.equals("base")&&option.score()>=Math.max(.2,best.score()*.6)){best=option;break;}
        return best;
    }
    private boolean advance(Minecraft c,Player target) throws ReflectiveOperationException {
        Plan plan=pending;Candidate action=plan.action;BlockPos pos=action.pos;
        if(!c.level.hasChunkAt(pos)||clock-plan.at>40){reject(pos,"Server did not confirm combo · trying another site");return false;}
        if(action.kind.equals("crystal")) {
            if(plan.stage==0&&plan.cover!=null&&!solid(c,plan.cover)) {
                if(!place(c,plan.cover,Items.OBSIDIAN)){reject(pos,"Cannot place crystal cover");return false;}
                supports++;plan.stage=3;plan.at=clock;reason="Waiting for crystal cover";return true;
            }
            if(plan.stage==3){if(!PvpAcknowledgements.serverBlock(c,plan.cover).is(Blocks.OBSIDIAN)){reason="Waiting for crystal cover confirmation";return false;}plan.stage=0;}
            Entity crystal=action.entity;
            if(crystal==null)for(Entity entity:c.level.entitiesForRendering())if(entity instanceof EndCrystal&&entity.isAlive()&&!entity.isRemoved()&&entity.position().distanceToSqr(Vec3.atCenterOf(pos).add(0,.5,0))<.25){crystal=entity;break;}
            if(plan.stage==0&&crystal==null) {
                if(!slotClick(c,Items.END_CRYSTAL,action.hit,Vec3.atCenterOf(pos).add(0,.5,0))){reject(pos,"Crystal placement rejected");return false;}
                plan.stage=1;plan.at=clock;reason="Waiting for crystal spawn";return true;
            }
            if(crystal==null||crystal.isRemoved()) {
                if(plan.stage==2&&action.entity!=null&&PvpAcknowledgements.entityRemovalRevision(c,action.entity.getId())>plan.revision){complete("crystal");return false;}
                reason="Waiting for server crystal spawn";return false;
            }
            if(plan.stage==2&&clock-plan.at<3){reason="Waiting for crystal removal";return false;}
            if(!c.player.isWithinEntityInteractionRange(crystal,0)||!c.player.hasLineOfSight(crystal)){pending=null;reason="Crystal outside normal reach · approaching opponent";return false;}
            Damage d=damage(c,target,crystal.position(),6,null,null);
            if(!d.safe||d.enemy<config.botMinDamage){pending=null;reason="Blast position changed · repositioning";return false;}
            if(target.hurtTime>1){reason="Waiting for opponent damage immunity to end";return false;}
            if(plan.tries++>=3){reject(pos,"Crystal attack declined · changing site");return false;}
            plan.revision=PvpAcknowledgements.entityRemovalRevision(c,crystal.getId());ArsenalBot.face(c,crystal.getBoundingBox().getCenter());owner.assistedAttack(c,crystal);plan.action=new Candidate("crystal",pos,crystal,null,d,null);plan.stage=2;plan.at=clock;
            reason=String.format(Locale.ROOT,"Crystal · target %.1f / self %.1f",d.enemy,d.self);ChunkFinderClient.BOT.detail(reason);return true;
        }
        var state=c.level.getBlockState(pos);boolean exists=state.is(Blocks.RESPAWN_ANCHOR);
        if(plan.stage==0) {
            if(exists){plan.stage=2;}else{
                if(!place(c,pos,Items.RESPAWN_ANCHOR)){reject(pos,"Anchor placement rejected");return false;}
                plan.stage=1;plan.at=clock;reason="Waiting for server anchor";return true;
            }
        }
        // Read an authoritative block update even on servers that omit vanilla sequence ACKs.
        if(plan.stage==1) {
            if(!PvpAcknowledgements.serverBlock(c,pos).is(Blocks.RESPAWN_ANCHOR)){reason="Waiting for observed anchor placement";return false;}
            plan.stage=2;
        }
        if(!exists){if(plan.stage>=6){if(PvpAcknowledgements.serverBlockKnown(c,pos)&&!PvpAcknowledgements.serverBlock(c,pos).is(Blocks.RESPAWN_ANCHOR))complete("anchor");else reason="Waiting for server anchor removal";return false;}reject(pos,"Anchor removed before combo finished");return false;}
        int charge=access.charges(state);
        Object hit=access.rayToBlock(c,pos,Vec3.atCenterOf(pos),false);
        if(hit==null||!c.player.isWithinBlockInteractionRange(pos,0)){reject(pos,"Anchor no longer visible / reachable");return false;}
        if(plan.stage==2) {
            if(charge>0){plan.stage=4;}else{
                if(!slotClick(c,Items.GLOWSTONE,hit,Vec3.atCenterOf(pos))){reject(pos,"Anchor charging rejected");return false;}
                plan.stage=3;plan.at=clock;reason="Waiting for observed anchor charge";return true;
            }
        }
        if(plan.stage==3) {
            var observed=PvpAcknowledgements.serverBlock(c,pos);
            if(!observed.is(Blocks.RESPAWN_ANCHOR)||access.charges(observed)<=0){reason="Waiting for observed anchor charge";return false;}
            plan.stage=4;
        }
        if(plan.stage==4&&plan.cover!=null&&!solid(c,plan.cover)) {
            if(!place(c,plan.cover,Items.OBSIDIAN)){reject(pos,"Cannot place the planned protective block");return false;}
            supports++;plan.stage=5;plan.at=clock;reason="Waiting for protective obsidian";return true;
        }
        if(plan.stage==5&&!PvpAcknowledgements.serverBlock(c,plan.cover).is(Blocks.OBSIDIAN)){reason="Waiting for protective block confirmation";return false;}
        if(plan.stage>=6) {
            if(PvpAcknowledgements.serverBlockKnown(c,pos)&&!PvpAcknowledgements.serverBlock(c,pos).is(Blocks.RESPAWN_ANCHOR)){complete("anchor");return false;}
            if(clock-plan.at<4){reason="Waiting for anchor removal";return false;}
            if(plan.tries++>=2){reject(pos,"Anchor detonation declined · changing site");return false;}
        }
        if(target.hurtTime>1){reason="Charged anchor ready · timing the next damaging hit";return false;}
        Damage d=damage(c,target,Vec3.atCenterOf(pos),5,pos,null);
        if(!d.safe||d.enemy<config.botMinDamage){reject(pos,"Anchor blast no longer useful / safe");return false;}
        if(c.player.getOffhandItem().is(Items.GLOWSTONE)){reject(pos,"Glowstone in offhand prevents anchor detonation");return false;}
        int slot=detonationSlot(c);if(slot<0){reject(pos,"A non-glowstone hotbar slot is required");return false;}
        if(!click(c,slot,hit,Vec3.atCenterOf(pos))){reject(pos,"Anchor detonation rejected");return false;}
        plan.stage=6;plan.at=clock;reason=String.format(Locale.ROOT,"Anchor · target %.1f / self %.1f",d.enemy,d.self);ChunkFinderClient.BOT.detail(reason);return true;
    }
    private void reject(BlockPos pos,String text){rejected.put(pos.immutable(),clock+30);pending=null;planned=null;scanAfter=clock;reason=text;}
    private void complete(String kind){supports=0;completedStreak=kind.equals(completed)?completedStreak+1:1;completed=kind;pending=null;planned=null;scanAfter=clock;reason=kind+" explosion observed";}
    private boolean melee(Minecraft c,Player target) throws ReflectiveOperationException {
        if(target.hurtTime>1||clock-lastSword<4||!c.player.hasLineOfSight(target)||!c.player.isWithinEntityInteractionRange(target,0))return false;
        int sword=-1;for(Item item:new Item[]{Items.NETHERITE_SWORD,Items.DIAMOND_SWORD,Items.IRON_SWORD,Items.NETHERITE_AXE,Items.DIAMOND_AXE}){sword=slot(c,item);if(sword>=0)break;}
        if(sword<0)return false;int previous=c.player.getInventory().getSelectedSlot();
        try{c.player.getInventory().setSelectedSlot(sword);if(c.player.getAttackStrengthScale(.5f)<.92||!c.player.isWithinAttackRange(c.player.getMainHandItem(),target.getBoundingBox(),0))return false;
            ArsenalBot.face(c,ChunkFinderClient.BOT.predictedPosition(1).add(0,target.getEyeHeight()*.75,0));owner.assistedAttack(c,target);lastSword=clock;ChunkFinderClient.BOT.detail("Sword opening · tracking opponent motion");return true;
        }finally{if(c.player.getInventory().getSelectedSlot()==sword)c.player.getInventory().setSelectedSlot(previous);}
    }
    private int slot(Minecraft c,Item item){for(int i=0;i<9;i++)if(c.player.getInventory().getItem(i).is(item))return i;return -1;}
    private int detonationSlot(Minecraft c){for(int i=0;i<9;i++){var stack=c.player.getInventory().getItem(i);if(!stack.is(Items.GLOWSTONE)&&!stack.is(Items.RESPAWN_ANCHOR)&&!stack.is(Items.END_CRYSTAL)&&!stack.is(Items.ENDER_PEARL)&&!stack.is(Items.OBSIDIAN))return i;}return -1;}
    private boolean slotClick(Minecraft c,Item item,Object hit,Vec3 point) throws ReflectiveOperationException {int chosen=slot(c,item);return chosen>=0&&click(c,chosen,hit,point);}
    private boolean click(Minecraft c,int chosen,Object hit,Vec3 point) throws ReflectiveOperationException {
        int previous=c.player.getInventory().getSelectedSlot();try{c.player.getInventory().setSelectedSlot(chosen);ArsenalBot.face(c,hit instanceof BlockHitResult blockHit?blockHit.getLocation():point);return owner.assistedClick(c,hit,InteractionHand.MAIN_HAND);}
        finally{if(c.player.getInventory().getSelectedSlot()==chosen)c.player.getInventory().setSelectedSlot(previous);}
    }
    private boolean place(Minecraft c,BlockPos pos,Item item) throws ReflectiveOperationException {
        if(!c.level.getBlockState(pos).isAir()||!clear(c,new AABB(pos)))return false;
        Object hit=placementHit(c,pos);return hit!=null&&slotClick(c,item,hit,Vec3.atCenterOf(pos));
    }
    private Object placementHit(Minecraft c,BlockPos pos) throws ReflectiveOperationException {
        for(Direction direction:new Direction[]{Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP}){
            BlockPos support=pos.relative(direction);if(!solid(c,support)||!c.player.isWithinBlockInteractionRange(support,0))continue;
            Vec3 face=Vec3.atCenterOf(support).add(-direction.getStepX()*.499,-direction.getStepY()*.499,-direction.getStepZ()*.499);
            Object hit=access.rayToBlock(c,support,face,false);
            if(hit instanceof BlockHitResult block&&block.getBlockPos().relative(block.getDirection()).equals(pos))return hit;
        }
        return null;
    }
    private BlockPos cover(Minecraft c,Vec3 explosion,BlockPos anchor) throws ReflectiveOperationException {
        Vec3 direction=explosion.subtract(c.player.position()).multiply(1,0,1).normalize();BlockPos pos=BlockPos.containing(c.player.position().add(direction.scale(1.1)));
        return !pos.equals(anchor)&&c.level.getBlockState(pos).isAir()&&solid(c,pos.below())&&clear(c,new AABB(pos))&&c.player.isWithinBlockInteractionRange(pos.below(),0)
            &&access.rayToBlock(c,pos.below(),Vec3.atCenterOf(pos).add(0,-.51,0),true)!=null?pos:null;
    }
    private boolean solid(Minecraft c,BlockPos p){return c.level.hasChunkAt(p)&&!c.level.getBlockState(p).getCollisionShape(c.level,p).isEmpty();}
    private boolean clear(Minecraft c,AABB box){return c.level.getEntities(null,box).isEmpty();}
    private boolean crystalReach(Minecraft c,Vec3 p){Vec3 eye=c.player.getEyePosition();return eye.distanceToSqr(new Vec3(Math.clamp(eye.x,p.x-1,p.x+1),Math.clamp(eye.y,p.y,p.y+2),Math.clamp(eye.z,p.z-1,p.z+1)))<8.8;}
    private Damage damage(Minecraft c,Player target,Vec3 center,double power,BlockPos removed,BlockPos virtualCover) throws ReflectiveOperationException {
        double self=BlastEstimates.damage(c,c.player,raw(c,center,c.player,power,removed,virtualCover,Vec3.ZERO))*1.1+.25;
        Vec3 predict=ChunkFinderClient.BOT.predictedPosition(2).subtract(target.position());
        double enemy=BlastEstimates.damage(c,target,raw(c,center,target,power,removed,virtualCover,predict));
        boolean safe=self<=config.botMaxSelfDamage&&self+2<c.player.getHealth()+c.player.getAbsorptionAmount();
        if(safe)for(Entity entity:c.level.entitiesForRendering())if(entity instanceof Player p&&p!=c.player&&p.isAlive()&&Friends.excludes(config,c.player,p,"bot")&&raw(c,center,p,power,removed,virtualCover,Vec3.ZERO)>1){safe=false;break;}
        return new Damage(enemy,self,safe);
    }
    private double raw(Minecraft c,Vec3 center,Entity entity,double power,BlockPos removed,BlockPos virtualCover,Vec3 prediction) throws ReflectiveOperationException {
        double radius=power*2,distance=entity.position().add(prediction).distanceTo(center);if(distance>=radius)return 0;
        double exposure;
        if(virtualCover==null&&prediction.lengthSqr()<.0001)exposure=access.exposure(c,center,entity,removed);
        else {
            AABB box=entity.getBoundingBox().move(prediction);int visible=0,total=0;
            for(double x:new double[]{.1,.5,.9})for(double y:new double[]{.05,.3,.55,.8,.95})for(double z:new double[]{.1,.5,.9}) {
                Vec3 sample=new Vec3(box.minX+box.getXsize()*x,box.minY+box.getYsize()*y,box.minZ+box.getZsize()*z);total++;
                if(virtualCover!=null&&new AABB(virtualCover).clip(sample,center).isPresent())continue;
                var hit=c.level.clip(new ClipContext(sample,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity));
                if(hit.getType()==HitResult.Type.MISS||removed!=null&&hit.getBlockPos().equals(removed))visible++;
            }exposure=(double)visible/total;
        }
        double impact=(1-distance/radius)*exposure;return impact<=0?0:(impact*impact+impact)/2*7*radius+1;
    }
}
