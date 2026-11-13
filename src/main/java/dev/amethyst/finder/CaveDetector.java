package dev.amethyst.finder;

import dev.amethyst.finder.core.CaveScan;
import dev.amethyst.finder.core.ChunkEvidence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Incremental complete scans of received terrain; never requests or generates chunks. */
public final class CaveDetector {
    public record Result(long key,int x,int z,LevelChunk source,boolean clear) {}
    private final FinderConfig config;
    private ClientLevel world;
    private boolean enabled;
    private int ticks,range;
    private Job current;
    private final Map<Long,Result> results=new LinkedHashMap<>();
    private final Deque<LevelChunk> queue=new ArrayDeque<>();
    private final Set<Long> queued=new HashSet<>();
    public CaveDetector(FinderConfig config) { this.config=config; }
    public void invalidate(LevelChunk chunk) {
        long key=key(chunk);
        results.remove(key);
        if(current!=null&&current.key==key)current=null;
    }
    private static long key(LevelChunk chunk) {return ChunkEvidence.key(chunk.getPos().x(),chunk.getPos().z());}
    private boolean loaded(LevelChunk chunk) {
        return world!=null&&world.getChunkSource().getChunk(chunk.getPos().x(),chunk.getPos().z(),ChunkStatus.FULL,false)==chunk;
    }
    public void tick(Minecraft client) {
        boolean active=config.isModuleEnabled("cave_detector");
        if(world!=client.level||enabled!=active||range!=config.chunkRangeBlocks) {
            world=client.level;enabled=active;range=config.chunkRangeBlocks;ticks=0;
            results.clear();queue.clear();queued.clear();current=null;
        }
        if(!active||world==null||client.player==null)return;
        if(ticks++%20==0) {
            int cx=client.player.chunkPosition().x(),cz=client.player.chunkPosition().z(),radius=Math.min(32,(range+15)/16);
            Set<Long> alive=new HashSet<>();List<LevelChunk> pending=new ArrayList<>();
            for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++) {
                if((x-cx)*(x-cx)+(z-cz)*(z-cz)>(radius+1)*(radius+1))continue;
                var value=world.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
                if(!(value instanceof LevelChunk chunk))continue;
                long key=key(chunk);alive.add(key);
                Result old=results.get(key);if(old!=null&&old.source()!=chunk)results.remove(key);
                if(!results.containsKey(key)&&!queued.contains(key)&&(current==null||current.key!=key))pending.add(chunk);
            }
            results.keySet().retainAll(alive);
            queue.removeIf(chunk->!alive.contains(key(chunk))||!loaded(chunk));
            queued.clear();for(var chunk:queue)queued.add(key(chunk));
            if(current!=null&&!alive.contains(current.key))current=null;
            pending.sort(Comparator.comparingInt(c->{int dx=c.getPos().x()-cx,dz=c.getPos().z()-cz;return dx*dx+dz*dz;}));
            for(var chunk:pending)if(queued.add(key(chunk)))queue.add(chunk);
        }
        long deadline=System.nanoTime()+1_000_000L;
        while(System.nanoTime()<deadline) {
            if(current==null) {
                LevelChunk chunk=queue.poll();if(chunk==null)break;
                queued.remove(key(chunk));if(!loaded(chunk))continue;
                current=new Job(chunk);
            }
            if(!loaded(current.chunk)) {results.remove(current.key);current=null;continue;}
            if(current.step()) {
                results.put(current.key,new Result(current.key,current.chunk.getPos().x(),current.chunk.getPos().z(),current.chunk,current.scan.clear()));
                current=null;
            }
        }
    }
    public List<Result> clearChunks(Minecraft client) {
        if(!enabled||!config.isModuleEnabled("cave_detector")||world!=client.level)return List.of();
        return results.values().stream().filter(r->r.clear()&&loaded(r.source())).toList();
    }
    public String status() {
        if(!config.isModuleEnabled("cave_detector"))return "Off · green marks require a complete underground scan";
        long clear=results.values().stream().filter(r->r.clear()&&loaded(r.source())).count();
        return clear+" no cavities detected · "+results.size()+" checked · "+(queue.size()+(current==null?0:1))+" pending · received terrain only";
    }
    private final class Job {
        final LevelChunk chunk;final long key;final CaveScan scan;
        final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        Job(LevelChunk chunk) {
            this.chunk=chunk;key=key(chunk);
            scan=new CaveScan(world.getMinY(),world.getMaxY(),new CaveScan.Terrain() {
                public int sample(int x,int y,int z) {
                    pos.set(chunk.getPos().x()*16+x,y,chunk.getPos().z()*16+z);
                    var state=chunk.getBlockState(pos);
                    if(state.isAir()||state.is(Blocks.WATER)||state.is(Blocks.LAVA))return CaveScan.CAVITY;
                    return state.canOcclude()?CaveScan.SOLID:CaveScan.OTHER;
                }
            });
        }
        boolean step() {return scan.step(256);}
    }
}
