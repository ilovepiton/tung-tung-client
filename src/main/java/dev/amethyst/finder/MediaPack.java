package dev.amethyst.finder;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;

/** A separate user-owned pack; creator archives are never rewritten. */
public final class MediaPack {
    public static final String TOTEM_SIZE_ID="file/TungTung-Totem-Size";
    public static final String ID = "file/TungTung-Personal";
    public static final Identifier SKY = Identifier.fromNamespaceAndPath("customskyboxes", "sky/tung.png");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static int filledSounds;
    private static String skyError = "";
    private static final String[] SOUND_FILES = {
        "item/totem/use_totem", "random/explode1", "random/explode2", "random/explode3", "random/explode4",
        "damage/hit1", "damage/hit2", "damage/hit3", "random/click", "random/click_stereo"
    };
    private MediaPack() {}
    public static Path media() { return FabricLoader.getInstance().getConfigDir().resolve("tun-tun-media"); }
    public static Path root() { return FabricLoader.getInstance().getGameDir().resolve("resourcepacks/TungTung-Personal"); }
    public static boolean skyAvailable() { return Files.isRegularFile(media().resolve("sky.png")); }
    public static boolean skyMod() { return FabricLoader.getInstance().isModLoaded("customskyboxes"); }
    public static Map<String,Object> state() {
        var cfg=ChunkFinderClient.CONFIG;
        return Map.of("skyEnabled",cfg.customSkyEnabled,"skyAvailable",skyAvailable(),"skyMod",skyMod(),
                "thinCrosshair",cfg.thinCrosshair,"fillSounds",cfg.packSoundsEnabled,"filledSounds",filledSounds,"error",skyError);
    }
    public static void prepare() {
        String key=ChunkFinderClient.CONFIG.pigPackVariant; boolean enabled=false;
        try {
            Path options=FabricLoader.getInstance().getGameDir().resolve("options.txt");
            if(Files.isRegularFile(options))for(String line:Files.readAllLines(options))if(line.startsWith("resourcePacks:")) {
                var ids=JsonParser.parseString(line.substring(14)).getAsJsonArray();
                for(var v:TexturePacks.VARIANTS)if(v.group().equals("pack"))for(var id:ids)if(v.aliases().contains(id.getAsString())){key=v.id();enabled=true;}
            }
            build(TexturePacks.variant(key),enabled);
        } catch(Exception ex) { ChunkFinderClient.LOGGER.warn("Cannot prepare personal textures",ex); }
    }
    private static void write(Path file,byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        if(Files.isSymbolicLink(file))throw new IOException("Personal texture path is a symbolic link");
        if(Files.isRegularFile(file)&&Arrays.equals(Files.readAllBytes(file),bytes))return;
        Path tmp=file.resolveSibling(file.getFileName()+".pending");Files.write(tmp,bytes);Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    public static void build(TexturePacks.Variant variant,boolean enabled) throws IOException {
        // Item models, including the fixed transform used by the pop animation,
        // belong to the selected resource pack. Do not replace them globally.
        Path root=root();if(Files.isSymbolicLink(root))throw new IOException("Personal pack folder is a symbolic link");
        write(root.resolve("pack.mcmeta"),"{\"pack\":{\"description\":\"Tung Tung · personal sky, crosshair and missing sounds\",\"min_format\":97,\"max_format\":97}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path crosshair=root.resolve("assets/minecraft/textures/gui/sprites/hud/crosshair.png");
        if(ChunkFinderClient.CONFIG.thinCrosshair) {
            try(var in=MediaPack.class.getResourceAsStream("/assets/amethyst_chunk_finder/textures/gui/thin_crosshair.png")) {write(crosshair,Objects.requireNonNull(in).readAllBytes());}
        } else Files.deleteIfExists(crosshair);
        Path sky=root.resolve("assets/customskyboxes/sky/tung.png");
        if(skyAvailable())write(sky,Files.readAllBytes(media().resolve("sky.png")));else Files.deleteIfExists(sky);
        fillSounds(root,variant,enabled);
    }
    private static void fillSounds(Path root,TexturePacks.Variant variant,boolean enabled) throws IOException {
        for(String name:SOUND_FILES)Files.deleteIfExists(root.resolve("assets/minecraft/sounds/"+name+".ogg"));
        Files.deleteIfExists(root.resolve("assets/minecraft/sounds.json"));filledSounds=0;
        Path packs=root.getParent(),source=packs.resolve("Tunk-Black.zip"),target=packs.resolve(variant.file());
        if(!enabled||!ChunkFinderClient.CONFIG.packSoundsEnabled||variant.id().equals("black")||!Files.isRegularFile(source)||!Files.isRegularFile(target))return;
        try(var from=new ZipFile(source.toFile());var to=new ZipFile(target.toFile())) {
            Set<String> targetFiles=new HashSet<>();to.stream().filter(e->!e.isDirectory()).forEach(e->targetFiles.add(e.getName()));
            for(String name:SOUND_FILES) {
                String rel="assets/minecraft/sounds/"+name+".ogg";
                if(targetFiles.stream().anyMatch(n->n.equals(rel)||n.endsWith("/"+rel)))continue;
                var entry=from.getEntry(rel);if(entry==null||entry.getSize()>2_000_000)continue;
                try(var in=from.getInputStream(entry)){write(root.resolve(rel),in.readNBytes(2_000_001));filledSounds++;}
            }
            var targetJson=new JsonObject();
            for(String name:targetFiles)if(name.equals("assets/minecraft/sounds.json")||name.endsWith("/assets/minecraft/sounds.json")) {
                try(var in=to.getInputStream(to.getEntry(name))){var parsed=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();parsed.entrySet().forEach(e->targetJson.add(e.getKey(),e.getValue()));}
            }
            var fallback=new JsonObject();
            try(var in=from.getInputStream(from.getEntry("assets/minecraft/sounds.json"))) {
                var original=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(var entry:original.entrySet())if(!targetJson.has(entry.getKey())) {
                    var value=entry.getValue().deepCopy().getAsJsonObject();value.addProperty("replace",true);fallback.add(entry.getKey(),value);
                }
            }
            if(!fallback.isEmpty())write(root.resolve("assets/minecraft/sounds.json"),JSON.toJson(fallback).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    /** Uses the mod's public local API. Never sends a command to a multiplayer server. */
    public static void applySky() {
        skyError="";if(!skyMod())return;
        try {
            Class<?> api=Class.forName("org.cotii.customskyboxes.api.CustomSkyboxesAPI"),type=Class.forName("org.cotii.customskyboxes.client.sky.SkyboxType");
            Object sky=type.getField("SKY").get(null);
            if(ChunkFinderClient.CONFIG.customSkyEnabled&&skyAvailable()) {
                Object result=api.getMethod("set",type,Identifier.class,String.class).invoke(null,sky,SKY,"opacity=1 fadein=0 fadeout=0 smooth_edges=true");
                if(!Boolean.TRUE.equals(result))throw new IllegalStateException("Sky settings were rejected");
            } else {
                Object active=api.getMethod("active",type).invoke(null,sky);
                if(active!=null&&SKY.equals(active.getClass().getMethod("texture").invoke(active)))api.getMethod("clear",type).invoke(null,sky);
            }
        } catch(ReflectiveOperationException|RuntimeException ex) {skyError="Custom sky could not be applied. "+ex.getClass().getSimpleName();ChunkFinderClient.LOGGER.warn(skyError,ex);}
    }
}
