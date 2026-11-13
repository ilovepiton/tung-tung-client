package dev.amethyst.finder;

import dev.amethyst.finder.core.*;
import dev.amethyst.finder.history.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Minecraft reads stay on the client thread; persistence uses a separate, coalescing worker. Missing chunks are never requested. */
public final class ChunkScanner {
    public static final int CACHE_LIMIT = 2048;
    private final FinderConfig config;
    private final ObservationMemory memory;
    private ClientLevel world;
    private ObservationScope scope;
    private CompletableFuture<Map<Long, ChunkObservation>> loading;
    private final Set<Long> loaded = new HashSet<>(), queued = new HashSet<>(), notified = new HashSet<>();
    private final Set<Long> verified = new HashSet<>(), continuousBaseline = new HashSet<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Map<Long, Long> generations = new HashMap<>(), dirtyDue = new HashMap<>(), dirtySince = new HashMap<>();
    private final LinkedHashMap<Long, Observation> cache = new LinkedHashMap<>();
    private final LinkedHashMap<Long, ChunkObservation> history = new LinkedHashMap<>();
    private List<Finding> findings = List.of();
    private List<BlockMark> marks = List.of();
    private List<ChunkAnalysis> analyses = List.of();
    private ScanJob current;
    private long tick, lastNotification = -1000, lastSaveTick, revision, scopeGeneration;
    private long baselinePositions;
    private boolean refreshNeeded = true, saveNeeded;
    private int discoveryCursor, discoveryRadius = -1;
    private int reconcileCursor, lastPlayerChunkX = Integer.MIN_VALUE, lastPlayerChunkZ = Integer.MIN_VALUE;
    private List<Long> reconcileKeys = List.of();
    private List<int[]> offsets = List.of();
    public long completedScans;
    public double lastWorkMs;
    public Finding waypoint;

