package dev.amethyst.finder.core;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/** Shared colors for object surfaces, labels, tracers and matching chunk bands. */
public final class HighlightPalette {
    private static final List<Signal> ORDER = List.of(Signal.CHEST,Signal.SPAWNER,Signal.BARREL,
            Signal.SHULKER,Signal.ENDER_CHEST,Signal.HOPPER,Signal.FURNACE,Signal.CRAFTING,
            Signal.ENCHANTING,Signal.ANVIL,Signal.BEACON,Signal.ARMOR_STAND,Signal.AMETHYST);
    private HighlightPalette() {}

    public static int color(Signal signal) {
        return switch (signal) {
            case CHEST -> 0xFFFF931F;
            case SPAWNER -> 0xFF42DCE6;
            case BARREL -> 0xFFDDA251;
            case SHULKER -> 0xFFBB69FF;
            case ENDER_CHEST -> 0xFF3BE7B0;
            case HOPPER -> 0xFF86B9DE;
            case FURNACE -> 0xFFFF6A49;
            case CRAFTING -> 0xFFD3A16A;
            case ENCHANTING -> 0xFFF46DB5;
            case ANVIL -> 0xFFB6C7D4;
            case BEACON -> 0xFF60FFD6;
            case ARMOR_STAND -> 0xFFFFD66A;
            case AMETHYST, BUDDING, BUD, CLUSTER -> 0xFFC575FF;
            default -> 0xFFFF931F;
        };
    }

    /** Repeated objects share one band; all four amethyst stages share one purple band. */
    public static List<Signal> bands(Collection<Signal> signals) {
        if (signals == null || signals.isEmpty()) return List.of();
        EnumSet<Signal> present = EnumSet.noneOf(Signal.class);
        for (Signal signal : signals) if (signal != null)
            present.add(signal.isAmethyst() ? Signal.AMETHYST : signal);
        return ORDER.stream().filter(present::contains).toList();
    }
}
