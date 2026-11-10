package dev.amethyst.finder.history;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class HistoryBounds {
    private HistoryBounds() {}
    static Map<String, Integer> integers(Map<String, Integer> input, int maximum, boolean nonnegative) {
        if (input == null || input.isEmpty()) return Map.of();
        TreeMap<String, Integer> sorted = new TreeMap<>();
        for (var entry : input.entrySet()) {
            String key = entry.getKey(); Integer value = entry.getValue();
            if (key == null || !key.matches("[A-Za-z0-9_:.\\-]{1,64}") || value == null
                    || (nonnegative && value < 0)) throw new IllegalArgumentException("Invalid observation count");
            sorted.put(key, value);
        }
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (var entry : sorted.entrySet()) {
            if (result.size() == maximum) break;
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
    static Map<Long, Integer> stages(Map<Long, Integer> input, int maximum) {
        if (input == null || input.isEmpty() || maximum == 0) return Map.of();
        TreeMap<Long, Integer> sorted = new TreeMap<>();
        for (var entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 4)
                throw new IllegalArgumentException("Invalid amethyst growth stage");
            sorted.put(entry.getKey(), entry.getValue());
        }
        LinkedHashMap<Long, Integer> result = new LinkedHashMap<>();
        for (var entry : sorted.entrySet()) {
            if (result.size() == maximum) break;
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
    static Map<Long, Integer> stages(Map<Long, Integer> input, int maximum, int chunkX, int chunkZ) {
        if (input != null) for (Long position : input.keySet()) {
            if (position == null || !belongs(position, chunkX, chunkZ))
                throw new IllegalArgumentException("Growth position belongs to a different chunk");
        }
        return stages(input, maximum);
    }
    static boolean belongs(long position, int chunkX, int chunkZ) {
        int blockX = (int) (position >> 38), blockZ = (int) (position << 26 >> 38);
        return (blockX >> 4) == chunkX && (blockZ >> 4) == chunkZ;
    }
    static long add(long value, int count) {
        return value > Long.MAX_VALUE - count ? Long.MAX_VALUE : value + count;
    }
}
