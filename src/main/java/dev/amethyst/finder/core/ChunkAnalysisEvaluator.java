package dev.amethyst.finder.core;

import java.util.ArrayList;
import java.util.List;

/** Rates combinations of observed block patterns; no result asserts who built them. */
public final class ChunkAnalysisEvaluator {
    private ChunkAnalysisEvaluator() {}
    public static ChunkAnalysis.StructureEvidence structure(ChunkEvidence e) {
        int storage = e.totalStorage();
        int crafting = e.count(Signal.CRAFTING) + e.count(Signal.ANVIL) + e.count(Signal.ENCHANTING);
        int redstone = e.count(Signal.REDSTONE);
        int rows = e.patternCount("repeated_rows"), infrastructureRows = e.patternCount("infrastructure_rows");
        if (storage >= 6 && crafting > 0 && redstone > 0 && infrastructureRows >= 2
                || storage >= 4 && crafting > 0 && redstone >= 2 && rows >= 4)
            return ChunkAnalysis.StructureEvidence.STRONG;
        int independentGroups = (storage > 0 ? 1 : 0) + (crafting > 0 ? 1 : 0) + (redstone > 0 ? 1 : 0);
        if (independentGroups >= 2 && (storage >= 3 || infrastructureRows >= 2 || rows >= 2))
            return ChunkAnalysis.StructureEvidence.MODERATE;
        if (storage > 0 || crafting > 0 || redstone > 0 || rows > 0 || e.count(Signal.BEACON) > 0)
            return ChunkAnalysis.StructureEvidence.WEAK;
        // A mob/trial spawner or natural geode, alone, is not construction evidence.
        return ChunkAnalysis.StructureEvidence.NONE;
    }
    public static String summary(ChunkEvidence e) {
        List<String> parts = new ArrayList<>();
        if (e.totalStorage() > 0) parts.add("Storage " + e.totalStorage());
        int crafting = e.count(Signal.CRAFTING) + e.count(Signal.ANVIL) + e.count(Signal.ENCHANTING);
        if (crafting > 0) parts.add("Crafting " + crafting);
        if (e.count(Signal.REDSTONE) > 0) parts.add("Redstone " + e.count(Signal.REDSTONE));
        if (e.patternCount("repeated_rows") > 0) parts.add("Repeated rows " + e.patternCount("repeated_rows"));
        if (e.patternCount("infrastructure_rows") > 0)
            parts.add("Infrastructure rows " + e.patternCount("infrastructure_rows"));
        if (parts.isEmpty()) return "No construction pattern observed";
        return String.join(" · ", parts);
    }
}
