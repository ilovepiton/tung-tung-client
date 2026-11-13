package dev.amethyst.finder.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.core.ChunkAnalysis;
import dev.amethyst.finder.core.ChunkEvidence;
import dev.amethyst.finder.core.HighlightProjection;
import dev.amethyst.finder.modules.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.*;

/** Full world-height chunk prisms, projected from double precision world coordinates into a bounded GUI mesh. */
public final class ChunkWorldRenderer {
    private static final int MAX_CHUNKS=64,MAX_FADE_KEYS=512,MAX_QUADS=16384;
    private static final int[][] EDGES={{0,1},{1,3},{3,2},{2,0},{4,5},{5,7},{7,6},{6,4},{0,4},{1,5},{2,6},{3,7}};
    private static final int[][] FACES={{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}};
    private static final LinkedHashMap<FadeKey,FadeState> fades=new LinkedHashMap<>();
    private static Object level;
    private static long revision=Long.MIN_VALUE,nearestAt,scopeGeneration=Long.MIN_VALUE;
    private static int lastCellX,lastCellZ,lastFlags,lastRange,lastLimit;
    private static List<Candidate> nearest=List.of();
    private static Long lastOverride;
    private static int renderedCount;
    private ChunkWorldRenderer() {}

    public static int renderedCount() { return renderedCount; }
    public static void reset() {
        level=null;revision=Long.MIN_VALUE;scopeGeneration=Long.MIN_VALUE;lastOverride=null;nearest=List.of();fades.clear();renderedCount=0;ChunkSelection.reset();
    }
    public static void draw(GuiGraphicsExtractor graphics,Minecraft client,Camera camera) {
        FinderConfig config=ChunkFinderClient.CONFIG;
        if(client.level==null||client.player==null) { reset();return; }
        long currentScope=ChunkFinderClient.SCANNER.scopeGeneration();
        if(level!=client.level||scopeGeneration!=currentScope) { reset();level=client.level;scopeGeneration=currentScope; }
        long now=System.nanoTime()/1_000_000L;
        Vec3 eye=camera.position();
        int cellX=(int)Math.floor(eye.x/16),cellZ=(int)Math.floor(eye.z/16);
        int flags=flags(config),range=config.chunkRangeBlocks,limit=Math.min(MAX_CHUNKS,config.chunkLimit);
        long currentRevision=ChunkFinderClient.SCANNER.revision();
        Long pin=ChunkSelection.overrideKey();
        if(!Objects.equals(lastOverride,pin)||currentRevision!=revision||flags!=lastFlags||cellX!=lastCellX||cellZ!=lastCellZ||range!=lastRange||limit!=lastLimit||now-nearestAt>250) {
            nearest=findNearest(config,eye,limit,pin);lastOverride=pin;
            revision=currentRevision;lastFlags=flags;lastCellX=cellX;lastCellZ=cellZ;lastRange=range;lastLimit=limit;nearestAt=now;
        }
        Map<Long,Candidate> candidates=new LinkedHashMap<>();
        for(Candidate candidate:nearest) candidates.put(candidate.key(),candidate.copy());
        if(config.chunkModuleEnabled("player_chunks")) {
            var currentPlayers=currentPlayers(client,range);
            for(var entry:currentPlayers.entrySet()) {
                long key=entry.getKey();int x=(int)(key>>32),z=(int)key;
                Candidate candidate=candidates.computeIfAbsent(key,k->new Candidate(x,z,null));
                candidate.players=entry.getValue();candidate.categories.add(ChunkSelection.Category.PLAYER);
            }
        }
        List<Candidate> ordered=new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingDouble(c->priorityDistance(c,eye,pin)));
        if(ordered.size()>limit) ordered=new ArrayList<>(ordered.subList(0,limit));
        int minY=client.level.getMinY(),maxY=ChunkSelectionMath.exclusiveWorldMaxY(client.level.getMaxY());
        select(ordered,client,camera,minY,maxY);
        Mesh mesh=new Mesh(camera.getViewRotationProjectionMatrix(new Matrix4f()),eye,graphics.guiWidth(),graphics.guiHeight());
        Set<FadeKey> present=new HashSet<>();
        Long selected=ChunkSelection.selectedKey();
        renderedCount=ordered.size();
        double fillScale=Math.max(.3,1-Math.max(0,ordered.size()-8)/80.0);
        for(Candidate candidate:ordered) {
            boolean chosen=selected!=null&&selected==candidate.key();
            List<ChunkSelection.Category> categories=new ArrayList<>(candidate.categories);
            categories.sort(Comparator.comparingInt(Enum::ordinal));
            for(int band=0;band<categories.size();band++) {
                var category=categories.get(band);
                FadeKey key=new FadeKey(candidate.key(),category);
                present.add(key);
                FadeState fade=fades.computeIfAbsent(key,k->new FadeState(candidate,category,now));
                fade.candidate=candidate;fade.lastSeen=now;
                double opacity=ChunkSelectionMath.fade(now-fade.appeared,220);
                boolean historical=category!=ChunkSelection.Category.PLAYER && candidate.analysis!=null&&candidate.analysis.status()!=ChunkAnalysis.Status.LIVE;
                double pulse=1;
                if(category==ChunkSelection.Category.CHEST&&candidate.analysis!=null)pulse+=Math.min(.22,Math.log1p(candidate.analysis.storageCount())/16);
                if(category==ChunkSelection.Category.AMETHYST&&config.chunkModuleEnabled("amethyst_growth")&&candidate.analysis!=null&&candidate.analysis.growthEvents()>0) {
                    long since=Math.max(0,System.currentTimeMillis()-candidate.analysis.lastGrowthAtMillis());
                    pulse=1.15+.12*Math.sin(now*.004)*Math.max(0,1-since/15_000.0);
                }
                mesh.chunk(ChunkSelectionMath.Bounds.chunk(candidate.x,candidate.z,minY,maxY),category.color,opacity,pulse,chosen,historical,
                        band*2.2,fillScale/categories.size());
            }
        }
        Iterator<Map.Entry<FadeKey,FadeState>> iterator=fades.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(present.contains(entry.getKey())) continue;
            FadeState fade=entry.getValue();
            long elapsed=now-fade.lastSeen;
            // Player locations cease to be current immediately; there is no stale-position fade or history trail.
            if(entry.getKey().category==ChunkSelection.Category.PLAYER||elapsed>=180||fades.size()>MAX_FADE_KEYS) { iterator.remove();continue; }
            double opacity=(1-ChunkSelectionMath.fade(elapsed,180))*.5;
            mesh.chunk(ChunkSelectionMath.Bounds.chunk(fade.candidate.x,fade.candidate.z,minY,maxY),fade.category.color,opacity,1,false,true,0,0);
        }
        if(config.itemFinderChunks&&ChunkFinderClient.ITEMS!=null) {
            for(var chunk:ChunkFinderClient.ITEMS.chunks(client)) {
                if(!chunkLoaded(client,chunk.x(),chunk.z()))continue;
                mesh.chunk(ChunkSelectionMath.Bounds.chunk(chunk.x(),chunk.z(),minY,maxY),0xFFFFC875,.85,1,false,false,0,.35);
            }
        }
        mesh.submit(graphics);

    }
    public static void drawHud(GuiGraphicsExtractor graphics,Minecraft client) {
        if(ChunkFinderClient.CONFIG.chunkHud&&(flags(ChunkFinderClient.CONFIG)&127)!=0)ChunkHud.draw(graphics,client,ChunkSelection.selected(),renderedCount,Math.min(MAX_CHUNKS,ChunkFinderClient.CONFIG.chunkLimit));
    }
    /** ClientLevel.hasChunk() is not an availability test in 26.3; false prevents creating/loading a chunk. */
    public static boolean chunkLoaded(Minecraft client,int x,int z) {
        return client!=null&&client.level!=null&&client.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)!=null;
    }
    public static Map<Long,Integer> currentPlayers(Minecraft client,int range) {
        if(client==null||client.level==null||client.player==null)return Map.of();
        List<ChunkVisualRules.ObservedPlayer> received=new ArrayList<>();
        for(var player:client.level.players()) {
            if(player==client.player)continue;
            var chunk=player.chunkPosition();
            boolean observable=player.isAlive()&&!player.isRemoved()&&chunkLoaded(client,chunk.x(),chunk.z());
            received.add(new ChunkVisualRules.ObservedPlayer(player.getId(),player.getX(),player.getY(),player.getZ(),observable));
        }
        return ChunkVisualRules.currentPlayerChunks(received,client.player.getX(),client.player.getY(),client.player.getZ(),range);
    }
    private static List<Candidate> findNearest(FinderConfig config,Vec3 eye,int limit,Long pin) {
        PriorityQueue<Candidate> queue=new PriorityQueue<>(Comparator.comparingDouble((Candidate c)->priorityDistance(c,eye,pin)).reversed());
        double range2=config.chunkRangeBlocks*(double)config.chunkRangeBlocks;
        var enabled=EnumSet.noneOf(ChunkSelection.Category.class);
        for(var category:ChunkSelection.Category.values())if(config.chunkModuleEnabled(category.moduleId))enabled.add(category);
        for(ChunkAnalysis analysis:ChunkFinderClient.SCANNER.chunkObservations()) {
            Candidate candidate=new Candidate(analysis.chunkX(),analysis.chunkZ(),analysis);
            if(distanceSquared(candidate,eye)>range2) continue;
            candidate.categories.addAll(ChunkVisualRules.categories(analysis,enabled,config.showHistoricalChunks,config.chunkModuleEnabled("amethyst_growth"),config.searchChunkMinClusters));
            if(candidate.categories.isEmpty()) continue;
            queue.add(candidate);if(queue.size()>limit)queue.poll();
        }
        List<Candidate> list=new ArrayList<>(queue);list.sort(Comparator.comparingDouble(c->priorityDistance(c,eye,pin)));return List.copyOf(list);
    }
    private static int flags(FinderConfig config) {
        int mask=config.showHistoricalChunks?128:0;
        for(var category:ChunkSelection.Category.values()) if(config.chunkModuleEnabled(category.moduleId))mask|=1<<category.ordinal();
        return config.chunkModuleEnabled("amethyst_growth")?mask|64:mask;
    }
    private static double priorityDistance(Candidate c,Vec3 eye,Long pin) { return pin!=null&&pin==c.key() ? -1 : distanceSquared(c,eye); }
    private static double distanceSquared(Candidate c,Vec3 eye) {
        double dx=c.x*16.0+8-eye.x,dz=c.z*16.0+8-eye.z;return dx*dx+dz*dz;
    }
    private static void select(List<Candidate> candidates,Minecraft client,Camera camera,int minY,int maxY) {
        Long override=ChunkSelection.overrideKey();
        Candidate chosen=null;double best=Double.POSITIVE_INFINITY;
        var forward=camera.forwardVector();
        Vec3 eye=camera.position();
        for(Candidate candidate:candidates) {
            if(override!=null&&candidate.key()==override) { chosen=candidate;break; }
            if(override!=null) continue;
            double distance=ChunkSelectionMath.rayDistance(ChunkSelectionMath.Bounds.chunk(candidate.x,candidate.z,minY,maxY),
                    eye.x,eye.y,eye.z,forward.x(),forward.y(),forward.z());
            if(distance<best) { best=distance;chosen=candidate; }
        }
        if(chosen==null&&override!=null) for(ChunkAnalysis analysis:ChunkFinderClient.SCANNER.chunkObservations()) {
            if(analysis.key()!=override)continue;
            chosen=new Candidate(analysis.chunkX(),analysis.chunkZ(),analysis);
            if(analysis.storageCount()>0)chosen.categories.add(ChunkSelection.Category.CHEST);
            if(analysis.amethystCount()>0)chosen.categories.add(ChunkSelection.Category.AMETHYST);
            if(analysis.evidence()!=ChunkAnalysis.StructureEvidence.NONE)chosen.categories.add(ChunkSelection.Category.BASE);
            if(analysis.spawnerCount()>0)chosen.categories.add(ChunkSelection.Category.SPAWNER);
            break;
        }
        ChunkSelection.update(chosen==null?null:new ChunkSelection.Selected(chosen.key(),chosen.x,chosen.z,chosen.analysis,List.copyOf(chosen.categories),chosen.players,override!=null));
    }
    private static final class Candidate {
        final int x,z;final ChunkAnalysis analysis;final EnumSet<ChunkSelection.Category> categories=EnumSet.noneOf(ChunkSelection.Category.class);int players;
        Candidate(int x,int z,ChunkAnalysis analysis){this.x=x;this.z=z;this.analysis=analysis;}
        long key(){return ChunkEvidence.key(x,z);}
        Candidate copy(){Candidate c=new Candidate(x,z,analysis);c.categories.addAll(categories);c.players=players;return c;}
    }
    private record FadeKey(long key,ChunkSelection.Category category) {}
    private static final class FadeState {
        Candidate candidate;final ChunkSelection.Category category;final long appeared;long lastSeen;
        FadeState(Candidate candidate,ChunkSelection.Category category,long now){this.candidate=candidate;this.category=category;appeared=now;lastSeen=now;}
    }
    private record ScreenPoint(float x,float y) {}
    private record Quad(ScreenPoint a,ScreenPoint b,ScreenPoint c,ScreenPoint d,int color) {}

    private static final class Mesh {
        final Matrix4f matrix;final Vec3 eye;final int width,height;
        final List<Quad> fill=new ArrayList<>(),glow=new ArrayList<>(),edges=new ArrayList<>();int quads;
        Mesh(Matrix4f matrix,Vec3 eye,int width,int height){this.matrix=matrix;this.eye=eye;this.width=width;this.height=height;}
        void chunk(ChunkSelectionMath.Bounds b,int color,double opacity,double pulse,boolean selected,boolean historical,double band,double fillScale) {
            if(opacity<=.005||quads>=MAX_QUADS)return;
            Vector4f[] corners=new Vector4f[8];
            for(int i=0;i<8;i++)corners[i]=matrix.transform(new Vector4f((float)(((i&1)==0?b.minX():b.maxX())-eye.x),
                    (float)(((i&2)==0?b.minY():b.maxY())-eye.y),(float)(((i&4)==0?b.minZ():b.maxZ())-eye.z),1));
            if(!historical&&fillScale>0) for(int[] face:FACES) {
                List<HighlightProjection.Vertex> vertices=new ArrayList<>(4);
                for(int index:face){var v=corners[index];vertices.add(new HighlightProjection.Vertex(v.x,v.y,v.z,v.w,0,0));}
                var clipped=HighlightProjection.clip(vertices);
                if(clipped.size()<3)continue;
                int alpha=(int)((selected?9:4)*opacity*fillScale);
                if(alpha<1)continue;
                ScreenPoint first=screen(clipped.getFirst());
                for(int i=1;i<clipped.size()-1;i++)add(fill,new Quad(first,screen(clipped.get(i)),screen(clipped.get(i+1)),screen(clipped.get(i+1)),alpha(color,alpha)));
            }
            int glowAlpha=(int)((selected?55:30)*opacity*pulse),edgeAlpha=(int)((historical?100:selected?230:175)*opacity*pulse);
            for(int[] edge:EDGES) {
                Vector4f a=corners[edge[0]],c=corners[edge[1]];
                var clipped=ChunkSelectionMath.clipSegment(new ChunkSelectionMath.ClipPoint(a.x,a.y,a.w),new ChunkSelectionMath.ClipPoint(c.x,c.y,c.w));
                if(clipped.isEmpty())continue;
                ScreenPoint p=screen(clipped.get().a()),q=screen(clipped.get().b());
                double length=Math.hypot(q.x-p.x,q.y-p.y);
                if(length<.2)continue;
                double nx=-(q.y-p.y)/length,ny=(q.x-p.x)/length;
                p=new ScreenPoint((float)(p.x+nx*band),(float)(p.y+ny*band));q=new ScreenPoint((float)(q.x+nx*band),(float)(q.y+ny*band));
                if(historical) {
                    int pieces=Math.min(160,(int)Math.ceil(length/12));
                    for(int i=0;i<pieces;i++) {
                        double start=i/(double)pieces,end=Math.min(1,start+6/length);
                        line(edges,interpolate(p,q,start),interpolate(p,q,end),.85,alpha(color,edgeAlpha));
                    }
                } else {
                    line(glow,p,q,selected?4:3.2,alpha(color,glowAlpha));
                    line(edges,p,q,selected?1.35:1,alpha(color,edgeAlpha));
                }
            }
        }
        ScreenPoint screen(HighlightProjection.Vertex v){return new ScreenPoint((float)((v.x()/v.w()*.5+.5)*width),(float)((.5-v.y()/v.w()*.5)*height));}
        ScreenPoint screen(ChunkSelectionMath.ClipPoint v){return new ScreenPoint((float)((v.x()/v.w()*.5+.5)*width),(float)((.5-v.y()/v.w()*.5)*height));}
        void line(List<Quad> list,ScreenPoint a,ScreenPoint b,double thickness,int color) {
            double length=Math.hypot(b.x-a.x,b.y-a.y);if(length<.05)return;
            float dx=(float)(-(b.y-a.y)/length*thickness*.5),dy=(float)((b.x-a.x)/length*thickness*.5);
            add(list,new Quad(new ScreenPoint(a.x-dx,a.y-dy),new ScreenPoint(b.x-dx,b.y-dy),new ScreenPoint(b.x+dx,b.y+dy),new ScreenPoint(a.x+dx,a.y+dy),color));
        }
        void add(List<Quad> list,Quad quad){if(quads>=MAX_QUADS)return;list.add(quad);quads++;}
        static ScreenPoint interpolate(ScreenPoint a,ScreenPoint b,double t){return new ScreenPoint((float)(a.x+(b.x-a.x)*t),(float)(a.y+(b.y-a.y)*t));}
        static int alpha(int color,int alpha){return color&0xFFFFFF|Math.clamp(alpha,0,255)<<24;}
        void submit(GuiGraphicsExtractor graphics) {
            var state=((GuiGraphicsAccessor)graphics).tuntung$guiRenderState();
            var pose=new Matrix3x2f(graphics.pose());var scissor=new ScreenRectangle(0,0,width,height);
            for(List<Quad> layer:List.of(fill,glow,edges))if(!layer.isEmpty())state.addGuiElement(new Batch(List.copyOf(layer),pose,scissor));
        }
    }
    private record Batch(List<Quad> quads,Matrix3x2fc pose,ScreenRectangle scissorArea) implements GuiElementRenderState {
        public RenderPipeline pipeline(){return RenderPipelines.GUI;}
        public TextureSetup textureSetup(){return TextureSetup.noTexture();}
        public ScreenRectangle bounds(){return scissorArea;}
        public void buildVertices(VertexConsumer out){for(Quad q:quads){
            // The stock GUI pipeline keeps back-face culling enabled. Match vanilla rectangle winding.
            boolean reverse=ChunkSelectionMath.reverseForGui(q.a.x,q.a.y,q.b.x,q.b.y,q.c.x,q.c.y,q.d.x,q.d.y);
            emit(out,q.a,q.color);emit(out,reverse?q.d:q.b,q.color);emit(out,q.c,q.color);emit(out,reverse?q.b:q.d,q.color);
        }}
        private void emit(VertexConsumer out,ScreenPoint p,int color){out.addVertexWith2DPose(pose,p.x,p.y).setColor(color);}
    }
}
