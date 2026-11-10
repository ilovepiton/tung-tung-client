package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.*;

/** Current received drops only. Pickups, despawns, world changes and selection clear live markers. */
public final class ItemFinder {
    public static final String ID="item_finder";
    public record Mark(int entityId,String itemId,String name,int count,double x,double y,double z,double distance,int chunkX,int chunkZ,int color) {}
    public record Chunk(int x,int z,int stacks,int items) {}
    private final FinderConfig config;
    private Object world;
    private List<Integer> entities=List.of();
    private int ticks,selectionHash;
    public ItemFinder(FinderConfig config) {this.config=config;}
    public boolean enabled() {return config.isModuleEnabled(ID);}
    public void tick(Minecraft client) {
        if(world!=client.level||!enabled()||client.player==null) {world=client.level;entities=List.of();ticks=0;}
        if(!enabled()||client.level==null||client.player==null)return;
        int hash=Objects.hash(config.itemFinderRange,config.itemFinderLimit,config.itemFinderTargets);
        if(ticks++%5!=0&&hash==selectionHash)return;
        selectionHash=hash;
        var nearest=new PriorityQueue<ItemEntity>(Comparator.<ItemEntity>comparingDouble(item->item.distanceToSqr(client.player)).thenComparingInt(ItemEntity::getId).reversed());
        double range=(double)config.itemFinderRange*config.itemFinderRange;
        for(var entity:client.level.entitiesForRendering()) {
            if(!(entity instanceof ItemEntity item)||!selected(item)||item.distanceToSqr(client.player)>range)continue;
            nearest.add(item);if(nearest.size()>config.itemFinderLimit)nearest.poll();
        }
        entities=nearest.stream().sorted(Comparator.<ItemEntity>comparingDouble(item->item.distanceToSqr(client.player)).thenComparingInt(ItemEntity::getId)).map(ItemEntity::getId).toList();
    }
    private boolean selected(ItemEntity item) {
        var entry=ValuableItems.match(item.getItem());
        return item.isAlive()&&!item.isRemoved()&&entry!=null
            &&(config.itemFinderTargets==null||!Boolean.FALSE.equals(config.itemFinderTargets.get(entry.id())));
    }
    public List<Mark> marks(Minecraft client) {
        if(!enabled()||client.level==null||client.level!=world||client.player==null)return List.of();
        var result=new ArrayList<Mark>();
        for(int id:entities) {
            if(!(client.level.getEntity(id) instanceof ItemEntity item)||!selected(item))continue;
            double distance=item.distanceTo(client.player);if(distance>config.itemFinderRange)continue;
            var entry=ValuableItems.match(item.getItem());var chunk=item.chunkPosition();
            String name=item.getItem().getHoverName().getString().replace('\n',' ').replace('\r',' ');
            if(name.length()>96)name=name.substring(0,96);
            result.add(new Mark(id,entry.id(),name,item.getItem().getCount(),item.getX(),item.getY(),item.getZ(),distance,chunk.x(),chunk.z(),entry.color()));
        }
        return List.copyOf(result);
    }
    public List<Chunk> chunks(Minecraft client) {
        var chunks=new LinkedHashMap<Long,int[]>();
        for(var mark:marks(client)) {
            long key=((long)mark.chunkX<<32)|(mark.chunkZ&0xffffffffL);
            int[] counts=chunks.computeIfAbsent(key,unused->new int[]{mark.chunkX,mark.chunkZ,0,0});counts[2]++;counts[3]+=mark.count;
        }
        return chunks.values().stream().limit(32).map(c->new Chunk(c[0],c[1],c[2],c[3])).toList();
    }
    public String status(Minecraft client) {
        return !enabled()?"Off":marks(client).size()+" dropped stacks · "+chunks(client).size()+" current chunks";
    }
    public Map<String,Object> state(Minecraft client) {
        return Map.of("enabled",enabled(),"status",status(client),"marks",marks(client),"chunks",chunks(client),"catalog",ValuableItems.ENTRIES,"receivedOnly",true);
    }
}
