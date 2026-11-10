package dev.amethyst.finder.modules.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded ground path search. World-space steering is independent of the current interaction yaw. */
public final class BotNavigation {
    public record Step(Vec3 direction,boolean jump){}
    private record Node(BlockPos pos,double cost,double estimate,Node parent){}
    private final ArrayDeque<BlockPos> path=new ArrayDeque<>();
    private Vec3 previousGoal;
    private int clock,planned;
    public void reset(){path.clear();previousGoal=null;clock=planned=0;}
    public Step toward(Minecraft c,Vec3 goal) {
        clock++;BlockPos feet=c.player.blockPosition();
        while(!path.isEmpty()&&Vec3.atBottomCenterOf(path.peekFirst()).multiply(1,0,1).distanceToSqr(c.player.position().multiply(1,0,1))<.23&&Math.abs(path.peekFirst().getY()-c.player.getY())<.7)path.removeFirst();
        if(path.isEmpty()||clock-planned>=8||previousGoal==null||previousGoal.distanceToSqr(goal)>2||!standing(c,path.peekFirst()))search(c,feet,goal);
        if(path.isEmpty())return new Step(Vec3.ZERO,false);
        Vec3 next=Vec3.atBottomCenterOf(path.peekFirst()),delta=next.subtract(c.player.position());
        return new Step(delta.multiply(1,0,1).normalize(),next.y>c.player.getY()+.35&&c.player.onGround());
    }
    private void search(Minecraft c,BlockPos start,Vec3 goal) {
        path.clear();previousGoal=goal;planned=clock;
        var open=new PriorityQueue<Node>(Comparator.comparingDouble(n->n.cost+n.estimate));var costs=new HashMap<BlockPos,Double>();
        Node first=new Node(start,0,estimate(start,goal),null),best=first;open.add(first);costs.put(start,0d);
        for(int visited=0;visited<180&&!open.isEmpty();visited++) {
            Node node=open.remove();if(node.cost>costs.getOrDefault(node.pos,Double.MAX_VALUE))continue;
            if(node.estimate<best.estimate)best=node;if(node.estimate<.65){best=node;break;}
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                if(dx==0&&dz==0)continue;BlockPos next=neighbor(c,node.pos,dx,dz);if(next==null)continue;
                if(dx!=0&&dz!=0&&(neighbor(c,node.pos,dx,0)==null||neighbor(c,node.pos,0,dz)==null))continue;
                double cost=node.cost+(dx!=0&&dz!=0?1.42:1)+Math.abs(next.getY()-node.pos.getY())*.4;
                if(cost>=costs.getOrDefault(next,Double.MAX_VALUE))continue;
                costs.put(next,cost);open.add(new Node(next,cost,estimate(next,goal),node));
            }
        }
        var reverse=new ArrayDeque<BlockPos>();for(Node n=best;n.parent!=null;n=n.parent)reverse.addFirst(n.pos);path.addAll(reverse);
    }
    private double estimate(BlockPos pos,Vec3 goal){return Math.hypot(pos.getX()+.5-goal.x,pos.getZ()+.5-goal.z)+Math.abs(pos.getY()-goal.y)*1.3;}
    private BlockPos neighbor(Minecraft c,BlockPos from,int dx,int dz) {
        BlockPos next=from.offset(dx,0,dz);
        for(int dy:new int[]{0,1,-1,-2,-3,-4,-5,-6}){BlockPos pos=next.offset(0,dy,0);if(!standing(c,pos))continue;
            if(dy<0){
                boolean clear=true;for(int y=pos.getY()+2;y<=from.getY()+1;y++)if(!c.level.getBlockState(new BlockPos(pos.getX(),y,pos.getZ())).getCollisionShape(c.level,new BlockPos(pos.getX(),y,pos.getZ())).isEmpty()){clear=false;break;}
                if(!clear||Math.max(0,-dy-3)+4>=c.player.getHealth()+c.player.getAbsorptionAmount())continue;
            }
            return pos;}
        return null;
    }
    public static boolean standing(Minecraft c,BlockPos pos) {
        if(!c.level.hasChunkAt(pos)||!c.level.hasChunkAt(pos.below()))return false;
        var feet=c.level.getBlockState(pos);var head=c.level.getBlockState(pos.above());var floor=c.level.getBlockState(pos.below());
        return feet.getCollisionShape(c.level,pos).isEmpty()&&head.getCollisionShape(c.level,pos.above()).isEmpty()
            &&!floor.getCollisionShape(c.level,pos.below()).isEmpty()&&c.level.getFluidState(pos).isEmpty()&&c.level.getFluidState(pos.below()).isEmpty()
            &&!feet.is(Blocks.FIRE)&&!feet.is(Blocks.SOUL_FIRE)&&!floor.is(Blocks.MAGMA_BLOCK)&&!floor.is(Blocks.CAMPFIRE);
    }
}
