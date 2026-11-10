package dev.amethyst.finder.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical selectable base features; these identify observations, never loot contents or raid history. */
public final class FinderTargets {
    public static final List<String> IDS = List.of("chest", "spawner", "barrel", "shulker", "ender_chest", "hopper",
            "furnace", "crafting", "enchanting", "anvil", "beacon", "armor_stand");
    private FinderTargets() {}
    public static Map<String, Boolean> defaults() { return sanitize(null); }
    public static Map<String, Boolean> sanitize(Map<String, Boolean> supplied) {
        Map<String, Boolean> clean = new LinkedHashMap<>();
        for (String id : IDS) clean.put(id, supplied == null || supplied.get(id) == null || Boolean.TRUE.equals(supplied.get(id)));
        return clean;
    }
    public static String key(Signal signal) {
        return switch (signal) {
            case CHEST -> "chest"; case SPAWNER -> "spawner"; case BARREL -> "barrel";
            case SHULKER -> "shulker"; case ENDER_CHEST -> "ender_chest"; case HOPPER -> "hopper";
            case FURNACE -> "furnace"; case CRAFTING -> "crafting"; case ENCHANTING -> "enchanting";
            case ANVIL -> "anvil"; case BEACON -> "beacon"; case ARMOR_STAND -> "armor_stand";
            default -> null;
        };
    }
    public static boolean enabled(Map<String, Boolean> targets, Signal signal) {
        String key = key(signal);
        return key != null && (targets == null || !Boolean.FALSE.equals(targets.get(key)));
    }
}
