package dev.amethyst.finder.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Strict, bounded JSON format. Corrupt/truncated files are errors, not successful empty history. */
public final class ObservationCodec {
    public static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .setStrictness(Strictness.STRICT).create();
    private ObservationCodec() {}
    private record Envelope(int schema, String scope, List<ChunkObservation> chunks) {}

    public static byte[] encode(ObservationScope scope, Map<Long, ChunkObservation> records) throws IOException {
        byte[] bytes = GSON.toJson(new Envelope(1, scope.fileId(), List.copyOf(boundedSnapshot(records).values())))
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Chunk history exceeds the file size limit");
        return bytes;
    }

    public static Map<Long, ChunkObservation> decode(ObservationScope scope, byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Chunk history exceeds the file size limit");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setStrictness(Strictness.STRICT);
            JsonElement document = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !document.isJsonObject())
                throw new IOException("Invalid chunk history document");
            JsonObject root = document.getAsJsonObject();
            if (number(root, "schema", 1, 1) != 1 || !string(root, "scope").equals(scope.fileId()))
                throw new IOException("Chunk history belongs to a different scope or schema");
            JsonArray chunks = array(root, "chunks", ObservationMemory.MAX_CHUNKS);
            LinkedHashMap<Long, ChunkObservation> records = new LinkedHashMap<>();
            int totalStages = 0;
            for (JsonElement entry : chunks) {
                if (!entry.isJsonObject()) throw new IOException("Invalid chunk history record");
                JsonObject chunk = entry.getAsJsonObject();
                number(chunk, "chunkX", Integer.MIN_VALUE, Integer.MAX_VALUE);
                number(chunk, "chunkZ", Integer.MIN_VALUE, Integer.MAX_VALUE);
                number(chunk, "firstObservedAt", 0, Long.MAX_VALUE);
                number(chunk, "observedAt", 0, Long.MAX_VALUE);
                number(chunk, "continuousGrowthEvents", 0, Long.MAX_VALUE);
                number(chunk, "gapGrowthEvents", 0, Long.MAX_VALUE);
                integerMap(chunk, "signalCounts", 64, true, false);
                integerMap(chunk, "representativeY", 64, false, false);
                integerMap(chunk, "patternCounts", 16, true, false);
                totalStages += integerMap(chunk, "budStages", ObservationMemory.MAX_STAGES_PER_CHUNK, true, true);
                if (totalStages > ObservationMemory.MAX_TOTAL_STAGES)
                    throw new IOException("Chunk history exceeds the total growth baseline limit");
                JsonArray events = array(chunk, "recentGrowth", ObservationMemory.MAX_RECENT_GROWTH);
                for (var event : events) {
                    if (!event.isJsonObject()) throw new IOException("Invalid growth event");
                    JsonObject growth = event.getAsJsonObject();
                    number(growth, "position", Long.MIN_VALUE, Long.MAX_VALUE);
                    number(growth, "previousStage", 1, 3);
                    number(growth, "currentStage", 2, 4);
                    number(growth, "observedAt", 0, Long.MAX_VALUE);
                    JsonElement continuous = growth.get("continuous");
                    if (continuous == null || !continuous.isJsonPrimitive() || !continuous.getAsJsonPrimitive().isBoolean())
                        throw new IOException("Invalid growth continuity flag");
                }
                ChunkObservation value = GSON.fromJson(chunk, ChunkObservation.class);
                if (records.put(value.key(), value) != null) throw new IOException("Duplicate chunk history record");
            }
            return Collections.unmodifiableMap(records);
        } catch (CharacterCodingException malformed) {
            throw new IOException("Chunk history contains invalid UTF-8", malformed);
        } catch (RuntimeException malformed) {
            throw new IOException("Chunk history is malformed or truncated", malformed);
        }
    }

    /** Newest records retain stage baselines first; older count/history facts remain available. */
    public static Map<Long, ChunkObservation> boundedSnapshot(Map<Long, ChunkObservation> input) {
        List<ChunkObservation> records = newestRecords(input);
        LinkedHashMap<Long, ChunkObservation> result = new LinkedHashMap<>();
        int remainingStages = ObservationMemory.MAX_TOTAL_STAGES;
        for (var record : records) {
            if (result.size() == ObservationMemory.MAX_CHUNKS) break;
            if (record.budStages().size() > remainingStages)
                record = record.withStages(HistoryBounds.stages(record.budStages(), remainingStages));
            remainingStages -= record.budStages().size();
            result.put(record.key(), record);
        }
        return Collections.unmodifiableMap(result);
    }
    /** Used on the client thread to capture at most 2048 immutable references, without copying stage maps. */
    static Map<Long, ChunkObservation> copyNewest(Map<Long, ChunkObservation> input) {
        LinkedHashMap<Long, ChunkObservation> result = new LinkedHashMap<>();
        for (var record : newestRecords(input)) result.put(record.key(), record);
        return Collections.unmodifiableMap(result);
    }
    private static List<ChunkObservation> newestRecords(Map<Long, ChunkObservation> input) {
        if (input == null || input.isEmpty()) return List.of();
        Comparator<ChunkObservation> order = Comparator.comparingLong(ChunkObservation::observedAt)
                .thenComparing(Comparator.<ChunkObservation>comparingLong(ChunkObservation::key).reversed());
        PriorityQueue<ChunkObservation> newest = new PriorityQueue<>(order);
        for (var entry : input.entrySet()) {
            ChunkObservation record = entry.getValue();
            if (entry.getKey() == null || record == null || entry.getKey().longValue() != record.key())
                throw new IllegalArgumentException("Chunk history key does not match its coordinates");
            if (newest.size() < ObservationMemory.MAX_CHUNKS) newest.add(record);
            else if (order.compare(record, newest.peek()) > 0) { newest.poll(); newest.add(record); }
        }
        List<ChunkObservation> result = new ArrayList<>(newest);
        result.sort(order.reversed());
        return result;
    }
    private static String string(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IOException("Invalid history field: " + key);
        return value.getAsString();
    }
    private static long number(JsonObject object, String key, long min, long max) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IOException("Invalid numeric history field: " + key);
        long number = value.getAsBigDecimal().toBigIntegerExact().longValueExact();
        if (number < min || number > max) throw new IOException("Out-of-range history field: " + key);
        return number;
    }
    private static JsonArray array(JsonObject object, String key, int maximum) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > maximum)
            throw new IOException("Invalid or oversized history array: " + key);
        return value.getAsJsonArray();
    }
    private static int integerMap(JsonObject object, String key, int maximum, boolean nonnegative,
            boolean positions) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject() || value.getAsJsonObject().size() > maximum)
            throw new IOException("Invalid or oversized history map: " + key);
        JsonObject values = value.getAsJsonObject();
        for (var entry : values.entrySet()) {
            if (positions) Long.parseLong(entry.getKey());
            else if (!entry.getKey().matches("[A-Za-z0-9_:.\\-]{1,64}")) throw new IOException("Invalid observation key");
            number(values, entry.getKey(), positions ? 1 : nonnegative ? 0 : Integer.MIN_VALUE,
                    positions ? 4 : Integer.MAX_VALUE);
        }
        return values.size();
    }
}
