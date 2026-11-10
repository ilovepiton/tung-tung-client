package dev.amethyst.finder.core;

import java.util.Objects;
import java.util.function.Supplier;

/** One exact input snapshot, not a time-based cache: callers own invalidation. */
public final class ExactSelectionCache<K,V> {
    private K key;
    private V value;
    private boolean valid;

    public V get(K requested, Supplier<V> build) {
        if (valid && Objects.equals(key, requested)) return value;
        V next = build.get();
        key = requested;
        value = next;
        valid = true;
        return next;
    }

    public void clear() {
        valid = false;
        key = null;
        value = null;
    }
}
