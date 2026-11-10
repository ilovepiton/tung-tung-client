package dev.amethyst.finder.core;

import java.util.UUID;

/** A deliberately immutable observation, never a reference to a moving player. */
public record PinnedPlayer(String scope, String dimension, String playerId, String name,
                           int x, int y, int z, long capturedAt) {
    public PinnedPlayer {
        if (scope == null || scope.isBlank() || scope.length() > 256
                || dimension == null || dimension.isBlank() || dimension.length() > 256
                || name == null || name.isBlank() || name.length() > 64
                || playerId == null || capturedAt <= 0)
            throw new IllegalArgumentException("Invalid saved player position");
        UUID.fromString(playerId);
        if (Math.abs((long)x) > 30_000_000 || Math.abs((long)z) > 30_000_000)
            throw new IllegalArgumentException("Position is outside the world");
    }

    public static PinnedPlayer capture(String scope, String dimension, UUID id, String name,
                                       double x, double y, double z, long at) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Position is unavailable");
        return new PinnedPlayer(scope, dimension, id.toString(), name,
                (int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z), at);
    }

    public boolean belongsTo(String currentScope) { return scope.equals(currentScope); }
    public String coordinates() { return "X " + x + "  Y " + y + "  Z " + z; }
}
