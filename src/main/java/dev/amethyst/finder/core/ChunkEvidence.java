package dev.amethyst.finder.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/** A completed client-side observation, never a probability or a server history. */
public final class ChunkEvidence {
    private final int[] counts;
    private final Set<Long> clusters;
    private final int[] representativeY;
    private final List<BlockMark> marks;
    private final Map<Long, Integer> growthStages;
    private final Map<String, Integer> patterns;
    public final int chunkX, chunkZ;
    public final long observedAt;

    private ChunkEvidence(Builder b, long observedAt) {
        this.chunkX = b.chunkX;
        this.chunkZ = b.chunkZ;
        this.counts = b.counts.clone();
        this.clusters = Set.copyOf(b.clusters);
        this.representativeY = b.representativeY.clone();
        this.observedAt = observedAt;
        this.marks = b.marks.snapshot();
        this.growthStages = Map.copyOf(b.growthStages);
        this.patterns = Map.of("construction_blocks", count(Signal.CONSTRUCTION),
                "repeated_rows", b.rowCount(b.constructionX, 4) + b.rowCount(b.constructionZ, 4),
                "infrastructure_rows", b.rowCount(b.infrastructureX, 2) + b.rowCount(b.infrastructureZ, 2));
    }
    private ChunkEvidence(ChunkEvidence blocks, List<BlockMark> stands) {
        chunkX = blocks.chunkX; chunkZ = blocks.chunkZ; observedAt = blocks.observedAt;
        counts = blocks.counts.clone(); representativeY = blocks.representativeY.clone();
        clusters = blocks.clusters; growthStages = blocks.growthStages; patterns = blocks.patterns;
        counts[Signal.ARMOR_STAND.ordinal()] = 0;
        representativeY[Signal.ARMOR_STAND.ordinal()] = Integer.MIN_VALUE;
        List<BlockMark> combined = new ArrayList<>();
        for (BlockMark mark : blocks.marks) if (mark.signal() != Signal.ARMOR_STAND) combined.add(mark);
        for (BlockMark mark : stands) {
            if (mark.signal() != Signal.ARMOR_STAND || (mark.x() >> 4) != chunkX || (mark.z() >> 4) != chunkZ) continue;
            counts[Signal.ARMOR_STAND.ordinal()]++;
            if (representativeY[Signal.ARMOR_STAND.ordinal()] == Integer.MIN_VALUE)
                representativeY[Signal.ARMOR_STAND.ordinal()] = mark.y();
            if (combined.size() < MarkerReservoir.LIMIT + 128) combined.add(mark);
        }
        marks = List.copyOf(combined);
    }
    /** Fresh entity observations replace the prior stand count; raw block history is untouched. */
    public ChunkEvidence withArmorStands(List<BlockMark> stands) { return new ChunkEvidence(this, stands); }

