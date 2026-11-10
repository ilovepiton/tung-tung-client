package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Mutually exclusive, optional visual masks. Never changes block types or server state. */
public final class AntiLeak {
    private AntiLeak() {}
    public static boolean variant(TexturePacks.Variant v){return v.id().startsWith("anti-leak-");}
    private static boolean pack(String id){return TexturePacks.VARIANTS.stream().anyMatch(v->variant(v)&&v.aliases().contains(id));}
    /** Keep the last explicitly selected mask, above other client textures, without enabling an OFF mask. */
    public static List<String> onTop(Collection<String> current){
        var next=new ArrayList<String>();String active=null;
        for(String id:current){if(pack(id))active=id;else if(!next.contains(id))next.add(id);}
        if(active!=null)next.add(active);return List.copyOf(next);
    }
    public static List<String> selection(Collection<String> current,String incoming){
        var next=new ArrayList<String>();for(String id:current)if(!pack(id)&&!next.contains(id))next.add(id);
        if(incoming!=null){if(!pack(incoming))throw new IllegalArgumentException("Unknown Anti-Leak pack");next.add(incoming);}
        return List.copyOf(next);
    }
    public static Map<String,Object> state(Minecraft c,Collection<String> selected,boolean loading){
        var active=TexturePacks.VARIANTS.stream().filter(AntiLeak::variant).filter(v->v.aliases().stream().anyMatch(selected::contains)).findFirst();
        if(active.isEmpty())return Map.of("enabled",false,"variant","","status","off");
        var v=active.get();
        boolean winning=List.of("blockstates/bedrock.json","blockstates/cobblestone.json").stream().allMatch(path->
            c.getResourceManager().getResource(Identifier.parse("minecraft:"+path)).map(r->v.aliases().contains(r.sourcePackId())).orElse(false));
        return Map.of("enabled",true,"variant",v.id(),"status",loading?"loading":winning?"active":"conflict");
    }
}
