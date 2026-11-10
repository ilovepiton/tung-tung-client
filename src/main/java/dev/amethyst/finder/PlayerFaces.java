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

/** Small, bounded face cache. Downloads only the skin already advertised in the tab profile. */
public final class PlayerFaces {
    private record Entry(String image, long retryAfter) {}
    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
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