    public int count(Signal s) { return counts[s.ordinal()]; }
    public Map<String, Integer> signalCounts() {
        Map<String, Integer> result = new HashMap<>();
        for (Signal signal : Signal.values()) if (signal != Signal.ARMOR_STAND && count(signal) > 0)
            result.put(signal.name(), count(signal));
        return Map.copyOf(result);
    }
    public Map<String, Integer> representativeHeights() {
        Map<String, Integer> result = new HashMap<>();
        for (Signal signal : Signal.values()) if (signal != Signal.ARMOR_STAND && count(signal) > 0)
            result.put(signal.name(), representativeY(signal));
        return Map.copyOf(result);
    }
    public Map<Long, Integer> growthStages() { return growthStages; }
    public Map<String, Integer> patternCounts() { return patterns; }
    /** Bounded baseline trimming keeps counts, patterns and display marks while only forgetting old per-position comparisons. */
    public ChunkEvidence withoutGrowthBaseline() { return new ChunkEvidence(this); }
    private ChunkEvidence(ChunkEvidence source) {
        chunkX = source.chunkX; chunkZ = source.chunkZ; observedAt = source.observedAt;
        counts = source.counts; representativeY = source.representativeY;
        marks = source.marks; patterns = source.patterns;
        growthStages = Map.of(); clusters = Set.of();
    }
    public int patternCount(String name) { return patterns.getOrDefault(name, 0); }
    public int totalStorage() {
        int total = 0;
        for (Signal signal : Signal.values()) if (signal.isStorage()) total += count(signal);
        return total;
    }
    /** Restore factual block history only. Live entities and render markers are deliberately absent. */
    public static ChunkEvidence restore(int chunkX, int chunkZ, long observedAt,
            Map<String, Integer> counts, Map<String, Integer> heights, Map<String, Integer> patterns,
            Map<Long, Integer> stages) {
        Builder b = new Builder(chunkX, chunkZ);
        for (Signal signal : Signal.values()) if (signal != Signal.ARMOR_STAND) {
            b.counts[signal.ordinal()] = Math.max(0, counts.getOrDefault(signal.name(), 0));
            b.representativeY[signal.ordinal()] = heights.getOrDefault(signal.name(), Integer.MIN_VALUE);
        }
        for (var stage : stages.entrySet()) if (stage.getValue() >= 1 && stage.getValue() <= 4) {
            b.growthStages.put(stage.getKey(), stage.getValue());
            if (stage.getValue() == 4) b.clusters.add(stage.getKey());
        }
        return new ChunkEvidence(b, observedAt, patterns);
    }
    private ChunkEvidence(Builder builder, long observedAt, Map<String, Integer> savedPatterns) {
        chunkX = builder.chunkX; chunkZ = builder.chunkZ; this.observedAt = observedAt;
        counts = builder.counts.clone(); representativeY = builder.representativeY.clone();
        clusters = Set.copyOf(builder.clusters); growthStages = Map.copyOf(builder.growthStages);
        marks = List.of(); patterns = Map.copyOf(savedPatterns);
    }
    public List<BlockMark> marks() { return marks; }
    public int totalAmethyst() {
        return count(Signal.AMETHYST) + count(Signal.BUDDING) + count(Signal.BUD) + count(Signal.CLUSTER);
    }
    public int representativeY(Signal s) { return representativeY[s.ordinal()]; }
    public int disappearedClusters(ChunkEvidence previous) {
        if (previous == null || previous.chunkX != chunkX || previous.chunkZ != chunkZ || previous.observedAt >= observedAt) return 0;
        int removed = 0;
        for (long p : previous.clusters) if (!clusters.contains(p)) removed++;
        return removed;
    }
    /** Only an existing bud advancing at the same position counts; first-seen clusters have unknown history. */
    public int advancedBuds(ChunkEvidence previous) {
        if (previous == null || previous.chunkX != chunkX || previous.chunkZ != chunkZ || previous.observedAt >= observedAt) return 0;
        int advanced = 0;
        for (var entry : growthStages.entrySet()) {
            Integer before = previous.growthStages.get(entry.getKey());
            if (before != null && entry.getValue() > before) advanced++;
        }
        return advanced;
    }
    public static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    public static long blockKey(int x, int y, int z) {
        return ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
    }

    public static final class Builder {
        private final int chunkX, chunkZ;
        private final int[] counts = new int[Signal.values().length];
        private final int[] representativeY = new int[Signal.values().length];
        private final Set<Long> clusters = new HashSet<>();
        private final MarkerReservoir marks = new MarkerReservoir();
        private final Map<Long, Integer> growthStages = new HashMap<>();
        private final Set<Signal> markerTargets;
        private final Map<Long, Integer> constructionX = new HashMap<>(), constructionZ = new HashMap<>();
        private final Map<Long, Integer> infrastructureX = new HashMap<>(), infrastructureZ = new HashMap<>();

        public Builder(int chunkX, int chunkZ) {
            this(chunkX, chunkZ, null);
        }
        public Builder(int chunkX, int chunkZ, Set<Signal> markerTargets) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.markerTargets = markerTargets == null ? null : Set.copyOf(markerTargets);
            Arrays.fill(representativeY, Integer.MIN_VALUE);
        }
        public void observe(Signal s, int x, int y, int z) {
            counts[s.ordinal()]++;
            if (representativeY[s.ordinal()] == Integer.MIN_VALUE) representativeY[s.ordinal()] = y;
            if (s == Signal.CLUSTER && clusters.size() < 4096) clusters.add(blockKey(x, y, z));
            if (s == Signal.CONSTRUCTION) {
                row(constructionX, y, z, x); row(constructionZ, y, x, z);
            }
            if (s.isInfrastructure()) {
                row(infrastructureX, y, z, x); row(infrastructureZ, y, x, z);
            }
            if ((FinderTargets.key(s) != null || s.isAmethyst() || s.isStorage())
                    && (markerTargets == null || markerTargets.contains(s)))
                marks.add(new BlockMark(s, x, y, z));
        }
        private static void row(Map<Long, Integer> rows, int y, int fixed, int variable) {
            long key = ((long)y << 4) | (fixed & 15);
            rows.merge(key, 1 << (variable & 15), (a, b) -> a | b);
        }
        private int rowCount(Map<Long, Integer> rows, int minimumLength) {
            int count = 0;
            for (int mask : rows.values()) {
                int run = 0;
                for (int i = 0; i < 16; i++) {
                    if ((mask & (1 << i)) != 0) run++;
                    else { if (run >= minimumLength) count++; run = 0; }
                }
                if (run >= minimumLength) count++;
            }
            return count;
        }
        public void observeGrowthStage(int stage, int x, int y, int z) {
            if (stage >= 1 && stage <= 4 && growthStages.size() < 4096)
                growthStages.put(blockKey(x, y, z), stage);
        }
        public ChunkEvidence finish(long observedAt) { return new ChunkEvidence(this, observedAt); }
    }
}
