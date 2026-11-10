package dev.amethyst.finder.history;

import java.util.Map;

/** A completed raw block scan. Player positions and live/session flags deliberately have no fields. */
public record ObservedChunk(int chunkX, int chunkZ, long observedAt,
        Map<String, Integer> signalCounts, Map<String, Integer> representativeY,
        Map<String, Integer> patternCounts, Map<Long, Integer> budStages) {
    public ObservedChunk {
        if (observedAt < 0) throw new IllegalArgumentException("Negative observation time");
        signalCounts = HistoryBounds.integers(signalCounts, 64, true);
        representativeY = HistoryBounds.integers(representativeY, 64, false);
        patternCounts = HistoryBounds.integers(patternCounts, 16, true);
        budStages = HistoryBounds.stages(budStages, ObservationMemory.MAX_STAGES_PER_CHUNK, chunkX, chunkZ);
    }
    public ObservedChunk(int chunkX, int chunkZ, long observedAt, Map<String, Integer> signalCounts,
            Map<String, Integer> representativeY, Map<Long, Integer> budStages) {
        this(chunkX, chunkZ, observedAt, signalCounts, representativeY, Map.of(), budStages);
    }
}
