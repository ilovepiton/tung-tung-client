package dev.amethyst.finder.history;

/** One observed stage advance, never an inferred number of ticks or proof of another player. */
public record GrowthTransition(long position, int previousStage, int currentStage,
        long observedAt, boolean continuous) {
    public GrowthTransition {
        if (previousStage < 1 || currentStage > 4 || currentStage <= previousStage || observedAt < 0)
            throw new IllegalArgumentException("Invalid observed growth transition");
    }
    public String status() { return continuous ? "GROWTH OBSERVED" : "STAGE ADVANCE ACROSS OBSERVATION GAP"; }
    public static String stageName(int stage) {
        return switch (stage) {
            case 1 -> "Small Amethyst Bud";
            case 2 -> "Medium Amethyst Bud";
            case 3 -> "Large Amethyst Bud";
            case 4 -> "Amethyst Cluster";
            default -> "Unavailable";
        };
    }
}
