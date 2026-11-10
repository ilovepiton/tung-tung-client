package dev.amethyst.finder.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ChunkAnalysisTest {
    @Test void storageClassificationIncludesDispenserAndDropperWithoutOrdinalDependence() {
        assertTrue(Signal.DISPENSER.isStorage()); assertTrue(Signal.DROPPER.isStorage());
        assertFalse(Signal.CONSTRUCTION.isStorage()); assertFalse(Signal.ARMOR_STAND.isStorage());
        ChunkEvidence.Builder b = new ChunkEvidence.Builder(-2, 3);
        b.observe(Signal.DISPENSER, -30, 70, 50); b.observe(Signal.DROPPER, -29, 70, 50);
        assertEquals(2, b.finish(100).totalStorage());
    }
    @Test void generatedSpawnerAndOneChestDoNotBecomeStrongStructureEvidence() {
        ChunkEvidence.Builder b = new ChunkEvidence.Builder(0, 0);
        b.observe(Signal.SPAWNER, 1, 10, 1);
        assertEquals(ChunkAnalysis.StructureEvidence.NONE, ChunkAnalysisEvaluator.structure(b.finish(1)));
        b.observe(Signal.CHEST, 2, 10, 1);
        assertEquals(ChunkAnalysis.StructureEvidence.WEAK, ChunkAnalysisEvaluator.structure(b.finish(2)));
    }
    @Test void rowsRequireConsecutiveCoordinatesAndPreserveNegativeChunkCoordinates() {
        ChunkEvidence.Builder b = new ChunkEvidence.Builder(-1, -1);
        for (int x : new int[]{-16, -15, -13, -12, -10, -9}) b.observe(Signal.CONSTRUCTION, x, -20, -15);
        assertEquals(0, b.finish(1).patternCount("repeated_rows"));
        b.observe(Signal.CONSTRUCTION, -14, -20, -15);
        assertEquals(1, b.finish(2).patternCount("repeated_rows"));
    }
    @Test void independentCraftingRedstoneAndStorageRowsProduceStrongObservedPattern() {
        ChunkEvidence.Builder b = new ChunkEvidence.Builder(0, 0);
        for (int z : new int[]{1, 4}) for (int x = 1; x <= 3; x++) b.observe(Signal.CHEST, x, 50, z);
        b.observe(Signal.CRAFTING, 1, 50, 7); b.observe(Signal.REDSTONE, 3, 50, 7);
        ChunkEvidence e = b.finish(1);
        assertEquals(ChunkAnalysis.StructureEvidence.STRONG, ChunkAnalysisEvaluator.structure(e));
        assertTrue(ChunkAnalysisEvaluator.summary(e).contains("Storage 6"));
        assertFalse(ChunkAnalysisEvaluator.summary(e).toLowerCase().contains("player"));
    }
    @Test void fineFinderSelectionsDoNotMaskRawChunkAnalysis() {
        ChunkEvidence.Builder b = new ChunkEvidence.Builder(0, 0, Set.of(Signal.SPAWNER));
        b.observe(Signal.CHEST, 1, 50, 1); b.observe(Signal.BUD, 2, 50, 1);
        b.observeGrowthStage(2, 2, 50, 1);
        ChunkEvidence e = b.finish(1);
        assertTrue(e.marks().isEmpty()); assertEquals(1, e.totalStorage()); assertEquals(1, e.totalAmethyst());
        assertEquals(2, e.growthStages().get(ChunkEvidence.blockKey(2, 50, 1)));
    }
    @Test void restoringHistoryPreservesFactsAndExcludesLiveArmorStandsAndRenderMarks() {
        ChunkEvidence e = ChunkEvidence.restore(-1, 2, 100, Map.of("CHEST", 9, "ARMOR_STAND", 2),
                Map.of("CHEST", -30), Map.of("repeated_rows", 3),
                Map.of(ChunkEvidence.blockKey(-4, -30, 35), 3));
        assertEquals(9, e.totalStorage()); assertEquals(0, e.count(Signal.ARMOR_STAND));
        assertEquals(-30, e.representativeY(Signal.CHEST)); assertEquals(3, e.patternCount("repeated_rows"));
        assertTrue(e.marks().isEmpty()); assertFalse(e.signalCounts().containsKey("ARMOR_STAND"));
        assertThrows(UnsupportedOperationException.class, () -> e.growthStages().clear());
    }
    @Test void infrastructureRowsDeduplicateCompositeDispenserRedstoneObservations() {
        ChunkEvidence.Builder b = new ChunkEvidence.Builder(0, 0);
        for (int x = 1; x <= 2; x++) {
            b.observe(Signal.DISPENSER, x, 30, 1); b.observe(Signal.REDSTONE, x, 30, 1);
        }
        ChunkEvidence e = b.finish(1);
        assertEquals(2, e.totalStorage()); assertEquals(2, e.count(Signal.REDSTONE));
        assertEquals(1, e.patternCount("infrastructure_rows"));
    }
}
