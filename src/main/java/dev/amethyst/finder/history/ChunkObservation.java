package dev.amethyst.finder.history;

import java.util.List;
import java.util.Map;

/** Stored facts only. A record loaded from disk is historical until a new client scan completes. */
public record ChunkObservation(int chunkX, int chunkZ, long firstObservedAt, long observedAt,
        Map<String, Integer> signalCounts, Map<String, Integer> representativeY,
        Map<String, Integer> patternCounts, Map<Long, Integer> budStages,
        List<GrowthTransition> recentGrowth, long continuousGrowthEvents, long gapGrowthEvents) {
    public ChunkObservation {
        if (firstObservedAt < 0 || observedAt < firstObservedAt || continuousGrowthEvents < 0 || gapGrowthEvents < 0)
            throw new IllegalArgumentException("Invalid observation chronology");
        signalCounts = HistoryBounds.integers(signalCounts, 64, true);
        representativeY = HistoryBounds.integers(representativeY, 64, false);
        patternCounts = HistoryBounds.integers(patternCounts, 16, true);
        budStages = HistoryBounds.stages(budStages, ObservationMemory.MAX_STAGES_PER_CHUNK, chunkX, chunkZ);
        recentGrowth = recentGrowth == null ? List.of() : List.copyOf(recentGrowth);
        if (recentGrowth.size() > ObservationMemory.MAX_RECENT_GROWTH)
            recentGrowth = List.copyOf(recentGrowth.subList(recentGrowth.size() - ObservationMemory.MAX_RECENT_GROWTH,
                    recentGrowth.size()));
        for (var event : recentGrowth) {
            if (event.observedAt() < firstObservedAt || event.observedAt() > observedAt
                    || !HistoryBounds.belongs(event.position(), chunkX, chunkZ))
                throw new IllegalArgumentException("Growth event is outside observation times");
        }
    }
    public long key() { return key(chunkX, chunkZ); }
    public static long key(int chunkX, int chunkZ) { return ((long) chunkX << 32) | (chunkZ & 0xffffffffL); }
    public long growthEvents() {
        return continuousGrowthEvents > Long.MAX_VALUE - gapGrowthEvents ? Long.MAX_VALUE
                : continuousGrowthEvents + gapGrowthEvents;
    }
    ChunkObservation withStages(Map<Long, Integer> stages) {
        return new ChunkObservation(chunkX, chunkZ, firstObservedAt, observedAt, signalCounts,
                representativeY, patternCounts, stages, recentGrowth, continuousGrowthEvents, gapGrowthEvents);
    }
}
