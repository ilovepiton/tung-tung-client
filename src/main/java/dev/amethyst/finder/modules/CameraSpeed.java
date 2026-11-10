package dev.amethyst.finder.modules;

/** Detached camera speed in blocks per client tick, with the normal sprint-key boost. */
public final class CameraSpeed {
    public static final double DEFAULT = 0.6;
    public static final double MIN = 0.025;
    public static final double MAX = 128;
    public static final double SPRINT_MULTIPLIER = 3;
    public static final double TICKS_PER_SECOND = 20;

    private CameraSpeed() { }

    public static double sanitize(double speed) {
        return Math.clamp(Double.isFinite(speed) ? speed : DEFAULT, MIN, MAX);
    }

    public static double effectiveBlocksPerTick(double configuredSpeed, boolean sprinting) {
        return sanitize(configuredSpeed) * (sprinting ? SPRINT_MULTIPLIER : 1);
    }

    public static double blocksPerSecond(double configuredSpeed, boolean sprinting) {
        return effectiveBlocksPerTick(configuredSpeed, sprinting) * TICKS_PER_SECOND;
    }
}
