package dev.amethyst.finder.ui;

import dev.amethyst.finder.*;
import dev.amethyst.finder.core.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Captured model surfaces and view-center tracers, composited above terrain. */
public final class WorldOverlay {
    // The desktop bridge reads visualStatus before item components are bound at boot.
    // Build the item only while drawing a valid game world, never during class loading.
    private static ItemStack totemIcon;
    private static Object captureLevel;
    private static final LinkedHashMap<BlockPos,CachedBlock> blockCaptures=new LinkedHashMap<>();
    private record MarkOrder(List<BlockMark> marks, Vec3 eye) {}
    private static final ExactSelectionCache<MarkOrder,List<BlockMark>> orderedMarks=new ExactSelectionCache<>();
    private static volatile String visualStatus = "Model highlights inactive";
    public static String visualStatus() { return visualStatus; }
    private WorldOverlay() {}

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        FinderConfig c = ChunkFinderClient.CONFIG;
        if (client.level == null || client.player == null || client.gui.hud.isHidden() || client.gui.screen() != null) {
            visualStatus="Model highlights inactive";
            if(client.level==null) { blockCaptures.clear();orderedMarks.clear();captureLevel=null;totemIcon=null;ChunkWorldRenderer.reset();PlayerObservationTracker.reset(); }
            return;
        }
        Camera camera = client.gameRenderer.mainCamera();
        if (!camera.isInitialized()) return;
        Projection p = new Projection(camera, graphics.guiWidth(), graphics.guiHeight());
        graphics.enableScissor(0, 0, p.width, p.height);
        ModelHighlightMesh mesh = new ModelHighlightMesh(p.matrix, p.eye, p.width, p.height);
        List<Label> labels = new ArrayList<>();
        List<Tracer> tracers = new ArrayList<>();
        List<PlayerArrow> playerArrows = new ArrayList<>();
        List<PlayerTag> playerTags = new ArrayList<>();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int drawn = 0;
        java.util.Set<Long> searchKeys=new java.util.HashSet<>();
        if(c.isModuleEnabled("search_chunk"))for(var observed:ChunkFinderClient.SCANNER.chunkObservations())
            if(observed.status()==ChunkAnalysis.Status.LIVE&&observed.count(Signal.CLUSTER)>=c.searchChunkMinClusters)searchKeys.add(observed.key());
        if(captureLevel!=client.level) { blockCaptures.clear();orderedMarks.clear();captureLevel=client.level; }
        if (c.enabled || c.isModuleEnabled("search_chunk") || c.isModuleEnabled("storage_esp") || c.isModuleEnabled("amethyst_esp") || c.isModuleEnabled("spawner_chunks")) {
            List<BlockMark> source=ChunkFinderClient.SCANNER.marks();
            List<BlockMark> marks=orderedMarks.get(new MarkOrder(source,p.eye),()->{
                var sorted=new ArrayList<>(source);
                sorted.sort(Comparator.comparingDouble(m -> distanceSquared(m.x() + .5, m.y() + .5, m.z() + .5, p.eye)));
                return List.copyOf(sorted);
            });
            for (BlockMark mark : marks) {
                if (drawn >= c.espLimit) break;
                Signal s = mark.signal();
                boolean visible = s.isStorage() && c.isModuleEnabled("storage_esp")
                        || s.isAmethyst() && c.isModuleEnabled("amethyst_esp")
                        || s == Signal.SPAWNER && c.isModuleEnabled("spawner_chunks")
                        || c.enabled && c.finderTargetEnabled(s)
                        || s==Signal.CLUSTER&&searchKeys.contains(ChunkEvidence.key(mark.x()>>4,mark.z()>>4));
                if (!visible) continue;
                int color = s==Signal.CLUSTER&&searchKeys.contains(ChunkEvidence.key(mark.x()>>4,mark.z()>>4))?0xFFFF3434:HighlightPalette.color(s);
                double distance = distanceSquared(mark.x() + .5, mark.y() + .5, mark.z() + .5, p.eye);
                double dx=mark.x()+.5-p.eye.x,dz=mark.z()+.5-p.eye.z;
                int markerRange=s==Signal.CLUSTER&&searchKeys.contains(ChunkEvidence.key(mark.x()>>4,mark.z()>>4))?c.searchChunkRangeBlocks:c.espRange;
                if (dx*dx+dz*dz > (double)markerRange * markerRange) continue;
                // Ignore stale observations after a block change; never load a missing chunk to verify a marker.
                if (!ChunkWorldRenderer.chunkLoaded(client,mark.x() >> 4,mark.z() >> 4)) continue;
                if (BlockClassifier.classify(client.level.getBlockState(new BlockPos(mark.x(), mark.y(), mark.z()))) != s) continue;
                boolean rendered=false;
                if (mesh.remaining() > 0) {
                    BlockPos pos = new BlockPos(mark.x(), mark.y(), mark.z());
                    rendered=mesh.add(blockCapture(client,pos,partial),color);
                } else mesh.budgetReached();
                // Off-screen models must not consume the visible-object budget.
                if(!rendered)continue;
                trace(tracers, p, mark.x() + .5, mark.y() + .5, mark.z() + .5, color, c.espTracers);
                if (c.enabled && c.finderTargetEnabled(s)) {
                    Point label = p.point(mark.x() + .5, mark.y() + 1.2, mark.z() + .5);
                    if (label != null) {
                        String name = targetName(s);
                        labels.add(new Label(name,label,color,false));
                    }
                }
                drawn++;
            }
        }
        if (c.enabled && c.finderTargetEnabled(Signal.ARMOR_STAND)) {
            List<ArmorStand> stands = new ArrayList<>();
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof ArmorStand stand && stand.isAlive() && !stand.isRemoved()
                        && stand.distanceToSqr(client.player) <= c.espRange * c.espRange
                        && ChunkWorldRenderer.chunkLoaded(client,(int)Math.floor(stand.getX()) >> 4,(int)Math.floor(stand.getZ()) >> 4)
                        && Math.abs(stand.chunkPosition().x() - client.player.chunkPosition().x()) <= c.radiusChunks
                        && Math.abs(stand.chunkPosition().z() - client.player.chunkPosition().z()) <= c.radiusChunks) stands.add(stand);
            stands.sort(Comparator.comparingDouble(s -> distanceSquared(s.getX(), s.getY(), s.getZ(), p.eye)));
            for (ArmorStand stand : stands) {
                if (drawn >= c.espLimit) break;
                var b = stand.getBoundingBox();
                if (mesh.remaining() > 0) mesh.add(ModelGeometryCapture.entity(client, stand, partial, Math.min(1024, mesh.remaining())), HighlightPalette.color(Signal.ARMOR_STAND));
                else mesh.budgetReached();
                trace(tracers,p,stand.getX(),(b.minY+b.maxY)/2,stand.getZ(),HighlightPalette.color(Signal.ARMOR_STAND),c.espTracers);
                Point label = p.point(stand.getX(), b.maxY + .3, stand.getZ());
                if (label != null) labels.add(new Label("Armor stand",label,HighlightPalette.color(Signal.ARMOR_STAND),false));
                drawn++;
            }
        }
        if (c.isModuleEnabled("player_esp") || c.isModuleEnabled("player_info") || c.isModuleEnabled("invis_mode")) {
            var players = new ArrayList<>(client.level.players());
            players.sort(Comparator.comparingDouble(player -> player.distanceToSqr(client.player)));
            int playerCount = 0;
            for (var player : players) {
                if (player == client.player || !player.isAlive() || player.isRemoved()
                        || player.isSpectator()
                        || player.distanceToSqr(client.player) > (double)c.playerVisualRange * c.playerVisualRange) continue;
                if (++playerCount > Math.min(128, c.espLimit)) break;
                var b = player.getBoundingBox();
                Vec3 location = player.getPosition(partial);
                String name = player.getName().getString();
                String distance = Math.round(player.distanceTo(client.player)) + "m";
                if (c.isModuleEnabled("player_esp")) {
                    // Never capture or recolor the player's model: their armor remains visible.
                    var indicator = p.indicator(location.x, location.y + b.getYsize() * .5, location.z);
                    if (indicator != null) playerArrows.add(new PlayerArrow(indicator, name, distance, c.espTracers));
                }
                Point label = p.point(location.x, location.y + b.getYsize() + .55, location.z);
                if (label != null && label.x >= 0 && label.x <= p.width && label.y >= 0 && label.y <= p.height) {
                    boolean info = c.isModuleEnabled("player_info");
                    boolean invisible=dev.amethyst.finder.modules.InvisMode.reveals(player);
                    if(!info&&!c.isModuleEnabled("player_esp")&&!invisible)continue;
                    playerTags.add(new PlayerTag(name, distance, label,
                            info ? hearts(player.getHealth()) : null,
                            info && player.getAbsorptionAmount() > 0 ? "+" + hearts(player.getAbsorptionAmount()) : null,
                            PlayerObservationTracker.visibleHeldTotems(player), PlayerObservationTracker.pops(player),PlayerObservationTracker.inventoryTotems(player),invisible));
                }
            }
        }
        int resourceLabels=0;
        for(var mark:ChunkFinderClient.RESOURCES.marks(client)) {
            BlockPos pos=mark.position();
            if(!ChunkWorldRenderer.chunkLoaded(client,pos.getX()>>4,pos.getZ()>>4))continue;
            var state=client.level.getBlockState(pos);
            if(ResourceScanner.kind(state)!=mark.kind()||ResourceScanner.mineral(state)!=mark.mineral())continue;
            if(mesh.remaining()>0)mesh.add(mark.kind()==ResourceScanner.Kind.LAVA?ModelGeometryCapture.lava(client,pos):blockCapture(client,pos,partial),mark.color());
            if(mark.kind()!=ResourceScanner.Kind.LAVA&&resourceLabels<8) {
                Point at=p.point(pos.getX()+.5,pos.getY()+1.2,pos.getZ()+.5);
                if(at!=null){labels.add(new Label(state.getBlock().getName().getString(),at,mark.color(),true));resourceLabels++;}
            }
        }
        if(ChunkFinderClient.ITEMS!=null) {
            int itemLabels=0;
            List<Label> dropLabels=new ArrayList<>();
            for(var mark:ChunkFinderClient.ITEMS.marks(client)) {
                if(itemLabels++>=32)break;
                Point at=p.point(mark.x(),mark.y()+.65,mark.z());
                if(at!=null) {
                    String text=client.font.plainSubstrByWidth(mark.name(),170)+" ×"+mark.count()+" · "+Math.round(mark.distance())+"m";
                    double width=client.font.width(text)*.6;
                    double x=Math.clamp(at.x,4+width/2,p.width-4-width/2);
                    for(int offset=0;offset<8&&dropLabels.size()<16;offset++) {
                        Point candidate=new Point(x,at.y-offset*9);
                        if(candidate.y<5||candidate.y>p.height-16)continue;
                        boolean overlap=false;
                        for(var existing:dropLabels)if(Math.abs(existing.point.y-candidate.y)<9
                                &&Math.abs(existing.point.x-candidate.x)<(client.font.width(existing.text)*.6+width)/2+5){overlap=true;break;}
                        if(!overlap){dropLabels.add(new Label(text,candidate,mark.color(),true,.6f));break;}
                    }
                    if(itemLabels<=8)trace(tracers,p,mark.x(),mark.y()+.2,mark.z(),mark.color(),true);
                }
            }
            labels.addAll(dropLabels);
        }
        ChunkWorldRenderer.draw(graphics,client,camera);
        mesh.draw(graphics,client);
        visualStatus = mesh.status();
        for (Tracer tracer : tracers) line(graphics,p.width/2.0,p.height/2.0,tracer.point.x,tracer.point.y,(tracer.color&0xFFFFFF)|0xB0000000);
        for (PlayerArrow arrow : playerArrows) drawPlayerArrow(graphics, client, p, arrow, c);
        for (Label label : labels) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float)label.point.x,(float)label.point.y).scale(label.scale);
            int x=-client.font.width(label.text)/2;
            if (label.backdrop) graphics.fill(x-4,-3,x+client.font.width(label.text)+4,11,0xC4110B12);
            graphics.text(client.font,label.text,x,0,label.color,false);
            graphics.pose().popMatrix();
        }
        for (PlayerTag tag : playerTags) drawPlayerTag(graphics, client, p, tag, c.playerInfoScale*.72*distanceTagScale(tag.distance));
        ChunkWorldRenderer.drawHud(graphics,client);
        if(ChunkFinderClient.ITEMS!=null&&ChunkFinderClient.ITEMS.enabled()) {
            int y=p.height-(ChunkFinderClient.ELYTRA.enabled()?178:88);
            graphics.pose().pushMatrix();graphics.pose().translate(8,y).scale(.65f);
            Theme.card(graphics,0,0,300,42,0xE817110E);
            Theme.smallText(graphics,"ITEM FINDER · "+ChunkFinderClient.ITEMS.marks(client).size()+" stacks · "+ChunkFinderClient.ITEMS.chunks(client).size()+" chunks",10,8,280,0xFFFFC875);
            var drops=ChunkFinderClient.ITEMS.marks(client);
            String detail=drops.isEmpty()?"No selected drops received · "+c.itemFinderRange+"m":drops.getFirst().name()+" ×"+drops.getFirst().count()+" · "+Math.round(drops.getFirst().distance())+"m · chunk "+drops.getFirst().chunkX()+", "+drops.getFirst().chunkZ();
            Theme.smallText(graphics,detail,10,25,280,0xFFF2EBE1);
            graphics.pose().popMatrix();
        }
        drawPinnedPosition(graphics,client,p);
        if (mesh.hasIssue()) graphics.text(client.font,visualStatus,8,p.height-32,0xFFFFB66E,false);
        graphics.disableScissor();

    }
    private static void drawPinnedPosition(GuiGraphicsExtractor g,Minecraft client,Projection projection) {
        var pin=ChunkFinderClient.PLAYERS.active(client);if(pin==null)return;
        int width=194,x=projection.width-width-12,y=12;
        Theme.card(g,x,y,width,57,0xE6100C09);g.fill(x,y+5,x+2,y+52,0xFFF3C879);
        Theme.smallText(g,"SAVED / "+pin.name(),x+10,y+8,width-20,0xFFF3C879);
        Theme.smallText(g,pin.coordinates(),x+10,y+23,width-20,0xFFF2F6FC);
        double distance=client.player.position().distanceTo(new Vec3(pin.x()+.5,pin.y(),pin.z()+.5));
        Theme.smallText(g,Math.round(distance)+"m to saved position · /findplayer clear",x+10,y+39,width-20,0xFF93897D);
        var indicator=projection.indicator(pin.x()+.5,pin.y()+1.3,pin.z()+.5);
        if(indicator!=null) {
            double mx=indicator.x(),my=indicator.y();int color=0xFFF3C879;
            line(g,mx,my-6,mx+6,my,color);line(g,mx+6,my,mx,my+6,color);
            line(g,mx,my+6,mx-6,my,color);line(g,mx-6,my,mx,my-6,color);
            Theme.smallText(g,"SAVED",(int)mx-15,(int)my+10,60,color);
        }
    }
    private static String targetName(Signal signal) {
        return switch (signal) {
            case CHEST -> "Chest"; case BARREL -> "Barrel"; case SHULKER -> "Shulker box";
            case ENDER_CHEST -> "Ender chest"; case HOPPER -> "Hopper"; case FURNACE -> "Furnace";
            case DISPENSER -> "Dispenser";case DROPPER -> "Dropper";
            case CRAFTING -> "Crafting table"; case ENCHANTING -> "Enchanting table"; case ANVIL -> "Anvil";
            case BEACON -> "Beacon"; case SPAWNER -> "Spawner"; case ARMOR_STAND -> "Armor stand";
            case AMETHYST -> "Amethyst"; case BUDDING -> "Budding amethyst";
            case BUD -> "Amethyst bud"; case CLUSTER -> "Amethyst cluster";
            default -> "Selected signal";
        };
    }

    private static double distanceSquared(double x, double y, double z, Vec3 eye) {
        double dx=x-eye.x, dy=y-eye.y, dz=z-eye.z; return dx*dx+dy*dy+dz*dz;
    }
    private record CachedBlock(Object state,long capturedAt,ModelGeometryCapture.Capture capture) {}
    private static ModelGeometryCapture.Capture blockCapture(Minecraft client,BlockPos pos,float partial) {
        var state=client.level.getBlockState(pos);
        long now=System.nanoTime()/1_000_000L;
        CachedBlock cached=blockCaptures.get(pos);
        // Baked geometry is immutable; block entities receive a fresh animation snapshot at up to 30 Hz.
        int lifetime=state.hasBlockEntity()?33:1500;
        if(cached!=null&&cached.state==state&&now-cached.capturedAt<lifetime)return cached.capture;
        ModelGeometryCapture.Capture capture=ModelGeometryCapture.block(client,pos,state,partial,256);
        if(blockCaptures.size()>=512&&!blockCaptures.containsKey(pos))blockCaptures.remove(blockCaptures.keySet().iterator().next());
        blockCaptures.put(pos,new CachedBlock(state,now,capture));
        return capture;
    }
    private static void trace(List<Tracer> tracers,Projection p,double x,double y,double z,int color,boolean enabled) {
        if (!enabled) return;
        Point point=p.point(x,y,z);
        if (point!=null) tracers.add(new Tracer(point,color));
    }
    private record Label(String text,Point point,int color,boolean backdrop,float scale) {
        Label(String text,Point point,int color,boolean backdrop){this(text,point,color,backdrop,1);}
    }
    private record Tracer(Point point,int color) {}
    private record PlayerArrow(PlayerIndicatorGeometry.Indicator indicator, String name, String distance, boolean tracer) {}
    private record PlayerTag(String name, String distance, Point point, String health, String absorption, int held, int pops,Integer inventory,boolean invisible) {}

    private static String hearts(float health) {
        if (!Float.isFinite(health) || health < 0) return "?";
        double hearts = Math.round(health * 5.0) / 10.0;
        return hearts == Math.floor(hearts) ? Long.toString((long) hearts) : Double.toString(hearts);
    }

    private static void drawPlayerArrow(GuiGraphicsExtractor g, Minecraft client, Projection p, PlayerArrow arrow, FinderConfig config) {
        var a = arrow.indicator;
        double centerX = p.width * .5, centerY = p.height * .5;
        int color = config.playerIndicatorColor | 0xFF000000;
        double distance = Math.hypot(a.x() - centerX, a.y() - centerY);
        if (arrow.tracer && distance > 16)
            line(g, centerX + a.directionX() * 9, centerY + a.directionY() * 9,
                    a.x() - a.directionX() * 7, a.y() - a.directionY() * 7, (color & 0xFFFFFF) | 0xB0000000);
        if (distance > 12) {
            double backX = a.x() - a.directionX() * 7, backY = a.y() - a.directionY() * 7;
            line(g, a.x(), a.y(), backX - a.directionY() * 4, backY + a.directionX() * 4, color);
            line(g, a.x(), a.y(), backX + a.directionY() * 4, backY - a.directionX() * 4, color);
        }
        if (a.offscreen()) {
            drawPlayerTag(g, client, p, new PlayerTag(arrow.name, arrow.distance,
                    new Point(a.x(), a.y() + (a.y() < centerY ? 24 : -8)), null, null, 0, 0,null,false), config.playerInfoScale*.72*distanceTagScale(arrow.distance));
        }
    }

    private static double distanceTagScale(String label) {
        try { double blocks=Double.parseDouble(label.replaceAll("[^0-9.]", ""));return Math.clamp(1/Math.sqrt(Math.max(1,blocks/12)),.3,1); }
        catch(NumberFormatException ignored){return 1;}
    }
    private static void drawPlayerTag(GuiGraphicsExtractor g, Minecraft client, Projection p, PlayerTag tag, double scale) {
        int nameWidth = client.font.width(tag.name), distanceWidth = client.font.width(tag.distance);
        String totems = tag.inventory==null?"Inv ?":"Inv "+tag.inventory;
        int healthWidth = tag.health == null ? 0 : 10 + client.font.width(tag.health);
        int absorptionWidth = tag.absorption == null ? 0 : 5 + client.font.width(tag.absorption);
        int statsWidth = healthWidth + absorptionWidth + 8 + 12 + client.font.width(totems);
        int width = Math.max(nameWidth + 8 + distanceWidth, tag.health == null ? 0 : statsWidth) + 8;
        if(tag.invisible)width=Math.max(width,client.font.width(dev.amethyst.finder.modules.InvisMode.LABEL)+8);
        int offset=tag.invisible?12:0;
        int height = (tag.health == null ? 15 : 28)+offset;
        var layout = CompactPlayerTagLayout.fit(tag.point.x, tag.point.y, width, height, scale, p.width, p.height);
        if (layout == null) return;
        g.pose().pushMatrix();
        g.pose().translate(layout.x(), layout.y()).scale(layout.scale());
        // A small rounded dark plate keeps the armor visible around the tag.
        g.fill(2, 1, width - 2, height, 0x50100B08);
        g.fill(1, 0, width - 1, height, 0xCC171615);
        g.fill(0, 1, width, height - 1, 0xCC171615);
        g.fill(2, 0, width - 2, 1, 0x606A5643);
        if(tag.invisible)g.text(client.font,dev.amethyst.finder.modules.InvisMode.LABEL,(width-client.font.width(dev.amethyst.finder.modules.InvisMode.LABEL))/2,3,dev.amethyst.finder.modules.InvisMode.LABEL_COLOR,false);
        g.text(client.font, tag.name, 4, 4+offset, 0xFFF4EFEA, false);
        g.text(client.font, tag.distance, width - distanceWidth - 4, 4+offset, 0xFFAAA09A, false);
        if (tag.health != null) {
            int x = 4, y = 17+offset;
            pixelHeart(g, x, y, 0xFFFA6372);
            x += 10;
            g.text(client.font, tag.health, x, y - 1, 0xFFFF7B84, false);
            x += client.font.width(tag.health);
            if (tag.absorption != null) {
                x += 5;
                g.text(client.font, tag.absorption, x, y - 1, 0xFFFFD568, false);
                x += client.font.width(tag.absorption);
            }
            x += 8;
            g.pose().pushMatrix();
            g.pose().translate(x - 1, y - 3).scale(.75f);
            if (totemIcon == null) totemIcon = new ItemStack(Items.TOTEM_OF_UNDYING);
            g.item(totemIcon, 0, 0);
            g.pose().popMatrix();
            g.text(client.font, totems, x + 12, y - 1, 0xFFF2CE7C, false);
        }
        g.pose().popMatrix();
    }

    private static void pixelHeart(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 3, y + 1, color);
        g.fill(x + 4, y, x + 6, y + 1, color);
        g.fill(x, y + 1, x + 7, y + 3, color);
        g.fill(x + 1, y + 3, x + 6, y + 4, color);
        g.fill(x + 2, y + 4, x + 5, y + 5, color);
        g.fill(x + 3, y + 5, x + 4, y + 6, 0xFFB63B4A);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFFFFB2B8);
    }
    private static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color) {
        double length=Math.hypot(x1-x0,y1-y0);
        if (!Double.isFinite(length) || length<.4 || length>8000) return;
        g.pose().pushMatrix();
        g.pose().translate((float)x0,(float)y0).rotate((float)Math.atan2(y1-y0,x1-x0));
        g.fill(0,0,(int)Math.ceil(length),1,color);
        g.pose().popMatrix();
    }
    private record Point(double x,double y) {}
    private static final class Projection {
        final Vec3 eye;
        final Matrix4f matrix;
        final int width,height;
        Projection(Camera camera,int width,int height) {
            this.eye=camera.position(); this.matrix=camera.getViewRotationProjectionMatrix(new Matrix4f()); this.width=width;this.height=height;
        }
        Point point(double x,double y,double z) {
            Vector4f q=matrix.transform(new Vector4f((float)(x-eye.x),(float)(y-eye.y),(float)(z-eye.z),1));
            if(q.w<=.05f || !Float.isFinite(q.w)) return null;
            double nx=q.x/q.w, ny=q.y/q.w;
            if(!Double.isFinite(nx)||!Double.isFinite(ny)||Math.abs(nx)>4||Math.abs(ny)>4) return null;
            return new Point((nx*.5+.5)*width,(.5-ny*.5)*height);
        }
        PlayerIndicatorGeometry.Indicator indicator(double x, double y, double z) {
            Vector4f q = matrix.transform(new Vector4f((float) (x-eye.x), (float) (y-eye.y), (float) (z-eye.z), 1));
            return PlayerIndicatorGeometry.project(q.x, q.y, q.w, width, height, 24);
        }
    }
}
