package dev.amethyst.finder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import java.util.*;

/** Local resource-pack selection. The server connection and unrelated packs are preserved. */
public final class TexturePacks {
    public record Variant(String id, String name, String file, String version, List<String> aliases) {public String group(){if(AntiLeak.variant(this))return "anti-leak";return (id.startsWith("tung-")||java.util.Set.of("oiiaoiia","small-shield-totem","dr-donut-meow","tux-totem","duck-totems","hamster-of-undying","furry-fox-totem","blinking-totem").contains(id))?"totem":java.util.Set.of("continuous-pastels","short-swords-pack","pvp-crosshair","dark-gui.",WhiteGlint.ID).contains(id)?"addon":"pack";}}
    public static final List<Variant> VARIANTS = List.of(
        new Variant("anti-leak-netherite","Anti-Leak · Netherite","Anti-Leak-Netherite.zip","Kulka · fixed rotations · 26.3",List.of("file/Anti-Leak-Netherite.zip")),
        new Variant("anti-leak-stone","Anti-Leak · Stone","Anti-Leak-Stone.zip","MagicClutch · fixed rotations · 26.3",List.of("file/Anti-Leak-Stone.zip")),
        new Variant("anti-leak-slate","Anti-Leak · Slate","Anti-Leak-Slate.zip","Umbraze · fixed rotations · 26.3",List.of("file/Anti-Leak-Slate.zip")),
        new Variant(WhiteGlint.ID,"White Glint",WhiteGlint.FILE,"crelay · 1.5 · 26.3",List.of(WhiteGlint.PACK)),
        new Variant("seven-cold-white","Cold White","SevenStars-cold-white-30ffb592.zip","26.3 · SillyKoyuki",List.of("file/SevenStars-cold-white-30ffb592.zip")),
        new Variant("seven-zanswhite-v1","ZansWhite V1","SevenStars-zanswhite-v1-cc18066a.zip","26.3 · Zansyz",List.of("file/SevenStars-zanswhite-v1-cc18066a.zip")),
        new Variant("seven-monochrome","MonoChrome","SevenStars-monochrome-487ee78b.zip","26.3 · SpliceX",List.of("file/SevenStars-monochrome-487ee78b.zip")),
        new Variant("seven-monster-white","Monster White","SevenStars-monster-white-bb1ab917.zip","26.3 · vw_y",List.of("file/SevenStars-monster-white-bb1ab917.zip")),
        new Variant("seven-black-knight","Black Knight","SevenStars-black-knight-192d9433.zip","26.3 · Fabriicio",List.of("file/SevenStars-black-knight-192d9433.zip")),
        new Variant("seven-lumexs-tung-tung-pack","LUMEXS Tung Tung","SevenStars-lumexs-tung-tung-pack-94fc5d3f.zip","26.3 · smartmend",List.of("file/SevenStars-lumexs-tung-tung-pack-94fc5d3f.zip")),
        new Variant("seven-zansblack","ZansBlack","SevenStars-zansblack-a2d508d5.zip","26.3 · Zansyz",List.of("file/SevenStars-zansblack-a2d508d5.zip")),
        variant("black", "TUNG Black", "Black", "V2 · size fix · 1.21.11", "PigBlack"),
        variant("white", "TUNG White", "White", "V1 · 1.21.11", "PigWhite"),
        variant("purple", "TUNG Purple", "Purple", "V1 · 26+", "PigPurp"),
        variant("classic", "TUNG Classic", "Classic", "V7 · 1.21.11", "PigPack"),
        variant("error", "TUNG Error", "Error", "9 Oct 2026 · 1.21.11", "PigError"),
        new Variant("pastelcraft","TUNG Pastelcraft","Tunk-Extra-pastelcraft.zip","21",List.of("file/Tunk-Extra-pastelcraft.zip")),
        new Variant("continuous-pastels","Continuous Pastels","Tunk-Extra-continuous-pastels.zip","1.0",List.of("file/Tunk-Extra-continuous-pastels.zip")),
        new Variant("pixel-perfection-legacy","TUNG Pixel Perfection Legacy","Tunk-Extra-pixel-perfection-legacy.zip","26.3-97.2",List.of("file/Tunk-Extra-pixel-perfection-legacy.zip")),
        new Variant("pixel-perfection-fidelity","TUNG Pixel Perfection Fidelity","Tunk-Extra-pixel-perfection-fidelity.zip","R2.0-88.97",List.of("file/Tunk-Extra-pixel-perfection-fidelity.zip")),
        new Variant("faithless-pvp-edit","TUNG Faithless PvP Edit","Tunk-Extra-faithless-pvp-edit.zip","26.3",List.of("file/Tunk-Extra-faithless-pvp-edit.zip")),
        new Variant("short-swords-pack","Short Swords","Tunk-Extra-short-swords-pack.zip","1.2.0",List.of("file/Tunk-Extra-short-swords-pack.zip")),
        new Variant("pvp-crosshair","PvP Crosshair","Tunk-Extra-pvp-crosshair.zip","1.0",List.of("file/Tunk-Extra-pvp-crosshair.zip")),
        new Variant("oiiaoiia","cat OIIAOIIA totem (Cot)","Tunk-Extra-oiiaoiia.zip","1",List.of("file/Tunk-Extra-oiiaoiia.zip")),
        new Variant("dark-gui.","Dark GUI","Tunk-Extra-dark-gui.zip","26.3",List.of("file/Tunk-Extra-dark-gui.zip")),
        new Variant("small-shield-totem","Small Shield & Totem","Tunk-Extra-small-shield-totem.zip","2.11",List.of("file/Tunk-Extra-small-shield-totem.zip")),
        new Variant("dr-donut-meow","Dr Donut · Meow","Tung-DrDonut-Meow.zip","1.0.0 · meow on totem pop",List.of("file/Tung-DrDonut-Meow.zip")),
        new Variant("tung-frog","TUNG Emerald Frog","TUNG-Totem-frog.zip","16px · compact",List.of("file/TUNG-Totem-frog.zip")),
        new Variant("tung-amethyst","TUNG Amethyst Golem","TUNG-Totem-amethyst.zip","16px · compact",List.of("file/TUNG-Totem-amethyst.zip")),
        new Variant("tung-axolotl","TUNG Cyan Axolotl","TUNG-Totem-axolotl.zip","16px · compact",List.of("file/TUNG-Totem-axolotl.zip")),
        new Variant("tung-mushroom","TUNG Mushroom Sprite","TUNG-Totem-mushroom.zip","16px · compact",List.of("file/TUNG-Totem-mushroom.zip")),
        new Variant("tung-fox","TUNG Snow Fox","TUNG-Totem-fox.zip","16px · compact",List.of("file/TUNG-Totem-fox.zip")),
        new Variant("tung-bee","TUNG Golden Bee","TUNG-Totem-bee.zip","16px · compact",List.of("file/TUNG-Totem-bee.zip")),
        new Variant("tung-enderman","TUNG Tiny Enderman","TUNG-Totem-enderman.zip","16px · compact",List.of("file/TUNG-Totem-enderman.zip")),
        new Variant("tung-dragon","TUNG Ember Dragon","TUNG-Totem-dragon.zip","16px · compact",List.of("file/TUNG-Totem-dragon.zip")),
        new Variant("tux-totem","Tux Penguin","TUNG-Downloaded-tux-totem.zip","1.16",List.of("file/TUNG-Downloaded-tux-totem.zip")),
        new Variant("duck-totems","Duck Totem","TUNG-Downloaded-duck-totems.zip","1.21.11+",List.of("file/TUNG-Downloaded-duck-totems.zip")),
        new Variant("hamster-of-undying","Hamster of Undying","TUNG-Downloaded-hamster-of-undying.zip","0.0.1",List.of("file/TUNG-Downloaded-hamster-of-undying.zip")),
        new Variant("furry-fox-totem","Furry Fox","TUNG-Downloaded-furry-fox-totem.zip","1.1",List.of("file/TUNG-Downloaded-furry-fox-totem.zip")),
        new Variant("blinking-totem","Blinking Totem","TUNG-Downloaded-blinking-totem.zip","26.2-26.3",List.of("file/TUNG-Downloaded-blinking-totem.zip"))
    );
    private static Variant variant(String id, String name, String suffix, String version, String alias) {
        String file="Tunk-"+suffix+".zip";
        return new Variant(id,name,file,version,List.of("file/"+file,"file/TungPig-"+suffix+".zip","file/"+alias+".zip","file/"+alias));
    }
    private PackRepository repository;
    private boolean loading;
    private String error="", applying="";
    private int reloads;
    private long lastDurationMs;
    private long started, restoreStarted;
    private int operation;
    private boolean restoring, completed;
    private boolean initialized;
    private List<String> previous=List.of(),expected=List.of();

