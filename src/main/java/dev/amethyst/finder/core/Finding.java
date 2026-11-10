package dev.amethyst.finder.core;

import java.util.List;

public record Finding(ChunkEvidence evidence, int score, Kind kind, List<String> reasons,
                      int y, int disappearedClusters, int observedGrowth) {
    public enum Kind { ACTIVITY, GEODE, MATCH }
    public int x() { return evidence.chunkX * 16 + 8; }
    public int z() { return evidence.chunkZ * 16 + 8; }
    public long key() { return ChunkEvidence.key(evidence.chunkX, evidence.chunkZ); }
    public double distance(double x, double z) { return Math.hypot(x() - x, z() - z); }
}
