package dev.amethyst.finder.ui;

import dev.amethyst.finder.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A walking/bridging compass and an explicit alert; neither is a pathfinder or an auto-exit. */
public final class ElytraHud {
    private static ItemStack icon;
    private ElytraHud(){}
    public static void extract(GuiGraphicsExtractor g,DeltaTracker delta){
        var c=Minecraft.getInstance();var finder=ChunkFinderClient.ELYTRA;
        if(finder==null||!finder.enabled()||c.level==null||c.player==null||c.gui.overlay()!=null)return;
        if(c.gui.screen()!=null)return;
        if(c.gui.hud.isHidden())return;
        int w=Math.min(322,g.guiWidth()-16),x=8,y=g.guiHeight()-101;
        Theme.card(g,x,y,w,65,0xE817110E);
        if(icon==null)icon=new ItemStack(Items.ELYTRA);g.item(icon,x+10,y+9);
        var t=finder.target();
        Theme.smallText(g,"ELYTRA FINDER · "+(t==null?"SEARCHING":t.kind().equals("frame")?"ITEM FRAME":"DROPPED ITEM"),x+35,y+8,w-45,0xFFF1C987);
        if(t==null){
            Theme.smallText(g,"No Elytra received in range",x+12,y+28,w-24,0xFFF2E9DC);
            Theme.smallText(g,"Explore to load more entities · "+ChunkFinderClient.CONFIG.elytraRange+"m",x+12,y+47,w-24,0xFFB0A192);return;
        }
        var live=ElytraFinder.locate(t.id(),t.kind(),c.player.getX(),c.player.getY(),c.player.getZ(),c.player.getYRot(),t.x(),t.y(),t.z());
        double angle=Math.toRadians(live.turn()),ax=x+20,ay=y+39;
        double tx=ax+Math.sin(angle)*10,ty=ay-Math.cos(angle)*10;
        line(g,ax-Math.sin(angle)*7,ay+Math.cos(angle)*7,tx,ty,0xFFF1C987);
        line(g,tx,ty,tx-Math.sin(angle+.65)*7,ty+Math.cos(angle+.65)*7,0xFFF1C987);
        line(g,tx,ty,tx-Math.sin(angle-.65)*7,ty+Math.cos(angle-.65)*7,0xFFF1C987);
        String direction=live.direction()+" / "+live.cardinal()+" · "+Math.round(live.distance())+"m";
        Theme.smallText(g,direction,x+38,y+28,w-48,0xFFFFF3DF);
        String xyz="X "+(int)Math.floor(live.x())+" Y "+(int)Math.floor(live.y())+" Z "+(int)Math.floor(live.z())+" · "+(live.vertical()>=0?"+":"")+Math.round(live.vertical())+"Y";
        Theme.smallText(g,xyz,x+38,y+46,w-48,0xFFB6A693);
    }
    /** Extracted after ordinary screens as well as the hidden HUD. */
    public static void extractWarning(GuiGraphicsExtractor g){
        var c=Minecraft.getInstance();var finder=ChunkFinderClient.ELYTRA;
        if(finder==null||!finder.enabled()||c.level==null||c.player==null||c.gui.overlay()!=null||c.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen)return;
        // Player safety stays visible in inventory, chat and F1, including players already nearby on activation.
        if(!finder.players().isEmpty()){
            boolean compact=c.gui.screen()!=null;int w=Math.min(350,g.guiWidth()-16),x=(g.guiWidth()-w)/2,y=compact?4:12,h=compact?28:42;
            g.fill(x,y,x+w,y+h,0xED3B1116);g.fill(x,y,x+3,y+h,0xFFFF626C);
            Theme.smallText(g,"PLAYER NEARBY · "+finder.players().size()+" RECEIVED",x+11,y+(compact?4:7),w-20,0xFFFF7B84);
            var p=finder.players().getFirst();String names=p.name()+" · "+Math.round(p.distance())+"m";
            if(finder.players().size()>1)names+=" · +"+(finder.players().size()-1)+" more";
            Theme.smallText(g,names,x+11,y+(compact?16:21),w-20,0xFFFFF1E8);
        }
    }
    private static void line(GuiGraphicsExtractor g,double x0,double y0,double x1,double y1,int color){
        g.pose().pushMatrix();g.pose().translate((float)x0,(float)y0).rotate((float)Math.atan2(y1-y0,x1-x0));g.fill(0,-1,(int)Math.ceil(Math.hypot(x1-x0,y1-y0)),1,color);g.pose().popMatrix();
    }
}
