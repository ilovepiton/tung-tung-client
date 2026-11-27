package dev.amethyst.finder;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Bounded face cache for tab-list skins and saved friends’ official public profiles. */
public final class PlayerFaces {
    private record Entry(String image, long retryAfter) {}
    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Entry> NAMES = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService NAME_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task,"tung-friend-face"); thread.setDaemon(true); return thread;
    });
    private static final ConcurrentHashMap<String, String> DEFAULTS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "tung-player-face"); thread.setDaemon(true); return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private PlayerFaces() {}

    public static String face(GameProfile profile) {
        String url = skinUrl(profile);
        if (url.isEmpty()) return defaultFace(profile);
        Entry found = CACHE.get(url);
        if (found != null && (found.retryAfter == 0 || System.currentTimeMillis() < found.retryAfter)) return found.image;
        if (CACHE.size() >= 512 && found == null) return "";
        Entry pending = new Entry("", Long.MAX_VALUE);
        boolean start = found == null ? CACHE.putIfAbsent(url, pending) == null : CACHE.replace(url, found, pending);
        if (start) WORKERS.execute(() -> load(url));
        return found == null ? "" : found.image;
    }

    /** Online server skins take priority; saved offline names use official public profiles asynchronously. */
    public static String friendFace(String name,String advertised) {
        String key=profileKey(name);
        if(key.isEmpty())return "";
        if(advertised!=null&&!advertised.isEmpty()){
            if(NAMES.size()<256||NAMES.containsKey(key))NAMES.put(key,new Entry(advertised,System.currentTimeMillis()+3600000));
            return advertised;
        }
        Entry found=NAMES.get(key);long now=System.currentTimeMillis();
        if(found!=null&&now<found.retryAfter)return found.image;
        if(found==null&&NAMES.size()>=256)return "";
        Entry pending=new Entry(found==null?"":found.image,Long.MAX_VALUE);
        boolean start=found==null?NAMES.putIfAbsent(key,pending)==null:NAMES.replace(key,found,pending);
        if(start)NAME_WORKER.execute(()->loadName(key,pending));
        return pending.image;
    }
    static String profileKey(String name){
        if(name==null)return "";String value=name.trim().toLowerCase(java.util.Locale.ROOT);
        return value.matches("[a-z0-9_]{1,16}")||value.matches("[a-f0-9]{32}")||value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")?value:"";
    }
    static String profileId(com.google.gson.JsonObject data){
        try{String id=data.get("id").getAsString();return id.matches("[a-fA-F0-9]{32}")?id.toLowerCase(java.util.Locale.ROOT):"";}catch(RuntimeException e){return "";}
    }
    static String sessionSkinUrl(com.google.gson.JsonObject data){
        try{for(var value:data.getAsJsonArray("properties")){
            var property=value.getAsJsonObject();if(!"textures".equals(property.get("name").getAsString()))continue;
            String encoded=property.get("value").getAsString();if(encoded.length()>16384)continue;
            var decoded=JsonParser.parseString(new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8)).getAsJsonObject();
            String url=decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            if(url.matches("https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128}"))return url.replaceFirst("^http:","https:");
        }}catch(RuntimeException ignored){}return "";
    }
    private static byte[] download(String url,int maximum)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()){
            if(response.statusCode()!=200)throw new java.io.IOException("Profile or skin unavailable");
            byte[] bytes=stream.readNBytes(maximum+1);if(bytes.length>maximum)throw new java.io.IOException("Response too large");return bytes;
        }
    }
    private static void loadName(String name,Entry pending){
        try{
            String id=name.replace("-","");
            if(!id.matches("[a-f0-9]{32}")){
                var profile=JsonParser.parseString(new String(download("https://api.mojang.com/users/profiles/minecraft/"+name,65536),StandardCharsets.UTF_8)).getAsJsonObject();
                id=profileId(profile);if(id.isEmpty())throw new java.io.IOException("Profile unavailable");
            }
            var session=JsonParser.parseString(new String(download("https://sessionserver.mojang.com/session/minecraft/profile/"+id,65536),StandardCharsets.UTF_8)).getAsJsonObject();
            if(!id.equals(profileId(session)))throw new java.io.IOException("Profile mismatch");
            String url=sessionSkinUrl(session);if(url.isEmpty())throw new java.io.IOException("No published skin");
            String image=crop(download(url,262144));NAMES.replace(name,pending,new Entry(image,System.currentTimeMillis()+3600000));
        }catch(Exception ignored){NAMES.replace(name,pending,new Entry(pending.image,System.currentTimeMillis()+600000));}
    }

    private static String defaultFace(GameProfile profile) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var path = net.minecraft.client.resources.DefaultPlayerSkin.get(profile).body().texturePath();
        String key = path.toString();
        if (DEFAULTS.putIfAbsent(key, "") == null) {
            var resources = client.getResourceManager();
            WORKERS.execute(() -> {
                try (var input = resources.open(path)) {
                    DEFAULTS.put(key, crop(input.readNBytes(262144)));
                } catch (Exception ignored) { DEFAULTS.remove(key); }
            });
        }
        return DEFAULTS.getOrDefault(key, "");
    }

    static String skinUrl(GameProfile profile) {
        try {
            for (var property : profile.properties().get("textures")) {
                if (property.value().length() > 16384) continue;
                String json = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
                String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                        .getAsJsonObject("SKIN").get("url").getAsString();
                if (url.matches("https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]{32,128}"))
                    return url.replaceFirst("^http:", "https:");
            }
        } catch (RuntimeException ignored) {}
        return "";
    }

    private static void load(String url) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() != 200) throw new IllegalStateException("Skin unavailable");
                byte[] bytes = stream.readNBytes(262145);
                if (bytes.length > 262144) throw new IllegalStateException("Skin too large");
                CACHE.put(url, new Entry(crop(bytes), 0));
            }
        } catch (Exception ignored) { CACHE.put(url, new Entry("", System.currentTimeMillis() + 60000)); }
    }

    static String crop(byte[] bytes) throws java.io.IOException {
        BufferedImage skin = ImageIO.read(new ByteArrayInputStream(bytes));
        if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32))
            throw new java.io.IOException("Unsupported skin size");
        BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        var graphics = face.createGraphics();
        try {
            graphics.drawImage(skin, 0, 0, 8, 8, 8, 8, 16, 16, null);
            graphics.drawImage(skin, 0, 0, 8, 8, 40, 8, 48, 16, null);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(face, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
