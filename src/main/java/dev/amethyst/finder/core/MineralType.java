package dev.amethyst.finder.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable saved IDs shared by the scanner, native game menu and desktop bridge. */
public enum MineralType {
    DIAMOND("diamond", "Diamond", 0xFF5FE7EE),
    EMERALD("emerald", "Emerald", 0xFF62E894),
    LAPIS("lapis", "Lapis lazuli", 0xFF739AFF),
    IRON("iron", "Iron", 0xFFE4C7AF),
    GOLD("gold", "Gold", 0xFFFFD365),
    COPPER("copper", "Copper", 0xFFED9A70),
    REDSTONE("redstone", "Redstone", 0xFFFF6C72),
    COAL("coal", "Coal", 0xFFBCC5D2),
    QUARTZ("quartz", "Nether quartz", 0xFFFFE5D4),
    ANCIENT_DEBRIS("ancient_debris", "Ancient debris", 0xFFCC9C8D),
    AMETHYST("amethyst", "Amethyst", 0xFFC99AFF),
    SULFUR("sulfur", "Sulfur", 0xFFF1ED8F);

    public final String id, label;
    public final int color;
    MineralType(String id, String label, int color) { this.id=id;this.label=label;this.color=color; }
    public String module() { return this==DIAMOND?"diamond_esp":"ore_esp"; }
    public static Map<String,Boolean> defaults() { return sanitize(null); }
    public static Map<String,Boolean> sanitize(Map<String,Boolean> supplied) {
        Map<String,Boolean> result=new LinkedHashMap<>();
        for(var mineral:values()) result.put(mineral.id,supplied==null||!Boolean.FALSE.equals(supplied.get(mineral.id)));
        return result;
    }
    public static MineralType byId(String id) {
        for(var mineral:values())if(mineral.id.equals(id))return mineral;
        return null;
    }
}
