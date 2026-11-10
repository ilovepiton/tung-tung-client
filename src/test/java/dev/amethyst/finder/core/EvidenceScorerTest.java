package dev.amethyst.finder.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceScorerTest {
    private static final ScanOptions DEFAULT = new ScanOptions(true, true, true, 65);
    private ChunkEvidence evidence(Signal... signals) {
        var b = new ChunkEvidence.Builder(-2, 3);
        for (int i = 0; i < signals.length; i++) b.observe(signals[i], -32 + (i & 15), 64 + i / 16, 48);
        return b.finish(1000);
    }
    @Test void oneNaturalChestDoesNotMeetDefaultThreshold() {
        assertNull(EvidenceScorer.evaluate(evidence(Signal.CHEST), DEFAULT, 0));
    }
    @Test void storageAndMachineryCombineIntoActivitySignal() {
        Finding f = EvidenceScorer.evaluate(evidence(Signal.CHEST, Signal.CHEST, Signal.CHEST,
                Signal.HOPPER, Signal.HOPPER, Signal.HOPPER, Signal.FURNACE), DEFAULT, 0);
        assertNotNull(f);
        assertEquals(Finding.Kind.ACTIVITY, f.kind());
        assertTrue(f.reasons().contains("hopper"));
        assertEquals(-24, f.x()); assertEquals(56, f.z());
    }
    @Test void naturalGeodeIsNotReportedAsBaseEvenAtMaximumSensitivity() {
        var b = new ChunkEvidence.Builder(0, 0);
        for (int i = 0; i < 8; i++) b.observe(Signal.BUDDING, i, -20, 0);
        for (int i = 0; i < 12; i++) b.observe(Signal.CLUSTER, i, -19, 0);
        Finding f = EvidenceScorer.evaluate(b.finish(1000), new ScanOptions(true, true, true, 90), 8);
        assertEquals(Finding.Kind.GEODE, f.kind());
        assertTrue(f.reasons().contains("cluster_change"));
        assertTrue(f.score() < 35);
        assertFalse(f.reasons().contains("growth"));
        assertFalse(f.reasons().contains("observed_growth"));
        assertTrue(f.reasons().contains("mature_clusters"));
    }
    @Test void onlyPreviouslyObservedBudAdvancesCountAsGrowth() {
        var before = new ChunkEvidence.Builder(0, 0);
        before.observeGrowthStage(1, 1, -20, 1);
        before.observeGrowthStage(3, 2, -20, 1);
        before.observeGrowthStage(4, 3, -20, 1);
        var after = new ChunkEvidence.Builder(0, 0);
        after.observeGrowthStage(2, 1, -20, 1);
        after.observeGrowthStage(4, 2, -20, 1);
        after.observeGrowthStage(1, 3, -20, 1);
        after.observeGrowthStage(4, 4, -20, 1);
        var old = before.finish(1000); var next = after.finish(2000);
        assertEquals(2, next.advancedBuds(old));
        assertEquals(0, next.advancedBuds(null));
        assertEquals(0, old.advancedBuds(next));
        assertEquals(0, new ChunkEvidence.Builder(1, 0).finish(2000).advancedBuds(old));
    }
    @Test void observedGrowthIsLabeledWithoutTurningAGeodeIntoABase() {
        var b = new ChunkEvidence.Builder(0, 0);
        for (int i = 0; i < 8; i++) b.observe(Signal.AMETHYST, i, -20, 0);
        Finding f = EvidenceScorer.evaluate(b.finish(1000), DEFAULT, 0, 2);
        assertEquals(Finding.Kind.GEODE, f.kind());
        assertEquals(2, f.observedGrowth());
        assertTrue(f.reasons().contains("observed_growth"));
        assertFalse(EvidenceScorer.evaluate(b.finish(2000), DEFAULT, 0).reasons().contains("observed_growth"));
    }
    @Test void clusterOnlyObservationHasAValidSampleHeight() {
        Signal[] clusters = new Signal[8]; java.util.Arrays.fill(clusters, Signal.CLUSTER);
        Finding f = EvidenceScorer.evaluate(evidence(clusters), DEFAULT, 0);
        assertNotNull(f); assertEquals(64, f.y());
    }
    @Test void disabledMethodsDoNotLeakIntoScores() {
        var e = evidence(Signal.SHULKER, Signal.BEACON, Signal.HOPPER);
        assertNull(EvidenceScorer.evaluate(e, new ScanOptions(false, true, false, 90), 0));
        assertNotNull(EvidenceScorer.evaluate(e, new ScanOptions(true, false, false, 65), 0));
    }
    @Test void sensitivityActuallyControlsWeakSignals() {
        var e = evidence(Signal.CHEST);
        assertNull(EvidenceScorer.evaluate(e, new ScanOptions(true, false, false, 50), 0));
        assertNotNull(EvidenceScorer.evaluate(e, new ScanOptions(true, false, false, 90), 0));
    }
    @Test void comparesClusterPositionsInsteadOfOnlyClusterCounts() {
        var first = new ChunkEvidence.Builder(0, 0); first.observe(Signal.CLUSTER, 1, -10, 1);
        var next = new ChunkEvidence.Builder(0, 0); next.observe(Signal.CLUSTER, 2, -10, 1);
        assertEquals(1, next.finish(2000).disappearedClusters(first.finish(1000)));
        var otherChunk = new ChunkEvidence.Builder(1, 0);
        assertEquals(0, otherChunk.finish(2000).disappearedClusters(first.finish(1000)));
        assertEquals(0, next.finish(2000).disappearedClusters(null));
    }
    @Test void scoreIsBoundedAndSnapshotsAreImmutable() {
        var b = new ChunkEvidence.Builder(0, 0); b.observe(Signal.BEACON, 0, 100, 0);
        var first = b.finish(1000); b.observe(Signal.BEACON, 1, 100, 0); b.observe(Signal.SHULKER, 2, 100, 0);
        assertEquals(1, first.count(Signal.BEACON));
        assertEquals(0, first.count(Signal.SHULKER));
        assertEquals(100, EvidenceScorer.evaluate(b.finish(2000), DEFAULT, 0).score());
    }
    private ScanOptions only(String target) {
        var choices = FinderTargets.defaults(); choices.replaceAll((id, selected) -> id.equals(target));
        return new ScanOptions(true, false, true, 65, choices, true);
    }
    @Test void selectedWeakChestIsAMatchWithoutLeakingUnselectedSignals() {
        Finding f = EvidenceScorer.evaluate(evidence(Signal.CHEST, Signal.BEACON, Signal.SHULKER, Signal.HOPPER, Signal.REDSTONE), only("chest"), 0);
        assertNotNull(f); assertEquals(Finding.Kind.MATCH, f.kind()); assertEquals(12, f.score());
        assertEquals(java.util.List.of("chest"), f.reasons());
        var none = FinderTargets.defaults(); none.replaceAll((id, selected) -> false);
        assertNull(EvidenceScorer.evaluate(evidence(Signal.CHEST, Signal.BEACON), new ScanOptions(true, false, true, 65, none, true), 0));
    }
    @Test void oneSelectedSpawnerMatchesButDoesNotClaimAStrongBase() {
        Finding f = EvidenceScorer.evaluate(evidence(Signal.SPAWNER), only("spawner"), 0);
        assertNotNull(f); assertEquals(Finding.Kind.MATCH, f.kind()); assertTrue(f.reasons().contains("spawner"));
        assertNull(EvidenceScorer.evaluate(evidence(Signal.SPAWNER), new ScanOptions(true, false, true, 65, only("spawner").targets(), false), 0));
    }
    @Test void liveArmorStandsReplaceEntityCountsAndDoNotMutateBlockHistory() {
        var blocks = new ChunkEvidence.Builder(0, 0); blocks.observe(Signal.CHEST, 1, 30, 1);
        var raw = blocks.finish(1000);
        var live = raw.withArmorStands(java.util.List.of(new BlockMark(Signal.ARMOR_STAND, 2, 30, 2), new BlockMark(Signal.ARMOR_STAND, 17, 30, 2)));
        assertEquals(0, raw.count(Signal.ARMOR_STAND)); assertEquals(1, live.count(Signal.ARMOR_STAND));
        Finding f = EvidenceScorer.evaluate(live, only("armor_stand"), 0);
        assertNotNull(f); assertEquals(Finding.Kind.MATCH, f.kind()); assertEquals(30, f.y());
        var gone = live.withArmorStands(java.util.List.of());
        assertEquals(0, gone.count(Signal.ARMOR_STAND)); assertEquals(1, gone.count(Signal.CHEST));
        assertNull(EvidenceScorer.evaluate(gone, only("armor_stand"), 0));
        assertFalse(Signal.ARMOR_STAND.isAmethyst());
    }
    @Test void targetSettingsAreImmutableAndCanonical() {
        var settings = FinderTargets.defaults(); var options = new ScanOptions(true, false, true, 65, settings, true);
        settings.put("chest", false); assertTrue(options.selected(Signal.CHEST));
        var clean = FinderTargets.sanitize(java.util.Map.of("chest", false, "unknown", true));
        assertEquals(12, clean.size()); assertFalse(clean.get("chest")); assertTrue(clean.get("spawner")); assertFalse(clean.containsKey("unknown"));
    }
    @Test void selectedMarkersIncludeBuildToolsWithoutWastingCapOnDisabledTypes() {
        var b = new ChunkEvidence.Builder(0, 0, java.util.Set.of(Signal.ANVIL));
        for (int i = 0; i < 600; i++) b.observe(Signal.CHEST, 1, i, 1);
        b.observe(Signal.ANVIL, 2, 40, 2);
        var e = b.finish(1000); assertEquals(600, e.count(Signal.CHEST));
        assertEquals(1, e.marks().size()); assertEquals(Signal.ANVIL, e.marks().getFirst().signal());
    }
    @Test void eachSelectableTypeCanBeFoundOnItsOwnIncludingOneCraftingTable() {
        for (Signal signal : Signal.values()) {
            String key = FinderTargets.key(signal); if (key == null) continue;
            Finding f = EvidenceScorer.evaluate(evidence(signal), only(key), 0);
            assertNotNull(f, key); assertNotEquals(Finding.Kind.GEODE, f.kind(), key);
            assertEquals(64, f.y(), key);
        }
    }
}
