package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import java.util.*;

/** Passive, current-world observations. Never requests chunks, moves, places blocks or disconnects. */
public final class ElytraFinder {
    public static final String ID="elytra_finder";
    public record Target(int id,String kind,double x,double y,double z,double distance,double horizontal,double turn,double bearing,double vertical,String direction,String cardinal) {}
    public record NearbyPlayer(String name,double distance) {}
    private final FinderConfig config;
    private Object world;
    private Target target;
    private List<NearbyPlayer> players=List.of();
    private int candidates;
    public ElytraFinder(FinderConfig config){this.config=config;}
    public boolean enabled(){return config.isModuleEnabled(ID);}
    public void setEnabled(Minecraft c,boolean value){config.setModuleEnabled(ID,value);clear();tick(c);}
    private void clear(){target=null;players=List.of();candidates=0;world=null;}
    public Target target(){return target;}
    public List<NearbyPlayer> players(){return players;}
    public static String kind(Entity e){
        if(e==null||!e.isAlive()||e.isRemoved())return null;
        if(e instanceof ItemFrame frame&&frame.getItem().is(Items.ELYTRA))return "frame";
        if(e instanceof ItemEntity item&&item.getItem().is(Items.ELYTRA))return "dropped";
        return null;
    }
    public static Target locate(int id,String kind,double px,double py,double pz,double yaw,double x,double y,double z){
        double dx=x-px,dy=y-py,dz=z-pz,h=Math.hypot(dx,dz),bearing=normalize(Math.toDegrees(Math.atan2(-dx,dz))),turn=normalize(bearing-yaw);
        String direction=h<.75?"At your X/Z":Math.abs(turn)<=25?"Ahead":Math.abs(turn)>=155?"Behind":turn<0?"Left":"Right";
        String[] cardinals={"S","SW","W","NW","N","NE","E","SE"};
        String cardinal=cardinals[(int)Math.floor((bearing+360+22.5)/45)%8];
        return new Target(id,kind,x,y,z,Math.sqrt(dx*dx+dy*dy+dz*dz),h,turn,bearing,dy,direction,cardinal);
    }
    private static double normalize(double angle){return ((angle%360)+540)%360-180;}
    public void tick(Minecraft c){
        if(!enabled()||c.level==null||c.player==null||c.getConnection()==null){clear();return;}
        if(world!=c.level){clear();world=c.level;}
        Entity nearest=null;double best=(double)config.elytraRange*config.elytraRange;candidates=0;
        var nearby=new ArrayList<NearbyPlayer>();
        for(Entity e:c.level.entitiesForRendering()){
            if(config.elytraWarnPlayers&&e instanceof Player p&&p!=c.player&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()){
                double distance=p.distanceTo(c.player);if(distance<=config.elytraPlayerRange)nearby.add(new NearbyPlayer(p.getName().getString(),distance));
            }
            String kind=kind(e);if(kind==null||e.getY()<c.level.getMinY())continue;
            double d=e.distanceToSqr(c.player);if(d>(double)config.elytraRange*config.elytraRange)continue;
            candidates++;
            if(nearest==null||d<best||d==best&&e.getId()<nearest.getId()){nearest=e;best=d;}
        }
        nearby.sort(Comparator.comparingDouble(NearbyPlayer::distance).thenComparing(NearbyPlayer::name));players=List.copyOf(nearby);
        target=nearest==null?null:locate(nearest.getId(),kind(nearest),c.player.getX(),c.player.getY(),c.player.getZ(),c.player.getYRot(),nearest.getX(),nearest.getY(),nearest.getZ());
    }
    public String status(){return !enabled()?"Off":target==null?"No Elytra received in range · explore to receive more entities":"Nearest "+(target.kind.equals("frame")?"framed":"dropped")+" Elytra · "+Math.round(target.distance)+"m · "+target.direction+" / "+target.cardinal;}
    public Map<String,Object> state(){
        var out=new LinkedHashMap<String,Object>();out.put("enabled",enabled());out.put("status",status());out.put("candidates",candidates);out.put("target",target);out.put("players",players);out.put("playerCount",players.size());out.put("warning",enabled()&&!players.isEmpty());out.put("receivedOnly",true);return out;
    }
}