    public ChunkScanner(FinderConfig config) {
        this(config, new ObservationMemory(FinderConfig.path().getParent().resolve("tun-tun-history")));
    }
    public ChunkScanner(FinderConfig config, ObservationMemory memory) { this.config = config; this.memory = memory; }
    /** Must be called before adopting/loading a world. Scope identity comes from the actual connected server/world. */
    public void setScope(ObservationScope next) {
        if (Objects.equals(scope, next)) return;
        saveNow();
        resetSession();
        scope = next;
        cache.clear(); history.clear(); analyses = List.of(); baselinePositions = 0;
        loading = next == null ? null : memory.load(next);
        refreshNeeded = true; revision++;
    }
    public void adoptWorld(ClientLevel level) {
        if (world == level) return;
        resetSession();
        world = level;
    }
    private void resetSession() {
        loaded.clear(); queued.clear(); queue.clear(); generations.clear(); dirtyDue.clear(); dirtySince.clear();
        reconcileKeys = List.of(); reconcileCursor = 0;
        lastPlayerChunkX = Integer.MIN_VALUE; lastPlayerChunkZ = Integer.MIN_VALUE;
        verified.clear(); continuousBaseline.clear(); notified.clear(); findings = List.of(); marks = List.of();
        current = null; waypoint = null; completedScans = 0; lastWorkMs = 0;
        List<ChunkAnalysis> historical = new ArrayList<>();
        for (var entry : history.entrySet()) {
            Observation raw = cache.get(entry.getKey());
            if (raw != null) historical.add(analysis(entry.getValue(), raw.evidence));
        }
        historical.sort(Comparator.comparingInt(ChunkAnalysis::chunkX).thenComparingInt(ChunkAnalysis::chunkZ));
        analyses = List.copyOf(historical);
        scopeGeneration++; revision++; refreshNeeded = true;
    }
    /** Explicit user clear deletes this scope's historical observations; dimension changes never call it. */
    public void clear() {
        cache.clear(); history.clear(); baselinePositions = 0; verified.clear(); continuousBaseline.clear(); notified.clear();
        findings = List.of(); marks = List.of(); analyses = List.of(); current = null;
        queue.clear(); queued.clear(); dirtyDue.clear(); dirtySince.clear(); waypoint = null;
        loading = null; saveNeeded = false;
        if (scope != null) memory.clear(scope);
        for (long key : loaded) if (config.requiresScan()) enqueue(key);
        scopeGeneration++; revision++; refreshNeeded = true;
    }
    public void close() {
        saveNow();
        try { memory.close(); }
        catch (java.io.IOException ex) { ChunkFinderClient.LOGGER.warn("Cannot flush chunk observation memory", ex); }
    }
    public String historyError() { return memory.lastError().orElse(""); }
    public boolean historyLoading() { return loading != null; }
    public long revision() { return revision; }
    public long scopeGeneration() { return scopeGeneration; }
    public List<ChunkAnalysis> chunkObservations() { return analyses; }
    public void onLoad(ClientLevel level, LevelChunk chunk) {
        adoptWorld(level);
        long key = ChunkEvidence.key(chunk.getPos().x(), chunk.getPos().z());
        loaded.add(key); verified.remove(key); continuousBaseline.remove(key);
        generations.merge(key, 1L, Long::sum);
        dirtyDue.remove(key); dirtySince.remove(key);
        if (current != null && current.key == key) current = null;
        if (config.requiresScan()) enqueue(key);
        refreshNeeded = true;
    }
    public void onUnload(ClientLevel level, LevelChunk chunk) {
        if (world != level) return;
        long key = ChunkEvidence.key(chunk.getPos().x(), chunk.getPos().z());
        loaded.remove(key); verified.remove(key); continuousBaseline.remove(key);
        generations.remove(key); dirtyDue.remove(key); dirtySince.remove(key);
        queued.remove(key); queue.remove(key); notified.remove(key);
        if (current != null && current.key == key) current = null;
        // Preserve block observations. Their status is now historical, never live.
        refreshNeeded = true;
    }
    /** Successful received block changes invalidate the whole scan generation before the next scan slice. */
    public void onBlockChanged(LevelChunk chunk, BlockState before, BlockState after) {
        if(ChunkFinderClient.RESOURCES!=null && (ResourceScanner.kind(before)!=ResourceScanner.kind(after)
                ||ResourceScanner.mineral(before)!=ResourceScanner.mineral(after)))
            ChunkFinderClient.RESOURCES.invalidateChunk(chunk);
        else if(ChunkFinderClient.RESOURCES!=null && before!=null
                && (before.isAir()||!before.canOcclude())!=(after.isAir()||!after.canOcclude()))
            ChunkFinderClient.RESOURCES.invalidateVisibility();
        if (before == null || world == null || chunk.getLevel() != world) return;
        if (BlockClassifier.classify(before) == BlockClassifier.classify(after)
                && BlockClassifier.growthStage(before) == BlockClassifier.growthStage(after)) return;
        long key = ChunkEvidence.key(chunk.getPos().x(), chunk.getPos().z());
        if (!loaded.contains(key)) return;
        generations.merge(key, 1L, Long::sum); verified.remove(key);
        if (current != null && current.key == key) current = null;
        long first = dirtySince.computeIfAbsent(key, ignored -> tick);
        dirtyDue.put(key, Math.min(tick + 3, first + 20));
        queued.remove(key); queue.remove(key);
        refreshNeeded = true;
    }
    private void enqueue(long key) { if (queued.add(key)) queue.addLast(key); }
    private int scanRadius() {
        return Math.max(config.radiusChunks,config.isModuleEnabled("search_chunk")?(config.searchChunkRangeBlocks+15)/16:0);
    }
    public record SusStatus(int checked,int pending,int matureChunks,int matureClusters,int matching,int inRange,int minimum,int range) {
        public String summary() {
            return inRange+" in range / "+matching+" matching · "+checked+" checked · "+pending+" pending · "+matureChunks+" chunks with "+matureClusters+" mature clusters · minimum "+minimum+" · "+range+" blocks";
        }
    }
    public SusStatus susStatus(Minecraft client) {
        int chunks=0,clusters=0,matching=0,nearby=0;
        for(var a:analyses) {
            if(a.status()!=ChunkAnalysis.Status.LIVE||!hasCurrentChunk(a.key()))continue;
            int count=a.count(Signal.CLUSTER);if(count>0) {chunks++;clusters+=count;}
            if(count>=config.searchChunkMinClusters) {
                matching++;
                if(client.player!=null&&dev.amethyst.finder.core.SusChunkRules.within(a.chunkX(),a.chunkZ(),client.gameRenderer.mainCamera().position().x,client.gameRenderer.mainCamera().position().z,config.searchChunkRangeBlocks))nearby++;
            }
        }
        return new SusStatus(verified.size(),pendingCount(),chunks,clusters,matching,nearby,config.searchChunkMinClusters,config.searchChunkRangeBlocks);
    }
    public int loadedCount() { return loaded.size(); }
    public int pendingCount() { return queue.size() + dirtyDue.size() + (current == null ? 0 : 1); }
    public boolean isLoaded(Finding f) { return loaded.contains(f.key()) && hasCurrentChunk(f.key()); }
    public boolean isVerified(Finding f) { return verified.contains(f.key()) && hasCurrentChunk(f.key()); }
    public List<Finding> findings() { return findings; }
    public List<BlockMark> marks() { return marks; }
    public void requestRescan() {
        current = null;
        for (long key : loaded) {
            generations.merge(key, 1L, Long::sum); verified.remove(key); enqueue(key);
        }
        refreshNeeded = true;
    }
    private void pollHistory() {
        if (loading == null || !loading.isDone()) return;
        var ready = loading; loading = null;
        try {
            for (var entry : ready.join().entrySet()) {
                ChunkObservation observation = entry.getValue();
                history.put(entry.getKey(), observation);
                baselinePositions += observation.budStages().size();
                cache.put(entry.getKey(), new Observation(restore(observation), 0, 0, 0, 0));
            }
        } catch (RuntimeException ex) {
            ChunkFinderClient.LOGGER.warn("Cannot load chunk observation memory", ex);
        }
        refreshNeeded = true;
    }
    private static ChunkEvidence restore(ChunkObservation o) {
        return ChunkEvidence.restore(o.chunkX(), o.chunkZ(), o.observedAt(), o.signalCounts(),
                o.representativeY(), o.patternCounts(), o.budStages());
    }
    public void tick(Minecraft client) {
        adoptWorld(client.level); tick++; pollHistory();
        if (world == null || client.player == null) { saveIfDue(); return; }
        long started = System.nanoTime(), deadline = started + Math.min(2, Math.max(1, config.budgetMs)) * 1_000_000L;
        int cx = client.player.chunkPosition().x(), cz = client.player.chunkPosition().z();
        if (cx != lastPlayerChunkX || cz != lastPlayerChunkZ) {
            lastPlayerChunkX = cx; lastPlayerChunkZ = cz; refreshNeeded = true;
        }
        reconcileLoaded(deadline);
        trimCache();
        if (!config.requiresScan()) {
            if (current != null || !queue.isEmpty() || !verified.isEmpty()) {
                current = null; queue.clear(); queued.clear(); verified.clear(); refreshNeeded = true;
            }
            lastWorkMs = 0;
            if (refreshNeeded || tick % 20 == 0) refresh(client);
            saveIfDue(); return;
        }
        discover(cx, cz, deadline);
        for (var iterator = dirtyDue.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getValue() <= tick) {
                if (loaded.contains(entry.getKey())) enqueue(entry.getKey());
                dirtySince.remove(entry.getKey()); iterator.remove();
            }
        }
        // Initial scans wait for the saved baseline; otherwise an early scan would overwrite it before comparison.
        int slices = 0;
        while (loading == null && System.nanoTime() < deadline && slices++ < 128) {
            if (current == null) {
                Long key = queue.pollFirst();
                if (key == null) break;
                queued.remove(key);
                int x = (int) (key >> 32), z = (int) (long) key;
                if (Math.abs(x - cx) > scanRadius() || Math.abs(z - cz) > scanRadius()) continue;
                LevelChunk chunk = world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null || !loaded.contains(key) || dirtyDue.containsKey(key)) continue;
                current = new ScanJob(chunk, key, generations.getOrDefault(key, 0L), config);
            }
            if (!hasCurrentChunk(current.key)) { forgetLoaded(current.key); current = null; continue; }
            if (current.generation != generations.getOrDefault(current.key, 0L)) { current = null; continue; }
            if (current.step()) { complete(client, current); current = null; }
        }
        lastWorkMs = (System.nanoTime() - started) / 1_000_000.0;
        if (refreshNeeded || tick % 20 == 0) refresh(client);
        saveIfDue();
    }
    /** ClientChunkCache can move its view center without firing an unload event for old out-of-range entries. */
    private void reconcileLoaded(long deadline) {
        if (reconcileCursor >= reconcileKeys.size()) {
            reconcileKeys = List.copyOf(loaded); reconcileCursor = 0;
        }
        for (int checked = 0; checked < 32 && reconcileCursor < reconcileKeys.size()
                && System.nanoTime() < deadline; checked++) {
            long key = reconcileKeys.get(reconcileCursor++);
            if (loaded.contains(key) && !hasCurrentChunk(key)) forgetLoaded(key);
        }
    }
    private boolean hasCurrentChunk(long key) {
        return world != null && world.getChunkSource().getChunk((int)(key >> 32), (int)key, ChunkStatus.FULL, false) != null;
    }
    private void forgetLoaded(long key) {
        loaded.remove(key); verified.remove(key); continuousBaseline.remove(key);
        queued.remove(key); queue.remove(key); generations.remove(key); dirtyDue.remove(key); dirtySince.remove(key);
        notified.remove(key); if (current != null && current.key == key) current = null;
        refreshNeeded = true;
    }
    private void discover(int cx, int cz, long deadline) {
        if (discoveryRadius != scanRadius()) {
            discoveryRadius = scanRadius();
            List<int[]> next = new ArrayList<>();
            for (int x = -discoveryRadius; x <= discoveryRadius; x++)
                for (int z = -discoveryRadius; z <= discoveryRadius; z++) next.add(new int[]{x, z});
            next.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1]));
            offsets = next; discoveryCursor = 0;
        }
        for (int n = 0; n < 48 && System.nanoTime() < deadline; n++) {
            int[] p = offsets.get(discoveryCursor++ % offsets.size());
            if (discoveryCursor == offsets.size()) discoveryCursor = 0;
            int x = cx + p[0], z = cz + p[1];
            LevelChunk chunk = world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            long key = ChunkEvidence.key(x, z);
            if (chunk == null) {
                if (loaded.contains(key)) forgetLoaded(key);
                continue;
            }
            if (loaded.add(key)) {
                generations.merge(key, 1L, Long::sum); verified.remove(key); continuousBaseline.remove(key);
                enqueue(key); refreshNeeded = true;
            } else if (!verified.contains(key) && !dirtyDue.containsKey(key)
                    && (current == null || current.key != key)) enqueue(key);
            // Completed, unchanged chunks are never rescanned on a periodic timer.
        }
    }
    private void complete(Minecraft client, ScanJob job) {
        if (!loaded.contains(job.key) || job.generation != generations.getOrDefault(job.key, 0L)) return;
        long now = System.currentTimeMillis();
        ChunkEvidence e = job.builder.finish(now);
        if (e.signalCounts().isEmpty() && !history.containsKey(job.key)) {
            // Empty terrain needs a current verification flag, not a persistent record that evicts useful findings.
            verified.add(job.key); continuousBaseline.add(job.key); completedScans++; refreshNeeded = true;
            return;
        }
        Observation old = cache.get(job.key);
        ChunkEvidence before = old == null ? null : old.evidence;
        boolean continuous = continuousBaseline.contains(job.key);
        ObservedChunk raw = new ObservedChunk(e.chunkX, e.chunkZ, now, e.signalCounts(),
                e.representativeHeights(), e.patternCounts(), e.growthStages());
        ChunkObservation prior = history.get(job.key);
        var update = ObservationMemory.observe(prior, raw, continuous);
        int removed = continuous ? e.disappearedClusters(before) : 0;
        int grown = update.growthDelta().size();
        long changeAt = removed > 0 ? now : old == null ? 0 : old.changeAt;
        long growthAt = grown > 0 ? now : old == null ? 0 : old.growthAt;
        if (removed == 0 && old != null && now - old.changeAt < 30_000) removed = old.removed;
        if (grown == 0 && old != null && now - old.growthAt < 60_000) grown = old.grown;
        cache.remove(job.key); cache.put(job.key, new Observation(e, removed, changeAt, grown, growthAt));
        history.remove(job.key); history.put(job.key, update.observation());
        baselinePositions += update.observation().budStages().size() - (prior == null ? 0 : prior.budStages().size());
        trimBaselines(job.key);
        verified.add(job.key); continuousBaseline.add(job.key); completedScans++; saveNeeded = true;
        trimCache();
        Finding f = evaluate(e, removed, grown);
        if (config.notifications && f != null && f.kind() == Finding.Kind.ACTIVITY && f.score() >= 60
                && !notified.contains(job.key) && tick - lastNotification >= 100) {
            notified.add(job.key); lastNotification = tick;
            client.gui.hud.setOverlayMessage(Component.literal("TUNG TUNG CLIENT · STRUCTURE FINDING · X "
                    + f.x() + " Z " + f.z()), true);
        }
        refreshNeeded = true;
    }
    private void trimCache() {
        for(long key:ObservationRetention.evictions(cache.keySet(),loaded,CACHE_LIMIT)) {
            cache.remove(key);
            ChunkObservation forgotten=history.remove(key);
            if(forgotten!=null)baselinePositions-=forgotten.budStages().size();
            notified.remove(key);continuousBaseline.remove(key);
        }
    }
    private void trimBaselines(long newest) {
        if (baselinePositions <= ObservationMemory.MAX_TOTAL_STAGES) return;
        for (var entry : history.entrySet()) {
            ChunkObservation o = entry.getValue();
            if (entry.getKey() == newest || o.budStages().isEmpty()) continue;
            baselinePositions -= o.budStages().size();
            entry.setValue(new ChunkObservation(o.chunkX(), o.chunkZ(), o.firstObservedAt(), o.observedAt(),
                    o.signalCounts(), o.representativeY(), o.patternCounts(), Map.of(), o.recentGrowth(),
                    o.continuousGrowthEvents(), o.gapGrowthEvents()));
            Observation raw = cache.get(entry.getKey());
            if (raw != null) cache.put(entry.getKey(), new Observation(raw.evidence.withoutGrowthBaseline(),
                    raw.removed, raw.changeAt, raw.grown, raw.growthAt));
            if (baselinePositions <= ObservationMemory.MAX_TOTAL_STAGES) break;
        }
    }
    private Finding evaluate(ChunkEvidence e, int removed, int grown) {
        Finding f = EvidenceScorer.evaluate(e, config.options(), removed, grown);
        if (f != null && f.kind() == Finding.Kind.ACTIVITY
                && ChunkAnalysisEvaluator.structure(e).ordinal() < ChunkAnalysis.StructureEvidence.MODERATE.ordinal())
            return new Finding(e, f.score(), Finding.Kind.MATCH, f.reasons(), f.y(), f.disappearedClusters(), f.observedGrowth());
        return f;
    }
    private void saveIfDue() { if (saveNeeded && tick - lastSaveTick >= 40) saveNow(); }
    private void saveNow() {
        if (scope != null && saveNeeded) { memory.scheduleSave(scope, Map.copyOf(history)); saveNeeded = false; lastSaveTick = tick; }
    }
    public void refresh(Minecraft client) {
        adoptWorld(client.level);
        List<Finding> next = new ArrayList<>();
        List<BlockMark> nextMarks = new ArrayList<>();
        List<ChunkAnalysis> nextAnalyses = new ArrayList<>();
        long now = System.currentTimeMillis();
        Map<Long, List<BlockMark>> stands = new HashMap<>();
        if (client.level != null && client.player != null && config.finderTargetEnabled(Signal.ARMOR_STAND)) {
            int seen = 0;
            for (var entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof ArmorStand) || entity.isRemoved() || !entity.isAlive()) continue;
                int sx = (int)Math.floor(entity.getX()), sz = (int)Math.floor(entity.getZ());
                if (!hasCurrentChunk(ChunkEvidence.key(sx >> 4, sz >> 4)) || !inRadius(client, sx >> 4, sz >> 4)) continue;
                long key = ChunkEvidence.key(sx >> 4, sz >> 4);
                stands.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new BlockMark(Signal.ARMOR_STAND,
                        sx, (int)Math.floor(entity.getY()), sz));
                if (++seen >= 2048) break;
            }
        }
        for (var entry : cache.entrySet()) {
            long key = entry.getKey(); Observation o = entry.getValue();
            boolean accessible = hasCurrentChunk(key);
            if (!accessible) {
                // Once a historical gap is observed, a fast return cannot revive its old LIVE flag or continuous baseline.
                verified.remove(key); continuousBaseline.remove(key);
            }
            ChunkObservation remembered = history.get(key);
            if (remembered != null) nextAnalyses.add(analysis(remembered, o.evidence));
            boolean currentVerified = verified.contains(key) && accessible;
            if (currentVerified) nextMarks.addAll(o.evidence.marks());
            if (!inRadius(client, o.evidence.chunkX, o.evidence.chunkZ)) continue;
            ChunkEvidence evidence = currentVerified
                    ? o.evidence.withArmorStands(stands.getOrDefault(key, List.of())) : o.evidence;
            if (currentVerified) stands.remove(key);
            int removed = now - o.changeAt < 30_000 ? o.removed : 0;
            int grown = now - o.growthAt < 60_000 ? o.grown : 0;
            Finding f = evaluate(evidence, removed, grown);
            if (f != null) next.add(f);
        }
        // Armor stands are current entity data and are never saved with block observations.
        for (var entry : stands.entrySet()) {
            int cx = (int)(entry.getKey() >> 32), cz = (int)(long)entry.getKey();
            ChunkEvidence live = new ChunkEvidence.Builder(cx, cz).finish(now).withArmorStands(entry.getValue());
            Finding f = evaluate(live, 0, 0);
            if (f != null) next.add(f);
        }
        double x = client.player == null ? 0 : client.player.getX(), z = client.player == null ? 0 : client.player.getZ();
        next.sort(Comparator.<Finding>comparingInt(f -> switch (f.kind()) { case ACTIVITY -> 0; case MATCH -> 1; case GEODE -> 2; })
                .thenComparing(Comparator.comparingInt(Finding::score).reversed())
                .thenComparingDouble(f -> f.distance(x, z)));
        nextAnalyses.sort(Comparator.comparingInt(ChunkAnalysis::chunkX).thenComparingInt(ChunkAnalysis::chunkZ));
        findings = List.copyOf(next); marks = List.copyOf(nextMarks);
        var immutable = List.copyOf(nextAnalyses);
        if (!analyses.equals(immutable)) { analyses = immutable; revision++; }
        refreshNeeded = false;
        if (waypoint != null) {
            long key = waypoint.key(); waypoint = next.stream().filter(f -> f.key() == key).findFirst().orElse(null);
        }
    }
    private ChunkAnalysis analysis(ChunkObservation o, ChunkEvidence e) {
        EnumMap<Signal, Integer> counts = new EnumMap<>(Signal.class);
        for (Signal signal : Signal.values()) if (signal != Signal.ARMOR_STAND && e.count(signal) > 0)
            counts.put(signal, e.count(signal));
        GrowthTransition last = o.recentGrowth().stream().max(Comparator.comparingLong(GrowthTransition::observedAt)
                .thenComparingLong(GrowthTransition::position)).orElse(null);
        boolean accessible = hasCurrentChunk(o.key());
        var status = accessible && verified.contains(o.key()) ? ChunkAnalysis.Status.LIVE
                : accessible && loaded.contains(o.key()) ? ChunkAnalysis.Status.VERIFYING : ChunkAnalysis.Status.HISTORICAL;
        return new ChunkAnalysis(o.chunkX(), o.chunkZ(), o.observedAt(), status, counts, e.totalStorage(),
                e.totalAmethyst(), e.count(Signal.SPAWNER), ChunkAnalysisEvaluator.structure(e),
                ChunkAnalysisEvaluator.summary(e), bounded(o.growthEvents()), last == null ? 0 : last.previousStage(),
                last == null ? 0 : last.currentStage(), last == null ? 0 : last.observedAt(),
                bounded(o.continuousGrowthEvents()), bounded(o.gapGrowthEvents()), o.recentGrowth(), o.growthEvents() > 0);
    }
    private static int bounded(long value) { return (int)Math.min(Integer.MAX_VALUE, Math.max(0, value)); }
    private boolean inRadius(Minecraft client, int cx, int cz) {
        return client.player != null && Math.abs(cx - client.player.chunkPosition().x()) <= scanRadius()
                && Math.abs(cz - client.player.chunkPosition().z()) <= scanRadius();
    }
    private record Observation(ChunkEvidence evidence, int removed, long changeAt, int grown, long growthAt) {}
    private static final class ScanJob {
        final long key, generation;
        final LevelChunk chunk;
        final ChunkEvidence.Builder builder;
        int section, index;
        ScanJob(LevelChunk chunk, long key, long generation, FinderConfig config) {
            this.chunk = chunk; this.key = key; this.generation = generation;
            Set<Signal> markers = EnumSet.noneOf(Signal.class);
            for (Signal signal : Signal.values()) {
                if (config.enabled && config.finderTargetEnabled(signal)
                        || config.isModuleEnabled("storage_esp") && signal.isStorage()
                        || config.isModuleEnabled("spawner_chunks") && signal == Signal.SPAWNER
                        || config.isModuleEnabled("amethyst_esp") && signal.isAmethyst()
                        || config.isModuleEnabled("search_chunk") && signal==Signal.CLUSTER) markers.add(signal);
            }
            builder = new ChunkEvidence.Builder(chunk.getPos().x(), chunk.getPos().z(), markers);
        }
        boolean step() {
            LevelChunkSection[] sections = chunk.getSections();
            if (section >= sections.length) return true;
            LevelChunkSection current = sections[section];
            if (index == 0 && (current.hasOnlyAir() || !current.maybeHas(BlockClassifier::relevant))) {
                section++; return section >= sections.length;
            }
            int end = Math.min(4096, index + 256), baseY = chunk.getSectionYFromSectionIndex(section) * 16;
            for (; index < end; index++) {
                int x = index & 15, z = (index >> 4) & 15, y = index >> 8;
                var state = current.getBlockState(x, y, z); Signal signal = BlockClassifier.classify(state);
                if (signal != null) {
                    int bx = chunk.getPos().x() * 16 + x, by = baseY + y, bz = chunk.getPos().z() * 16 + z;
                    builder.observe(signal, bx, by, bz);
                    // Dispensers/droppers are storage blocks and redstone infrastructure, counted independently.
                    if (signal == Signal.DISPENSER || signal == Signal.DROPPER) builder.observe(Signal.REDSTONE, bx, by, bz);
                    builder.observeGrowthStage(BlockClassifier.growthStage(state), bx, by, bz);
                }
            }
            if (index == 4096) { section++; index = 0; }
            return section >= sections.length;
        }
    }
}
