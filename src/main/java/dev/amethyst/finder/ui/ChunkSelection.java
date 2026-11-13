package dev.amethyst.finder.ui;

import dev.amethyst.finder.core.ChunkAnalysis;
import dev.amethyst.finder.core.ChunkEvidence;
import java.util.List;

/** Selection shared by the gameplay crosshair and the dedicated in-game analysis screen. */
public final class ChunkSelection {
    public enum Category {
        CHEST("CHEST CHUNK",0xFFFF2D2D,"chest_chunks"),
        AMETHYST("AMETHYST CHUNK",0xFFB66CFF,"amethyst_chunks"),
        BASE("STRUCTURE FINDING",0xFFFFB020,"base_chunks"),
        PLAYER("PLAYER CHUNK",0xFFFF55CC,"player_chunks"),
        SPAWNER("SPAWNER CHUNK",0xFF35C9FF,"spawner_chunks"),
        SEARCH("SUS CHUNK",0xFFFF3434,"search_chunk"),
        CAVE_FREE("NO CAVES DETECTED",0xFF57E69B,"cave_detector");
        public final String label,moduleId;public final int color;
        Category(String label,int color,String moduleId) { this.label=label;this.color=color;this.moduleId=moduleId; }
    }
    public record Selected(long key,int chunkX,int chunkZ,ChunkAnalysis analysis,List<Category> categories,int players,boolean explicit) {
        public Selected { categories=List.copyOf(categories); }
    }
    private static Long overrideKey;
    private static Selected selected;
    private ChunkSelection() {}
    public static Long selectedKey() {
        if(selected!=null)return Long.valueOf(selected.key());
        return overrideKey;
    }
    public static Long overrideKey() { return overrideKey; }
    public static void setOverride(Long key) { overrideKey=key;selected=null; }
    public static void setOverride(int x,int z) { setOverride(ChunkEvidence.key(x,z)); }
    public static void clearOverride() { overrideKey=null;selected=null; }
    public static Selected selected() { return selected; }
    static void update(Selected selection) { selected=selection; }
    static void reset() { clearOverride(); }
}