    public static Variant variant(String key) {
        return VARIANTS.stream().filter(v->v.id.equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown Pig pack variant"));
    }
    private static String compatibility(Variant v) { return "file/Tunk-"+v.id+"-Support.zip"; }
    private static boolean managed(String id) {
        return VARIANTS.stream().anyMatch(v->v.group().equals("pack")&&(v.aliases.contains(id)||compatibility(v).equals(id)||id.equals("file/TungPig-"+v.id+"-Compatibility.zip")));
    }
    /** Pure selection policy: keep user/server packs in order and add one Pig variant on top. */
    public static List<String> selection(Collection<String> current, String pack, String compatibility) {
        List<String> next=new ArrayList<>();
        for(String id:current)if(!managed(id)&&!next.contains(id))next.add(id);
        List<String> overlays=next.stream().filter(id->VARIANTS.stream().anyMatch(v->!v.group().equals("pack")&&v.aliases.contains(id))).toList();next.removeAll(overlays);
        boolean personal=next.remove(MediaPack.ID);
        if(pack!=null)next.add(pack);
        if(compatibility!=null)next.add(compatibility);
        next.addAll(overlays.stream().filter(id->VARIANTS.stream().anyMatch(v->v.group().equals("addon")&&v.aliases.contains(id))).toList());
        if(personal)next.add(MediaPack.ID);
        next.addAll(overlays.stream().filter(id->VARIANTS.stream().anyMatch(v->v.group().equals("totem")&&v.aliases.contains(id))).toList());
        next.addAll(overlays.stream().filter(id->VARIANTS.stream().anyMatch(v->AntiLeak.variant(v)&&v.aliases.contains(id))).toList());
        return AntiLeak.onTop(WhiteGlint.onTop(next));
    }
    private void discover(Minecraft c, boolean refresh) {
        var repo=c.getResourcePackRepository();
        if(repository!=repo||refresh){repository=repo;repository.reload();}
    }
    private String installed(Variant v) {
        return v.aliases.stream().filter(repository::isAvailable).findFirst().orElse(null);
    }
    public Map<String,Object> state(Minecraft c) {
        discover(c,false);
        if(!initialized&&c.isGameLoadFinished()) {
            initialized=true;
            if(repository.isAvailable(MediaPack.ID)&&(!repository.getSelectedIds().contains(MediaPack.ID)||repository.getSelectedIds().contains(MediaPack.TOTEM_SIZE_ID)))refreshMedia(c);
            else MediaPack.applySky();
        }
        // Minecraft's automatic shader-error recovery can abandon the public
        // reload future. Observe its finished overlay and real resource stack too.
        if(loading&&c.gui.overlay()==null&&System.nanoTime()-started>250_000_000L){
            if(completed)finish(started);
            else if(!restoring&&!selectionMatches(expected,repository.getSelectedIds()))restore(c);
            else if(restoring&&System.nanoTime()-restoreStarted>500_000_000L
                    &&selectionMatches(previous,repository.getSelectedIds()))finish(started);
        }
        Set<String> selected=new HashSet<>(repository.getSelectedIds());
        List<Map<String,Object>> rows=new ArrayList<>();String active="",totem="";
        for(var v:VARIANTS){
            String installed=installed(v);boolean on=v.aliases.stream().anyMatch(selected::contains);if(on){if(v.group().equals("totem"))totem=v.id;else if(v.group().equals("pack"))active=v.id;}
            rows.add(Map.of("id",v.id,"name",v.name,"group",v.group(),"version",v.version,"available",installed!=null,"active",on,
                "compatibility",installed==null?"missing":repository.getPack(installed).getCompatibility().toString()));
        }
        String remembered=ChunkFinderClient.CONFIG.pigPackVariant;
        if(!VARIANTS.stream().map(Variant::id).toList().contains(remembered))remembered="black";
        var result=new LinkedHashMap<String,Object>(Map.of("variants",rows,"totemVariant",totem,"enabled",!active.isEmpty(),"variant",active.isEmpty()?remembered:active,
            "loading",loading,"applying",applying,"error",error,"reloads",reloads,"lastDurationMs",lastDurationMs,
            "optimizations",Map.of("sodium",FabricLoader.getInstance().isModLoaded("sodium"),"lithium",FabricLoader.getInstance().isModLoaded("lithium"),"ferritecore",FabricLoader.getInstance().isModLoaded("ferritecore"),"immediatelyfast",FabricLoader.getInstance().isModLoaded("immediatelyfast"),"entityculling",FabricLoader.getInstance().isModLoaded("entityculling"),"dynamicfps",FabricLoader.getInstance().isModLoaded("dynamic_fps"),"krypton",FabricLoader.getInstance().isModLoaded("krypton"))));
        result.put("antiLeak",AntiLeak.state(c,repository.getSelectedIds(),loading));return result;
    }
    public void apply(Minecraft c, String key, boolean enabled) {
        Variant v=variant(key);
        if(v.group().equals("totem")){applyTotem(c,key,enabled);return;}
        if(v.group().equals("addon")||AntiLeak.variant(v)){applyAddon(c,key,enabled);return;}
        checkIdle();
        discover(c,true);
        String pack=enabled?installed(v):null;
        if(enabled&&pack==null)throw new IllegalArgumentException(v.name+" is not installed in this Minecraft profile.");
        String compat=enabled&&repository.isAvailable(compatibility(v))?compatibility(v):null;
        List<String> before=List.copyOf(repository.getSelectedIds()),next=selection(before,pack,compat);
        if(enabled)next=withoutConflictingAddons(c,next,v);
        try{MediaPack.build(v,enabled);}catch(java.io.IOException ex){throw new IllegalStateException("Cannot build personal texture settings",ex);}
        if(repository.isAvailable(MediaPack.ID)&&!next.contains(MediaPack.ID)){next=new ArrayList<>(next);next.add(MediaPack.ID);next=List.copyOf(next);}
        ChunkFinderClient.CONFIG.pigPackVariant=v.id;ChunkFinderClient.CONFIG.save();
        error="";
        if(before.equals(next))return;
        begin(c,before,next,enabled?v.name:"Vanilla / other packs");
    }
    public void applyTotem(Minecraft c,String key,boolean enabled){
        checkIdle();discover(c,true);Variant v=enabled?variant(key):null;
        if(v!=null&&!v.group().equals("totem"))throw new IllegalArgumentException("Choose a totem pack");
        String pack=v==null?null:installed(v);if(enabled&&pack==null)throw new IllegalArgumentException("Totem pack is not installed");
        var before=List.copyOf(repository.getSelectedIds());var next=new ArrayList<String>();
        for(String id:before)if(VARIANTS.stream().noneMatch(x->x.group().equals("totem")&&x.aliases.contains(id)))next.add(id);
        if(pack!=null)next.add(pack);
        if(!before.equals(next))begin(c,before,List.copyOf(next),enabled?v.name:"Default totem");
    }
    public void applyAddon(Minecraft c,String key,boolean enabled){
        if(AntiLeak.variant(variant(key))){applyAntiLeak(c,key,enabled);return;}
        checkIdle();discover(c,true);Variant v=variant(key);if(!v.group().equals("addon"))throw new IllegalArgumentException("Choose an add-on");
        String pack=installed(v);if(enabled&&pack==null)throw new IllegalArgumentException("Add-on is not installed");
        var before=List.copyOf(repository.getSelectedIds());var next=new ArrayList<String>();for(String id:before)if(!v.aliases.contains(id))next.add(id);
        if(enabled)next=new ArrayList<>(withoutConflictingAddons(c,next,v));
        var tails=next.stream().filter(id->id.equals(MediaPack.ID)||VARIANTS.stream().anyMatch(x->x.group().equals("totem")&&x.aliases.contains(id))).toList();next.removeAll(tails);if(enabled)next.add(pack);if(tails.contains(MediaPack.ID))next.add(MediaPack.ID);next.addAll(tails.stream().filter(id->!id.equals(MediaPack.ID)).toList());
        if(!before.equals(next))begin(c,before,List.copyOf(next),v.name+(enabled?" on":" off"));
    }
    public void applyAntiLeak(Minecraft c,String key,boolean enabled){
        checkIdle();discover(c,true);Variant v=variant(key);
        if(!AntiLeak.variant(v))throw new IllegalArgumentException("Choose an Anti-Leak theme");
        String pack=enabled?installed(v):null;if(enabled&&pack==null)throw new IllegalArgumentException("Anti-Leak theme is not installed");
        var before=List.copyOf(repository.getSelectedIds());var next=AntiLeak.selection(before,pack);
        if(!before.equals(next))begin(c,before,next,enabled?v.name:"Anti-Leak off");
    }
    public void checkIdle(){if(loading)throw new IllegalStateException("Textures are reloading. Wait for the current selection to finish.");}
    private static Set<String> assets(java.nio.file.Path path) throws java.io.IOException {
        try(var zip=new java.util.zip.ZipFile(path.toFile())){
            return zip.stream().filter(e->!e.isDirectory()&&e.getName().startsWith("assets/"))
                .map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
    private static List<String> withoutConflictingAddons(Minecraft c,List<String> selected,Variant incoming){
        // Explicit add-ons may overlay a main pack. A newly selected main pack wins
        // over older managed add-ons; unrelated user and required server packs stay intact.
        var root=FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        try{
            Set<String> incomingAssets=assets(root.resolve(incoming.file));
            var next=new ArrayList<>(selected);
            for(var other:VARIANTS)if(other.group().equals("addon")&&!other.id.equals(incoming.id)
                    &&!(incoming.group().equals("pack")&&other.id.equals(WhiteGlint.ID))
                    &&other.aliases.stream().anyMatch(selected::contains)){
                var file=root.resolve(other.file);
                if(java.nio.file.Files.isRegularFile(file)&&!Collections.disjoint(incomingAssets,assets(file)))next.removeAll(other.aliases);
            }
            return List.copyOf(next);
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot check resource-pack conflicts; selection was not changed",ex);}
    }
    public void refreshMedia(Minecraft c) {
        checkIdle();discover(c,true);
        String key=ChunkFinderClient.CONFIG.pigPackVariant;boolean enabled=false;
        for(var v:VARIANTS)if(v.group().equals("pack")&&v.aliases.stream().anyMatch(repository.getSelectedIds()::contains)){key=v.id;enabled=true;}
        try{MediaPack.build(variant(key),enabled);}catch(java.io.IOException ex){throw new IllegalStateException("Cannot build personal texture settings",ex);}
        repository.reload();
        List<String> before=List.copyOf(repository.getSelectedIds());var next=new ArrayList<>(before);next.remove(MediaPack.ID);
        int firstTotem=0;while(firstTotem<next.size()&&!isTotem(next.get(firstTotem)))firstTotem++;next.add(firstTotem,MediaPack.ID);
        begin(c,before,List.copyOf(next),"Your sky, crosshair and sounds");
    }
    private void begin(Minecraft c,List<String> before,List<String> requested,String label) {
        var normalized=new ArrayList<>(requested);normalized.remove(MediaPack.TOTEM_SIZE_ID);
        List<String> next=AntiLeak.onTop(WhiteGlint.onTop(normalized));
        loading=true;applying=label;error="";
        started=System.nanoTime();previous=before;expected=next;restoring=false;completed=false;int own=++operation;reloads++;
        try {
            storeSelection(c,next);
            c.reloadResourcePacks().whenComplete((unused,failure)->c.execute(()->{
                if(own!=operation||!loading)return;
                boolean selected=selectionMatches(next,repository.getSelectedIds());
                if(failure!=null||!selected){
                    restore(c);
                }else {completed=true;if(c.gui.overlay()==null)finish(started);}
            }));
        } catch(RuntimeException ex) {
            storeSelection(c,before);loading=false;applying="";
            error="Resource reload could not start: "+ex.getClass().getSimpleName();throw ex;
        }
    }
    private void restore(Minecraft c) {
        if(restoring)return;
        restoring=true;restoreStarted=System.nanoTime();int own=++operation;
        error="Minecraft could not load this pack. The previous resource selection was restored.";
        storeSelection(c,previous);reloads++;
        c.reloadResourcePacks().whenComplete((done,restoreError)->c.execute(()->{
            if(own!=operation||!loading)return;
            if(restoreError!=null)error+=" Minecraft reported an error while restoring resources.";
            completed=true;if(c.gui.overlay()==null)finish(started);
        }));
    }
    private static boolean selectionMatches(List<String> expected, Collection<String> actual) {
        // Required vanilla/server packs may be inserted by PackRepository.
        return actual.containsAll(expected)&&expected.stream().filter(TexturePacks::managed).toList().equals(actual.stream().filter(TexturePacks::managed).toList());
    }
    private static boolean isTotem(String id){return VARIANTS.stream().anyMatch(v->v.group().equals("totem")&&v.aliases.contains(id));}
    private void storeSelection(Minecraft c, List<String> ids) {
        repository.setSelected(ids);
        // Options.updateResourcePacks also starts a reload but discards its future.
        // Store the same vanilla option fields here, then start exactly one reload
        // whose completion/error can be reported to the desktop UI.
        c.options.resourcePacks.clear();c.options.incompatibleResourcePacks.clear();
        for(var pack:repository.getSelectedPacks())if(!pack.isFixedPosition()){
            c.options.resourcePacks.add(pack.getId());
            if(!pack.getCompatibility().isCompatible())c.options.incompatibleResourcePacks.add(pack.getId());
        }
        c.options.save();
        WhiteGlint.tick(c);
    }
    private void finish(long started) {lastDurationMs=(System.nanoTime()-started)/1_000_000;loading=false;restoring=false;completed=false;applying="";MediaPack.applySky();}
}
