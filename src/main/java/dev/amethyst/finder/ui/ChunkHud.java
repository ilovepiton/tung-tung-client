package dev.amethyst.finder.ui;

import dev.amethyst.finder.core.ChunkAnalysis;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;

/** Compact, factual current-versus-history details drawn in the gameplay HUD. */
public final class ChunkHud {
    private ChunkHud() {}
    public static void draw(GuiGraphicsExtractor g,Minecraft client,ChunkSelection.Selected selected,int rendered,int limit) {
        List<String> rows=new ArrayList<>();
        int accent=0xFFB66CFF;
        String title="TUNTUN / CHUNKS",key=analysisKeyName();
        if(selected==null) {
            rows.add(rendered+" nearby · limit "+limit);
            rows.add(key+": findings and controls");
        } else {
            var category=selected.categories().isEmpty()?null:selected.categories().getFirst();
            if(category!=null) {
                accent=category.color;
                title="TUNTUN / "+switch(category) {
                    case CHEST -> "STORAGE";case AMETHYST -> "AMETHYST";case BASE -> "STRUCTURES";
                    case PLAYER -> "PLAYERS";case SPAWNER -> "SPAWNERS";case SEARCH -> "SUS";case CAVE_FREE -> "CAVE SCAN";
                };
            }
            var a=selected.analysis();
            String status=category==ChunkSelection.Category.PLAYER||a==null?"CURRENT":switch(a.status()) {
                case LIVE -> "CURRENT";case HISTORICAL -> "HISTORY";case VERIFYING -> "VERIFYING";
            };
            String age=a==null||category==ChunkSelection.Category.PLAYER?"": " · "+age(a.observedAtMillis()).replace(" ago","");
            rows.add(selected.chunkX()+", "+selected.chunkZ()+" · "+status+age);
            if(category==ChunkSelection.Category.CAVE_FREE) {
                rows.add("No underground cavities detected");
                rows.add("Received blocks · full scan");
            } else if(category==ChunkSelection.Category.PLAYER||a==null) rows.add("Players observed: "+selected.players());
            else if(category==ChunkSelection.Category.CHEST)rows.add("Storage blocks: "+a.storageCount());
            else if(category==ChunkSelection.Category.SPAWNER)rows.add("Spawners: "+a.spawnerCount());
            else if(category==ChunkSelection.Category.BASE) {
                rows.add("Evidence: "+a.evidence());
                rows.add("Storage "+a.storageCount()+" · Crafting "+a.craftingCount()+" · Redstone "+a.redstoneCount());
            } else if(category==ChunkSelection.Category.AMETHYST) {
                rows.add("Amethyst "+a.amethystCount()+" · Growth "+a.growthEvents());
                if(a.growthEvents()>0)rows.add(shortStage(a.previousGrowthStage())+" → "+shortStage(a.currentGrowthStage())
                        +(a.gapGrowthEvents()>0?" · across gap":""));
            }
            if(rows.size()<3&&selected.categories().size()>1) {
                String also=selected.categories().stream().skip(1).map(c->c.name()).collect(java.util.stream.Collectors.joining(" / "));
                rows.add("Also: "+also);
            }
            rows.add(category==ChunkSelection.Category.CAVE_FREE?"Hidden terrain stays unknown":key+": full details"+(selected.explicit()?" · pinned":""));
        }
        int width=Math.min(198,g.guiWidth()-16),height=18+rows.size()*11;
        int x=8,y=Math.max(32,g.guiHeight()-height-46);
        g.fill(x,y,x+width,y+height,0xB80C0B12);
        g.fill(x,y,x+2,y+height,accent);
        g.text(client.font,fit(client,title,width-18),x+8,y+5,accent,false);
        int rowY=y+17;
        for(String text:rows) { g.text(client.font,fit(client,text,width-16),x+8,rowY,0xFFD2CBDC,false);rowY+=11; }
    }
    private static String shortStage(int stage) {
        return switch(stage){case 1 -> "Small";case 2 -> "Medium";case 3 -> "Large";case 4 -> "Cluster";default -> "Unknown";};
    }
    public static String analysisKeyName() {
        return ChunkFinderClient.ANALYSIS==null?"J":ChunkFinderClient.ANALYSIS.getTranslatedKeyMessage().getString();
    }
    public static String age(long observedMillis) {
        if(observedMillis<=0)return "Unavailable";
        long seconds=Math.max(0,(System.currentTimeMillis()-observedMillis)/1000);
        if(seconds<60)return seconds+"s ago";
        if(seconds<3600)return seconds/60+"m ago";
        if(seconds<86400)return seconds/3600+"h ago";
        return seconds/86400+"d ago";
    }
    private static String fit(Minecraft client,String text,int width) {
        if(client.font.width(text)<=width)return text;
        int end=text.length();while(end>0&&client.font.width(text.substring(0,end)+"…")>width)end--;
        return text.substring(0,end)+"…";
    }
}
