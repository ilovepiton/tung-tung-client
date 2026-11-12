package dev.amethyst.finder.modules;

import java.util.*;

/** Explicit farm-resource allowlist; never an inventory-wide sell-all command. */
public final class FarmItems {
    public static final List<String> IDS=List.of("cobblestone","stone","cobbled_deepslate","dirt","netherrack",
            "sand","gravel","oak_log","bamboo","sugar_cane","cactus","kelp","wheat","carrot","potato",
            "beetroot","melon_slice","pumpkin","sweet_berries","rotten_flesh","bone","string","gunpowder","iron_ingot");
    private FarmItems() {}
    public static Map<String,Boolean> sanitize(Map<String,Boolean> input) {
        Map<String,Boolean> result=new LinkedHashMap<>();
        for(String id:IDS)result.put(id,input==null?"cobblestone".equals(id):Boolean.TRUE.equals(input.get(id)));
        return result;
    }
    public static boolean fullStack(String id,int count,int maximum,boolean protectedItem,Map<String,Boolean> selected) {
        return maximum>1&&count==maximum&&!protectedItem&&IDS.contains(id)&&Boolean.TRUE.equals(selected.get(id));
    }
    public enum Receipt { WAIT, ACCEPTED, PARTIAL }
    public static Receipt receipt(long before,long after,int original,int remaining) {
        if(after<=before||remaining<0||remaining>=original)return Receipt.WAIT;
        return remaining==0?Receipt.ACCEPTED:Receipt.PARTIAL;
    }
}
