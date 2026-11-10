package dev.amethyst.finder.history;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Background disk I/O with coalesced writes, scope separation and atomic replacement. */
public final class ObservationMemory implements AutoCloseable {
    public static final int MAX_CHUNKS = 2048;
    public static final int MAX_STAGES_PER_CHUNK = 4096;
    public static final int MAX_TOTAL_STAGES = 65536;
    public static final int MAX_RECENT_GROWTH = 32;
    private final Path directory;
    private final ExecutorService io;
    private final Object lock = new Object();
    private final Map<String, Long> generations = new HashMap<>();
    private final Map<WriteKey, WriteBatch> pending = new HashMap<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    // Accessed only by the single I/O worker. A failed read must never be overwritten by an autosave.
    private final Set<ObservationScope> failedLoads = new HashSet<>();
    private boolean closed;
    private record WriteKey(ObservationScope scope, long generation) {}
    private static final class WriteBatch {
        Map<Long, ChunkObservation> latest;
        CompletableFuture<Void> completion;
    }
    public record Update(ChunkObservation observation, List<GrowthTransition> growthDelta) {
        public Update { growthDelta = List.copyOf(growthDelta); }
    }

    public ObservationMemory(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        io = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Tung-Tung-Chunk-Memory");
            thread.setDaemon(true);
            return thread;
        });
    }
    public Path fileFor(ObservationScope scope) { return directory.resolve(scope.fileId() + ".json"); }
    public Optional<String> lastError() { return Optional.ofNullable(lastError.get()); }

    public CompletableFuture<Map<Long, ChunkObservation>> load(ObservationScope scope) {
        synchronized (lock) {
            requireOpen();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Path file = fileFor(scope);
                    if (!Files.exists(file)) { failedLoads.remove(scope); lastError.set(null); return Map.of(); }
                    if (Files.size(file) > ObservationCodec.MAX_FILE_BYTES)
                        throw new IOException("Chunk history exceeds the file size limit");
                    byte[] bytes;
                    try (var stream = Files.newInputStream(file)) {
                        bytes = stream.readNBytes(ObservationCodec.MAX_FILE_BYTES + 1);
                    }
                    var records = ObservationCodec.decode(scope, bytes);
                    failedLoads.remove(scope);
                    lastError.set(null);
                    return records;
                } catch (IOException failure) {
                    failedLoads.add(scope);
                    lastError.set("Chunk memory unavailable: " + failure.getMessage());
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, io);
        }
    }

    /** Caller supplies continuity only when the same chunk remained observable between completed scans. */
    public static Update observe(ChunkObservation previous, ObservedChunk data, boolean continuous) {
        boolean sameChunk = previous != null && previous.chunkX() == data.chunkX() && previous.chunkZ() == data.chunkZ();
        if (sameChunk && data.observedAt() <= previous.observedAt()) return new Update(previous, List.of());
        List<GrowthTransition> delta = new ArrayList<>();
        List<GrowthTransition> recent = new ArrayList<>();
        long first = data.observedAt(), continuousTotal = 0, gapTotal = 0;
        if (sameChunk) {
            first = previous.firstObservedAt();
            continuousTotal = previous.continuousGrowthEvents(); gapTotal = previous.gapGrowthEvents();
            recent.addAll(previous.recentGrowth());
            for (var entry : data.budStages().entrySet()) {
                Integer oldStage = previous.budStages().get(entry.getKey());
                if (oldStage != null && entry.getValue() > oldStage)
                    delta.add(new GrowthTransition(entry.getKey(), oldStage, entry.getValue(), data.observedAt(), continuous));
            }
            if (continuous) continuousTotal = HistoryBounds.add(continuousTotal, delta.size());
            else gapTotal = HistoryBounds.add(gapTotal, delta.size());
        }
        recent.addAll(delta);
        var record = new ChunkObservation(data.chunkX(), data.chunkZ(), first, data.observedAt(),
                data.signalCounts(), data.representativeY(), data.patternCounts(), data.budStages(),
                recent, continuousTotal, gapTotal);
        return new Update(record, delta);
    }

    /** Completion means this snapshot or a newer coalesced snapshot has been atomically committed. */
    public CompletableFuture<Void> scheduleSave(ObservationScope scope, Map<Long, ChunkObservation> records) {
        Map<Long, ChunkObservation> snapshot = ObservationCodec.copyNewest(records);
        synchronized (lock) {
            requireOpen();
            WriteKey key = new WriteKey(scope, generations.getOrDefault(scope.fileId(), 0L));
            WriteBatch batch = pending.get(key);
            boolean enqueue = batch == null;
            if (enqueue) { batch = new WriteBatch(); pending.put(key, batch); }
            batch.latest = snapshot;
            if (batch.completion == null) batch.completion = new CompletableFuture<>();
            if (enqueue) io.execute(() -> drain(key));
            return batch.completion;
        }
    }

    /** Cancels obsolete queued snapshots before deleting; later saves cannot be reordered before this clear. */
    public CompletableFuture<Void> clear(ObservationScope scope) {
        CompletableFuture<Void> obsoleteCompletion = null;
        CompletableFuture<Void> clearCompletion;
        synchronized (lock) {
            requireOpen();
            String id = scope.fileId();
            long generation = generations.getOrDefault(id, 0L);
            WriteBatch obsolete = pending.remove(new WriteKey(scope, generation));
            if (obsolete != null) {
                obsolete.latest = null;
                obsoleteCompletion = obsolete.completion;
                obsolete.completion = null;
            }
            generations.put(id, generation + 1);
            clearCompletion = CompletableFuture.runAsync(() -> {
                try { Files.deleteIfExists(fileFor(scope)); failedLoads.remove(scope); lastError.set(null); }
                catch (IOException failure) {
                    lastError.set("Chunk memory could not be cleared: " + failure.getMessage());
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, io);
        }
        if (obsoleteCompletion != null) obsoleteCompletion.complete(null);
        return clearCompletion;
    }
    private void drain(WriteKey key) {
        while (true) {
            Map<Long, ChunkObservation> records;
            CompletableFuture<Void> completion;
            synchronized (lock) {
                WriteBatch batch = pending.get(key);
                if (batch == null || batch.latest == null) { pending.remove(key); return; }
                records = batch.latest; batch.latest = null;
                completion = batch.completion; batch.completion = null;
            }
            try {
                writeAtomic(key.scope(), records);
                lastError.set(null);
                completion.complete(null);
            } catch (IOException | RuntimeException failure) {
                lastError.set("Chunk memory could not be saved: " + failure.getMessage());
                completion.completeExceptionally(failure);
            }
        }
    }
    private void writeAtomic(ObservationScope scope, Map<Long, ChunkObservation> records) throws IOException {
        if (failedLoads.contains(scope))
            throw new IOException("History load failed; clear or successfully reload this scope before saving");
        byte[] bytes = ObservationCodec.encode(scope, records);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, scope.fileId() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer contents = ByteBuffer.wrap(bytes);
                while (contents.hasRemaining()) channel.write(contents);
                channel.force(true);
            }
            // Do not fall back to a non-atomic replacement that could destroy a valid old file on failure.
            Files.move(temporary, fileFor(scope), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Chunk memory is closed"); }

    /** Called from normal client shutdown, after the scanner has scheduled its last snapshot. */
    @Override public void close() throws IOException {
        synchronized (lock) {
            if (closed) return;
            closed = true; io.shutdown();
        }
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) throw new IOException("Chunk memory flush timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Chunk memory flush interrupted", interrupted);
        }
    }
}
