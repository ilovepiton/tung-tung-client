package dev.amethyst.finder.core;

import dev.amethyst.finder.history.GrowthTransition;
import java.util.List;
import java.util.Map;

/** Immutable observation for the in-game chunk view. LIVE means a completed scan of the current chunk instance. */
public record ChunkAnalysis(int chunkX, int chunkZ, long observedAtMillis, Status status,
        Map<Signal, Integer> counts, int storageCount, int amethystCount, int spawnerCount,
        StructureEvidence evidence, String summary, int growthEvents, int previousGrowthStage,
        int currentGrowthStage, long lastGrowthAtMillis, int continuousGrowthEvents, int gapGrowthEvents,
        List<GrowthTransition> growthTransitions, boolean simulationActivityPossible) {
    public enum Status { LIVE, HISTORICAL, VERIFYING }
    public enum StructureEvidence { NONE, WEAK, MODERATE, STRONG }
    public ChunkAnalysis {
        counts = Map.copyOf(counts);
        growthTransitions = List.copyOf(growthTransitions);
    }
    public long key() { return ChunkEvidence.key(chunkX, chunkZ); }
    public int count(Signal signal) { return counts.getOrDefault(signal, 0); }
    public int craftingCount() { return count(Signal.CRAFTING) + count(Signal.ANVIL) + count(Signal.ENCHANTING); }
    public int redstoneCount() { return count(Signal.REDSTONE); }
    public boolean activityVerified() { return false; } // No authorized server diagnostics are connected.
    public static String stageName(int stage) {
        return switch (stage) {
            case 1 -> "Small Amethyst Bud"; case 2 -> "Medium Amethyst Bud";
            case 3 -> "Large Amethyst Bud"; case 4 -> "Amethyst Cluster"; default -> "Unavailable";
        };
    }
}
