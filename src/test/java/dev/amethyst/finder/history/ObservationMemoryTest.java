package dev.amethyst.finder.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ObservationMemoryTest {
    @TempDir Path directory;
    private final ObservationScope overworld = new ObservationScope("server:example.test:25565", "minecraft:overworld", "main");
    private ObservedChunk scan(int x, int z, long time, Map<Long, Integer> stages) {
        HashMap<Long, Integer> ownPositions = new HashMap<>();
        for (var entry : stages.entrySet()) {
            long position = entry.getKey();
            int blockX = x * 16 + ((int) (position >> 38) & 15);
            int blockZ = z * 16 + ((int) (position << 26 >> 38) & 15);
            long owned = ((blockX & 0x3ffffffL) << 38) | ((blockZ & 0x3ffffffL) << 12) | (position & 0xfffL);
            ownPositions.put(owned, entry.getValue());
        }
        return new ObservedChunk(x, z, time, Map.of("CHEST", 14, "BUD", stages.size()),
                Map.of("CHEST", -24, "BUD", -19), Map.of("repeated_rows", 2), ownPositions);
    }
    private ChunkObservation observation(int x, int z, long time, Map<Long, Integer> stages) {
        return ObservationMemory.observe(null, scan(x, z, time, stages), false).observation();
    }
    private Map<Long, ChunkObservation> records(ChunkObservation observation) {
        return Map.of(observation.key(), observation);
    }

    @Test void scopesSeparateServersWorldsDimensionsAndProfilesWithoutUnsafePaths() {
        var scopes = new java.util.HashSet<String>();
        scopes.add(overworld.fileId());
        scopes.add(new ObservationScope("server:other.test:25565", "minecraft:overworld", "main").fileId());
        scopes.add(new ObservationScope("world:/data/saves/a", "minecraft:overworld", "main").fileId());
        scopes.add(new ObservationScope("world:/data/saves/b", "minecraft:overworld", "main").fileId());
        scopes.add(new ObservationScope(overworld.identity(), "minecraft:the_nether", "main").fileId());
        scopes.add(new ObservationScope(overworld.identity(), overworld.dimension(), "alternate").fileId());
        assertEquals(6, scopes.size());
        assertTrue(scopes.stream().allMatch(name -> name.matches("[0-9a-f]{64}")));
        assertNotEquals(new ObservationScope("ab", "c", "").fileId(), new ObservationScope("a", "bc", "").fileId());
        assertEquals(overworld.fileId(), new ObservationScope(overworld.identity(), overworld.dimension(), "main").fileId());
        assertNotEquals(new ObservationScope("world:/data/saves/a", "minecraft:overworld").fileId(),
                new ObservationScope("world:/data/saves/a ", "minecraft:overworld").fileId());
    }

    @Test void firstSeenMatureClusterDoesNotInventGrowth() {
        var update = ObservationMemory.observe(null, scan(84, -19, 1000, Map.of(1L, 4)), true);
        assertTrue(update.growthDelta().isEmpty());
        assertEquals(0, update.observation().growthEvents());
        assertEquals(1000, update.observation().firstObservedAt());
    }

    @Test void growthRequiresAnOlderStageAtTheSamePositionAndCountsOneObservedAdvance() {
        var previous = observation(84, -19, 1000, Map.of(1L, 1, 2L, 3, 3L, 4));
        var update = ObservationMemory.observe(previous, scan(84, -19, 2000, Map.of(1L, 2, 2L, 4, 3L, 1, 4L, 4)), true);
        assertEquals(2, update.growthDelta().size());
        assertEquals(2, update.observation().continuousGrowthEvents());
        assertEquals(0, update.observation().gapGrowthEvents());
        assertTrue(update.growthDelta().stream().allMatch(GrowthTransition::continuous));
        assertEquals("GROWTH OBSERVED", update.growthDelta().getFirst().status());
        var skippedStages = ObservationMemory.observe(previous, scan(84, -19, 3000, Map.of(1L, 4)), true);
        assertEquals(1, skippedStages.growthDelta().size());
        assertEquals(1, skippedStages.observation().growthEvents());
    }

    @Test void restartAndReloadComparisonRemainsAnObservationGapInsteadOfContinuousActivity() throws IOException {
        var prior = observation(-5, 8, 1000, Map.of(71L, 1));
        var restored = ObservationCodec.decode(overworld, ObservationCodec.encode(overworld, records(prior))).get(prior.key());
        var update = ObservationMemory.observe(restored, scan(-5, 8, 7000, Map.of(71L, 3)), false);
        assertEquals(1, update.observation().gapGrowthEvents());
        assertEquals(0, update.observation().continuousGrowthEvents());
        assertFalse(update.growthDelta().getFirst().continuous());
        assertEquals("STAGE ADVANCE ACROSS OBSERVATION GAP", update.growthDelta().getFirst().status());
        assertEquals(1000, update.observation().firstObservedAt());
        assertEquals(7000, update.observation().observedAt());
        assertEquals(1, update.growthDelta().getFirst().previousStage());
        assertEquals(3, update.growthDelta().getFirst().currentStage());
    }

    @Test void disappearedAndRegressedBudsStartANewBaselineAndStaleScansCannotRewriteHistory() {
        var prior = observation(-1, -1, 1000, Map.of(71L, 2));
        var disappeared = ObservationMemory.observe(prior, scan(-1, -1, 2000, Map.of()), true).observation();
        assertTrue(ObservationMemory.observe(disappeared, scan(-1, -1, 3000, Map.of(71L, 4)), true).growthDelta().isEmpty());
        var regressed = ObservationMemory.observe(prior, scan(-1, -1, 2000, Map.of(71L, 1)), true).observation();
        assertEquals(0, regressed.growthEvents());
        assertEquals(1, ObservationMemory.observe(regressed, scan(-1, -1, 3000, Map.of(71L, 2)), true).growthDelta().size());
        assertSame(prior, ObservationMemory.observe(prior, scan(-1, -1, 999, Map.of(71L, 4)), true).observation());
        assertEquals(0, ObservationMemory.observe(prior, scan(0, -1, 2000, Map.of(71L, 4)), true).observation().growthEvents());
    }

    @Test void codecPreservesFactsAndCumulativeCountsButDoesNotPersistLiveVerificationFlags() throws IOException {
        var before = observation(128, -42, 1000, Map.of(Long.MIN_VALUE, 1, -1L, 4));
        var after = ObservationMemory.observe(before, scan(128, -42, 2000, Map.of(Long.MIN_VALUE, 2, -1L, 4)), true).observation();
        byte[] bytes = ObservationCodec.encode(overworld, records(after));
        assertEquals(records(after), ObservationCodec.decode(overworld, bytes));
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(json.contains("loaded")); assertFalse(json.contains("live"));
        assertFalse(json.contains("verified")); assertFalse(json.contains("player"));
        var immutable = ObservationCodec.decode(overworld, bytes).get(after.key());
        assertThrows(UnsupportedOperationException.class, () -> immutable.signalCounts().put("CHEST", 0));
        assertThrows(UnsupportedOperationException.class, () -> immutable.budStages().put(3L, 4));
        assertEquals(2, immutable.patternCounts().get("repeated_rows"));
    }

    @Test void codecRejectsTruncationScopeMismatchFractionalNumbersAndMalformedUtf8() throws IOException {
        byte[] bytes = ObservationCodec.encode(overworld, records(observation(1, -2, 1000, Map.of(1L, 1))));
        assertThrows(IOException.class, () -> ObservationCodec.decode(overworld, java.util.Arrays.copyOf(bytes, bytes.length - 5)));
        assertThrows(IOException.class, () -> ObservationCodec.decode(new ObservationScope("other", "minecraft:overworld"), bytes));
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ObservationCodec.decode(overworld, json.replace("\"chunkX\":1", "\"chunkX\":1.5").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> ObservationCodec.decode(overworld, json.replace("\"observedAt\":1000", "\"observedAt\":null").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> ObservationCodec.decode(overworld, new byte[]{(byte) 0xc3, 0x28}));
        assertThrows(IOException.class, () -> ObservationCodec.decode(overworld, (json + " {}").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void atomicStorageKeepsScopesIndependentAndClearingOneLeavesOtherHistory() throws Exception {
        var nether = new ObservationScope(overworld.identity(), "minecraft:the_nether", "main");
        var red = observation(128, -42, 1000, Map.of(1L, 1));
        var purple = observation(84, -19, 2000, Map.of(2L, 4));
        try (var memory = new ObservationMemory(directory)) {
            memory.scheduleSave(overworld, records(red)).join();
            memory.scheduleSave(nether, records(purple)).join();
            assertEquals(records(red), memory.load(overworld).join());
            assertEquals(records(purple), memory.load(nether).join());
            memory.clear(overworld).join();
            assertTrue(memory.load(overworld).join().isEmpty());
            assertEquals(records(purple), memory.load(nether).join());
            try (var files = Files.list(directory)) { assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp"))); }
        }
    }

    @Test void shutdownFlushesLatestCoalescedSnapshotAndClearCannotResurrectQueuedData() throws Exception {
        var memory = new ObservationMemory(directory);
        for (int time = 1; time <= 50; time++) memory.scheduleSave(overworld, records(observation(0, 0, time, Map.of(1L, 1))));
        memory.clear(overworld);
        var latest = observation(0, 0, 100, Map.of(1L, 2));
        memory.scheduleSave(overworld, records(latest));
        memory.close();
        assertEquals(records(latest), ObservationCodec.decode(overworld, Files.readAllBytes(memory.fileFor(overworld))));
        assertThrows(IllegalStateException.class, () -> memory.load(overworld));
    }

    @Test void invalidReplacementPreservesCommittedFileAndCorruptCurrentFileReportsUnavailable() throws Exception {
        var prior = observation(1, 2, 1000, Map.of(1L, 1));
        try (var memory = new ObservationMemory(directory)) {
            memory.scheduleSave(overworld, records(prior)).join();
            byte[] committed = Files.readAllBytes(memory.fileFor(overworld));
            assertThrows(IllegalArgumentException.class, () -> memory.scheduleSave(overworld, Map.of(999L, prior)));
            assertArrayEquals(committed, Files.readAllBytes(memory.fileFor(overworld)));
            Files.writeString(directory.resolve("interrupted-write.tmp"), "{truncated");
            assertEquals(records(prior), memory.load(overworld).join());
            Files.writeString(memory.fileFor(overworld), "{truncated");
            assertThrows(CompletionException.class, () -> memory.load(overworld).join());
            assertTrue(memory.lastError().orElseThrow().contains("unavailable"));
            assertThrows(CompletionException.class, () -> memory.scheduleSave(overworld, records(prior)).join());
            assertEquals("{truncated", Files.readString(memory.fileFor(overworld)));
            memory.clear(overworld).join();
            memory.scheduleSave(overworld, records(prior)).join();
            assertTrue(memory.lastError().isEmpty());
            assertEquals(records(prior), memory.load(overworld).join());
        }
    }

    @Test void retainedGrowthListIsBoundedWhileCumulativeCountsRemainExact() {
        ChunkObservation record = observation(0, 0, 1, Map.of(1L, 1));
        for (int i = 0; i < 50; i++) {
            record = ObservationMemory.observe(record, scan(0, 0, 2L + i * 2, Map.of(1L, 2)), true).observation();
            record = ObservationMemory.observe(record, scan(0, 0, 3L + i * 2, Map.of(1L, 1)), true).observation();
        }
        assertEquals(50, record.continuousGrowthEvents());
        assertEquals(ObservationMemory.MAX_RECENT_GROWTH, record.recentGrowth().size());
        assertTrue(record.recentGrowth().getFirst().observedAt() > record.firstObservedAt());
    }

    @Test void memoryLimitsRetainNewestChunksAndDeterministicGrowthPositionsWithoutInventingEvents() {
        HashMap<Long, Integer> manyStages = new HashMap<>();
        for (long i = 5000; i >= 0; i--) manyStages.put(i, 1);
        var capped = observation(0, 0, 1, manyStages);
        assertEquals(ObservationMemory.MAX_STAGES_PER_CHUNK, capped.budStages().size());
        assertTrue(capped.budStages().containsKey(0L)); assertFalse(capped.budStages().containsKey(4096L));
        var input = new HashMap<Long, ChunkObservation>();
        for (int i = 0; i < ObservationMemory.MAX_CHUNKS + 2; i++) {
            var record = observation(i, -1, 1 + i, Map.of()); input.put(record.key(), record);
        }
        var cappedChunks = ObservationCodec.boundedSnapshot(input);
        assertEquals(ObservationMemory.MAX_CHUNKS, cappedChunks.size());
        assertFalse(cappedChunks.containsKey(ChunkObservation.key(0, -1)));
        assertTrue(cappedChunks.containsKey(ChunkObservation.key(ObservationMemory.MAX_CHUNKS + 1, -1)));
        input.clear();
        for (int i = 0; i < 17; i++) {
            var record = observation(i, -1, 1 + i, capped.budStages()); input.put(record.key(), record);
        }
        var cappedStages = ObservationCodec.boundedSnapshot(input);
        assertEquals(ObservationMemory.MAX_TOTAL_STAGES, cappedStages.values().stream().mapToInt(c -> c.budStages().size()).sum());
        assertEquals(14, cappedStages.get(ChunkObservation.key(0, -1)).signalCounts().get("CHEST"));
        assertTrue(cappedStages.get(ChunkObservation.key(0, -1)).budStages().isEmpty());
        assertTrue(ObservationMemory.observe(cappedStages.get(ChunkObservation.key(0, -1)),
                scan(0, -1, 100, Map.of(1L, 4)), false).growthDelta().isEmpty());
    }

    @Test void recordRejectsGrowthPositionsAndEventsFromAnotherChunk() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new ObservedChunk(1, -1, 1000,
                Map.of("BUD", 1), Map.of("BUD", -19), Map.of(0L, 1)));
        var record = observation(1, -1, 1000, Map.of(0L, 1));
        String json = new String(ObservationCodec.encode(overworld, records(record)), StandardCharsets.UTF_8);
        String ownPosition = Long.toString(record.budStages().keySet().iterator().next());
        assertThrows(IOException.class, () -> ObservationCodec.decode(overworld,
                json.replace("\"" + ownPosition + "\":1", "\"0\":1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new ChunkObservation(1, -1, 1000, 2000,
                record.signalCounts(), record.representativeY(), record.patternCounts(), record.budStages(),
                java.util.List.of(new GrowthTransition(0L, 1, 2, 2000, true)), 1, 0));
    }
}
