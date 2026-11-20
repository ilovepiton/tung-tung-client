package dev.amethyst.finder.builder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import dev.amethyst.finder.modules.pvp.BotNavigation;
import java.util.*;

/** Builder walks on received ground only; no parkour, digging, teleports or long drops. */
final class BuilderNavigation {
    private record Node(BlockPos p,double cost,double score,Node parent){}
    private List<BlockPos> path=List.of();private BlockPos goal;private int age;
    void reset(){path=List.of();goal=null;age=0;}
    Vec3 direction(Minecraft c,BlockPos target) {
        if(!target.equals(goal)||age++%10==0||path.isEmpty())search(c,target);
        for(BlockPos p:path)if(c.player.position().distanceToSqr(Vec3.atBottomCenterOf(p))>.3)return Vec3.atBottomCenterOf(p).subtract(c.player.position());
        return Vec3.ZERO;
    }
    private void search(Minecraft c,BlockPos target) {
        goal=target;BlockPos start=c.player.blockPosition();
        var open=new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));var costs=new HashMap<BlockPos,Double>();
        Node first=new Node(start,0,distance(start,target),null),best=first;open.add(first);costs.put(start,0d);
        for(int visited=0;visited<512&&!open.isEmpty();visited++) {
            Node n=open.remove();if(n.cost>costs.getOrDefault(n.p,Double.MAX_VALUE))continue;
            if(distance(n.p,target)<distance(best.p,target))best=n;if(n.p.equals(target)){best=n;break;}
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,1,-1}) {
                BlockPos p=n.p.offset(d[0],dy,d[1]);
                if(Math.abs(p.getX()-start.getX())>24||Math.abs(p.getZ()-start.getZ())>24||!BotNavigation.standing(c,p))continue;
                if(dy==1&&!c.level.getBlockState(n.p.above(2)).getCollisionShape(c.level,n.p.above(2)).isEmpty())continue;
                double cost=n.cost+1+Math.abs(dy)*.4;if(cost>=costs.getOrDefault(p,Double.MAX_VALUE))break;
                costs.put(p,cost);open.add(new Node(p,cost,cost+distance(p,target),n));break;
            }
        }
        var reverse=new ArrayList<BlockPos>();for(Node n=best;n.parent!=null;n=n.parent)reverse.add(n.p);Collections.reverse(reverse);path=List.copyOf(reverse);
    }
    private static double distance(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY())*1.4;}
}
