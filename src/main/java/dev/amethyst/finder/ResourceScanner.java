package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;
import dev.amethyst.finder.core.MineralType;
import dev.amethyst.finder.core.ExactSelectionCache;
import net.minecraft.world.phys.Vec3;

/** Budgeted scans of already loaded chunks. Never requests a chunk from the server. */
public final class ResourceScanner {
    private static final int MINERAL_COUNT=MineralType.values().length;
    public enum Kind {
        DIAMOND("diamond_esp", "Diamonds", 0xFF5FE7EE),
        ORE("ore_esp", "Other ores", 0xFFBDA1FF),
        LAVA("lava_esp", "Lava", 0xDFFF8C47);
        public final String module, label; public final int color;
        Kind(String module, String label, int color) { this.module=module;this.label=label;this.color=color; }
    }
    public record Mark(BlockPos position, Kind kind, MineralType mineral) {
        public int color() { return mineral==null?kind.color:mineral.color; }
    }
    private final FinderConfig config;
    private ClientLevel world;
    private final LinkedHashMap<Long, List<Mark>> cache = new LinkedHashMap<>();
    private final Map<Long, Long> scanned = new HashMap<>();
    private final ArrayDeque<LevelChunk> queue = new ArrayDeque<>();
    private Job current;
    private int mask, ticks;
    private long revision;
    private record Selection(long revision, Vec3 position, int mask, int range, int limit, boolean exposedOnly) {}
    private final ExactSelectionCache<Selection,List<Mark>> selection = new ExactSelectionCache<>();
    public ResourceScanner(FinderConfig config) { this.config=config; }
    public static Kind kind(BlockState s) {
        if(s==null)return null;
        if(s.is(Blocks.LAVA))return Kind.LAVA;
        var mineral=mineral(s);return mineral==null?null:mineral==MineralType.DIAMOND?Kind.DIAMOND:Kind.ORE;
    }
    public static MineralType mineral(BlockState s) {
        if(s==null)return null;
        if(s.is(Blocks.DIAMOND_ORE)||s.is(Blocks.DEEPSLATE_DIAMOND_ORE))return MineralType.DIAMOND;
        if(s.is(Blocks.EMERALD_ORE)||s.is(Blocks.DEEPSLATE_EMERALD_ORE))return MineralType.EMERALD;
        if(s.is(Blocks.LAPIS_ORE)||s.is(Blocks.DEEPSLATE_LAPIS_ORE))return MineralType.LAPIS;
        if(s.is(Blocks.IRON_ORE)||s.is(Blocks.DEEPSLATE_IRON_ORE))return MineralType.IRON;
        if(s.is(Blocks.GOLD_ORE)||s.is(Blocks.DEEPSLATE_GOLD_ORE)||s.is(Blocks.NETHER_GOLD_ORE))return MineralType.GOLD;
        if(s.is(Blocks.COPPER_ORE)||s.is(Blocks.DEEPSLATE_COPPER_ORE))return MineralType.COPPER;
        if(s.is(Blocks.REDSTONE_ORE)||s.is(Blocks.DEEPSLATE_REDSTONE_ORE))return MineralType.REDSTONE;
        if(s.is(Blocks.COAL_ORE)||s.is(Blocks.DEEPSLATE_COAL_ORE))return MineralType.COAL;
        if(s.is(Blocks.NETHER_QUARTZ_ORE))return MineralType.QUARTZ;
        if(s.is(Blocks.ANCIENT_DEBRIS))return MineralType.ANCIENT_DEBRIS;
        if(s.is(Blocks.BUDDING_AMETHYST)||s.is(Blocks.AMETHYST_CLUSTER)||s.is(Blocks.LARGE_AMETHYST_BUD)
                ||s.is(Blocks.MEDIUM_AMETHYST_BUD)||s.is(Blocks.SMALL_AMETHYST_BUD))return MineralType.AMETHYST;
        if(s.is(Blocks.SULFUR)||s.is(Blocks.POTENT_SULFUR)||s.is(Blocks.SULFUR_SPIKE))return MineralType.SULFUR;
        return null;
    }
    private boolean enabled(Kind k) { return k != null && config.isModuleEnabled(k.module); }
    private boolean enabled(BlockState state) {var m=mineral(state);return m!=null?config.mineralEnabled(m):state.is(Blocks.LAVA)&&enabled(Kind.LAVA);}
    private boolean enabled(Mark mark) {return mark.mineral==null?enabled(mark.kind):config.mineralEnabled(mark.mineral);}
    private int mask() {int value=0;for(var m:MineralType.values())if(config.mineralEnabled(m))value|=1<<m.ordinal();if(enabled(Kind.LAVA))value|=1<<MINERAL_COUNT;return value;}
    public void invalidate(BlockPos position) {
        revision++;
        long key=key(position.getX()>>4,position.getZ()>>4);scanned.remove(key);
        // Flowing fluids must not repeatedly discard a partially scanned chunk.
        // Rendering rechecks each cached block against the current received state.
        if(current!=null&&key==current.key)current.dirty=true;
    }
    public void invalidateChunk(LevelChunk chunk) {
        revision++;
        long key=key(chunk.getPos().x(),chunk.getPos().z());scanned.remove(key);
        if(current!=null&&key==current.key)current.dirty=true;
    }
    public void invalidateVisibility() { revision++; }
    public void tick(Minecraft client) {
        if(world!=client.level || mask()!=mask) {world=client.level;mask=mask();cache.clear();scanned.clear();queue.clear();current=null;ticks=0;revision++;selection.clear();}
        if(world==null||client.player==null||mask==0)return;
        if(ticks++%20==0) {
            int cx=client.player.chunkPosition().x(),cz=client.player.chunkPosition().z(),radius=(config.resourceRange+15)/16;
            List<LevelChunk> chunks=new ArrayList<>();Set<Long> alive=new HashSet<>();
            for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++) {
                if((x-cx)*(x-cx)+(z-cz)*(z-cz)>(radius+1)*(radius+1))continue;
                var candidate=world.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
                if(candidate instanceof LevelChunk chunk) {long key=key(x,z);alive.add(key);
                    if(ticks-scanned.getOrDefault(key,-1000L)>100)chunks.add(chunk);}
            }
            if(cache.keySet().retainAll(alive))revision++;scanned.keySet().retainAll(alive);
            chunks.sort(Comparator.comparingInt(c->{int dx=c.getPos().x()-cx,dz=c.getPos().z()-cz;return dx*dx+dz*dz;}));
            queue.clear();queue.addAll(chunks);
        }
        long deadline=System.nanoTime()+1_300_000L;
        while(System.nanoTime()<deadline) {
            if(current==null) {LevelChunk next=queue.poll();if(next==null)break;current=new Job(next);}
            var loaded=world.getChunkSource().getChunk(current.chunk.getPos().x(),current.chunk.getPos().z(),ChunkStatus.FULL,false);
            if(loaded!=current.chunk) {cache.remove(current.key);revision++;scanned.remove(current.key);current=null;continue;}
            if(current.step(deadline)) {cache.put(current.key,List.copyOf(current.marks));revision++;if(current.dirty)scanned.remove(current.key);else scanned.put(current.key,(long)ticks);current=null;}
        }
    }
    public List<Mark> marks(Minecraft client) {
        if(client.level!=world||client.player==null||mask==0)return List.of();
        Vec3 position=client.player.position();
        double range=(double)config.resourceRange*config.resourceRange;
        Selection key=new Selection(revision,position,mask(),config.resourceRange,config.resourceLimit,config.resourceExposedOnly);
        return selection.get(key,()->cache.values().stream().flatMap(Collection::stream).filter(m->enabled(m)
                &&m.position.distToCenterSqr(position)<=range
                &&(!config.resourceExposedOnly||m.kind==Kind.LAVA||exposed(m.position)))
                .sorted(Comparator.comparingDouble(m->m.position.distToCenterSqr(position)))
                .limit(config.resourceLimit).toList());
    }
    private boolean exposed(BlockPos pos){
        for(var direction:net.minecraft.core.Direction.values()){
            var neighbor=world.getBlockState(pos.relative(direction));
            if(neighbor.isAir()||!neighbor.canOcclude())return true;
        }
        return false;
    }
    public String status() {return mask==0?"Highlights off":cache.size()+" loaded chunks · "+config.resourceRange+" blocks";}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private final class Job {
        final LevelChunk chunk;final long key;final List<Mark> marks=new ArrayList<>();final int[] counts=new int[MINERAL_COUNT+1];
        int section,index;boolean dirty;
        Job(LevelChunk chunk){this.chunk=chunk;key=key(chunk.getPos().x(),chunk.getPos().z());}
        boolean step(long deadline) {
            var sections=chunk.getSections();int operations=0;
            while(section<sections.length) {
                var s=sections[section];
                if(index==0&&(s.hasOnlyAir()||!s.maybeHas(ResourceScanner.this::enabled))) {section++;continue;}
                while(index<4096) {
                    int i=index++,x=i&15,z=(i>>4)&15,y=(i>>8)&15;var state=s.getBlockState(x,y,z);
                    var mineral=mineral(state);var kind=mineral==null?(state.is(Blocks.LAVA)?Kind.LAVA:null):mineral==MineralType.DIAMOND?Kind.DIAMOND:Kind.ORE;
                    int bucket=mineral==null?MINERAL_COUNT:mineral.ordinal();
                    if((mineral!=null?config.mineralEnabled(mineral):enabled(kind))&&counts[bucket]<(mineral==MineralType.DIAMOND?48:kind==Kind.LAVA?32:16)) {
                        BlockPos pos=new BlockPos(chunk.getPos().getMinBlockX()+x,world.getMinY()+section*16+y,chunk.getPos().getMinBlockZ()+z);
                        // Keep the outer shell of lava, avoiding thousands of interior ocean markers.
                        if(kind!=Kind.LAVA||!world.getBlockState(pos.above()).is(Blocks.LAVA)) {marks.add(new Mark(pos,kind,mineral));counts[bucket]++;}
                    }
                    if((++operations&63)==0&&System.nanoTime()>=deadline)return false;
                }
                index=0;section++;
            }
            return true;
        }
    }
}
