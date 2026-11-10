package dev.amethyst.finder.ui;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded observations, not an estimate of a player's hidden inventory. */
public final class TotemPopLedger {
    private record Entry(int pops, long seenAt) {}
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private static final int MAX_PLAYERS = 512;
    private static final long RETAIN_TICKS = 1200;

    public void seen(UUID player, long tick) {
        Entry previous = entries.remove(player);
        entries.put(player, new Entry(previous == null ? 0 : previous.pops, tick));
        while (entries.size() > MAX_PLAYERS) entries.remove(entries.keySet().iterator().next());
    }

    public void popped(UUID player, long tick) {
        seen(player, tick);
        Entry previous = entries.get(player);
        entries.put(player, new Entry(Math.min(9999, previous.pops + 1), tick));
    }

    public int pops(UUID player) { Entry entry = entries.get(player); return entry == null ? 0 : entry.pops; }
    public void prune(long tick) { entries.values().removeIf(entry -> tick < entry.seenAt || tick - entry.seenAt > RETAIN_TICKS); }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }
}
