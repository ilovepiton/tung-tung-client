package dev.amethyst.finder.core;

import java.util.ArrayList;
import java.util.List;

public final class EvidenceScorer {
    private EvidenceScorer() {}

    public static Finding evaluate(ChunkEvidence e, ScanOptions options, int removedClusters) {
        return evaluate(e, options, removedClusters, 0);
    }
    public static Finding evaluate(ChunkEvidence e, ScanOptions options, int removedClusters, int grownBuds) {
        List<String> reasons = new ArrayList<>();
        int score = 0;
        Signal primary = null;
        if (options.storage()) {
            int chests = count(e, options, Signal.CHEST), barrels = count(e, options, Signal.BARREL);
            int basic = chests + barrels;
            if (basic > 0) {
                score += Math.min(42, 8 + basic * 4);
                if (options.targets().isEmpty()) reasons.add("storage");
                else { if (chests > 0) reasons.add("chest"); if (barrels > 0) reasons.add("barrel"); }
                primary = chests > 0 ? Signal.CHEST : Signal.BARREL;
            }
            if (count(e, options, Signal.SHULKER) > 0) { score += 65; reasons.add("shulker"); primary = Signal.SHULKER; }
            if (count(e, options, Signal.ENDER_CHEST) > 0) { score += 55; reasons.add("ender"); primary = Signal.ENDER_CHEST; }
            if (count(e, options, Signal.HOPPER) > 0) {
                score += Math.min(36, 12 + count(e, options, Signal.HOPPER) * 4);
                reasons.add("hopper");
                if (primary == null) primary = Signal.HOPPER;
            }
            if (count(e, options, Signal.FURNACE) > 0) {
                score += Math.min(16, count(e, options, Signal.FURNACE) * 4);
                reasons.add("furnace");
                if (primary == null) primary = Signal.FURNACE;
            }
        }
        if (options.builds()) {
            for (Signal s : List.of(Signal.BEACON, Signal.ENCHANTING, Signal.ANVIL, Signal.CRAFTING,
                    Signal.REDSTONE, Signal.BED, Signal.PORTAL, Signal.TORCH, Signal.SPAWNER, Signal.ARMOR_STAND)) {
                int count = count(e, options, s);
                if (count == 0) continue;
                int points = switch (s) {
                    case BEACON -> 70;
                    case ENCHANTING -> 26;
                    case ANVIL -> 14;
                    case CRAFTING -> Math.min(12, count * 4);
                    case REDSTONE -> Math.min(28, 8 + count * 2);
                    case BED -> Math.min(10, count * 2);
                    case PORTAL -> 12;
                    case TORCH -> Math.min(6, count / 4);
                    case SPAWNER -> Math.min(50, 24 + count * 6);
                    case ARMOR_STAND -> Math.min(30, 8 + count * 4);
                    default -> 0;
                };
                score += points;
                boolean selectedMatch = options.includeMatches() && FinderTargets.key(s) != null;
                if (points >= 8 || selectedMatch) reasons.add(s.name().toLowerCase(java.util.Locale.ROOT));
                if (primary == null && (points >= 8 || selectedMatch)) primary = s;
            }
        }
        int constructionScore = score;
        if (options.amethyst() && e.totalAmethyst() >= 8) {
            reasons.add("geode");
            if (primary == null) {
                for (Signal s : List.of(Signal.BUDDING, Signal.AMETHYST, Signal.CLUSTER, Signal.BUD))
                    if (e.count(s) > 0) { primary = s; break; }
            }
            // Abundance is a snapshot, not observed growth or proof of a nearby player.
            if (e.count(Signal.BUDDING) >= 3 && e.count(Signal.CLUSTER) >= 6) {
                reasons.add("mature_clusters");
            }
            if (grownBuds > 0) {
                score += Math.min(18, 6 + grownBuds * 2);
                reasons.add("observed_growth");
            }
            // Only use differences from uninterrupted, completed observations of this same loaded chunk.
            if (removedClusters > 0) {
                score += Math.min(22, 10 + removedClusters * 2);
                reasons.add("cluster_change");
            }
        }
        score = Math.min(100, score);
        int selectedHits = 0;
        for (Signal s : Signal.values()) if (FinderTargets.key(s) != null) selectedHits += count(e, options, s);
        Finding.Kind kind = constructionScore > 0 && score >= options.threshold() ? Finding.Kind.ACTIVITY
                : options.includeMatches() && selectedHits > 0 ? Finding.Kind.MATCH : Finding.Kind.GEODE;
        if (kind == Finding.Kind.GEODE && (!options.amethyst() || e.totalAmethyst() < 8)) return null;
        if (primary == null) return null;
        return new Finding(e, score, kind, List.copyOf(reasons), e.representativeY(primary), removedClusters, grownBuds);
    }
    private static int count(ChunkEvidence e, ScanOptions options, Signal signal) {
        return options.selected(signal) ? e.count(signal) : 0;
    }
}
