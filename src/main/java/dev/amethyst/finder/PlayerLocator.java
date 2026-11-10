package dev.amethyst.finder;

import com.google.gson.Gson;
import dev.amethyst.finder.core.PinnedPlayer;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** The tab list supplies names; only currently received entities supply positions. */
public final class PlayerLocator {
    private static final Gson JSON = new Gson();
    private PinnedPlayer saved;
    public record Row(String id, String name, boolean observable, boolean self, Integer x, Integer y, Integer z, Long distance, String face) {}

    public PlayerLocator() {
        if (Files.isRegularFile(path())) try { saved = JSON.fromJson(Files.readString(path()), PinnedPlayer.class); }
        catch (IOException | RuntimeException error) { ChunkFinderClient.LOGGER.warn("Cannot load Find Player waypoint", error); }
    }
    private static Path path() { return FinderConfig.path().resolveSibling("tun-tun-find-player.json"); }
    private static String scope(Minecraft client) {
        var identity = client.level == null ? null : HistoryContext.resolve(client, client.level, ChunkFinderClient.CONFIG);
        return identity == null ? "" : identity.fileId();
    }
    public PinnedPlayer saved() { return saved; }
    public PinnedPlayer active(Minecraft client) { return saved != null && saved.belongsTo(scope(client)) ? saved : null; }

    public List<Row> rows(Minecraft client) {
        if (client.getConnection() == null || client.level == null || client.player == null) return List.of();
        List<Row> rows = new ArrayList<>();
        for (var info : client.getConnection().getOnlinePlayers()) {
            var profile = info.getProfile();
            var player = client.level.getPlayerByUUID(profile.id());
            boolean visible = player != null && !player.isRemoved();
            var pos = visible ? player.blockPosition() : null;
            rows.add(new Row(profile.id().toString(), profile.name(), visible,
                    profile.id().equals(client.player.getUUID()), pos == null ? null : pos.getX(),
                    pos == null ? null : pos.getY(), pos == null ? null : pos.getZ(),
                    visible ? Math.round((double)player.distanceTo(client.player)) : null, PlayerFaces.face(profile)));
        }
        rows.sort(Comparator.comparing((Row r) -> !r.observable()).thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(rows.stream().limit(512).toList());
    }

    public PinnedPlayer pin(Minecraft client, String idOrName) throws IOException {
        if (client.level == null || client.player == null) throw new IllegalArgumentException("Join a world first.");
        Row row = rows(client).stream().filter(r -> r.id().equals(idOrName) || r.name().equalsIgnoreCase(idOrName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not in the current player list."));
        var player = client.level.getPlayerByUUID(UUID.fromString(row.id()));
        if (player == null || player.isRemoved())
            throw new IllegalArgumentException("This player's position is not received by your game. Try when they are nearby.");
        PinnedPlayer next = PinnedPlayer.capture(scope(client), client.level.dimension().toString(), player.getUUID(),
                player.getName().getString(), player.getX(), player.getY(), player.getZ(), System.currentTimeMillis());
        write(next);
        saved = next;
        return next;
    }
    public void clear() throws IOException { write(null); saved = null; }
    private void write(PinnedPlayer next) throws IOException {
        Path target = path(), temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(target.getParent());
        Files.writeString(temporary, JSON.toJson(next));
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
