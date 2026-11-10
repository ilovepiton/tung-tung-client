package dev.amethyst.finder.core;
import java.util.Map;

public record ScanOptions(boolean storage, boolean amethyst, boolean builds, int sensitivity,
                          Map<String, Boolean> targets, boolean includeMatches) {
    public ScanOptions { targets = targets == null ? Map.of() : Map.copyOf(targets); }
    public ScanOptions(boolean storage, boolean amethyst, boolean builds, int sensitivity) {
        this(storage, amethyst, builds, sensitivity, Map.of(), false);
    }
    public boolean selected(Signal signal) {
        if (signal.isAmethyst()) return amethyst;
        if (!(signal.isStorage() ? storage : builds)) return false;
        return targets.isEmpty() || FinderTargets.enabled(targets, signal);
    }
    public int threshold() { return 100 - Math.clamp(sensitivity, 10, 90); }
}
