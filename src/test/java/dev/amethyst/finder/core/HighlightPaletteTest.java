package dev.amethyst.finder.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HighlightPaletteTest {
    @Test void mixedChunksRetainEveryDistinctSelectedColorInStableOrder() {
        var a=HighlightPalette.bands(List.of(Signal.SPAWNER,Signal.CHEST,Signal.BARREL,Signal.CHEST));
        var b=HighlightPalette.bands(List.of(Signal.BARREL,Signal.CHEST,Signal.SPAWNER));
        assertEquals(List.of(Signal.CHEST,Signal.SPAWNER,Signal.BARREL),a);
        assertEquals(a,b);
        assertEquals(3,a.stream().map(HighlightPalette::color).distinct().count());
    }
    @Test void growthStagesShareAmethystBandWhileContainersRemainSeparate() {
        assertEquals(List.of(Signal.CHEST,Signal.SHULKER,Signal.AMETHYST),HighlightPalette.bands(
                List.of(Signal.CLUSTER,Signal.BUD,Signal.BUDDING,Signal.AMETHYST,Signal.SHULKER,Signal.CHEST)));
        assertEquals(HighlightPalette.color(Signal.BUD),HighlightPalette.color(Signal.CLUSTER));
    }
    @Test void legacyUnselectedEvidenceDoesNotAddUnexpectedBands() {
        assertEquals(List.of(Signal.ARMOR_STAND),HighlightPalette.bands(
                List.of(Signal.REDSTONE,Signal.TORCH,Signal.PORTAL,Signal.ARMOR_STAND)));
        assertEquals(List.of(),HighlightPalette.bands(List.of()));
    }
    @Test void oneTypeUsesItsObjectColorWithOpaqueSurfaceAlpha() {
        assertEquals(List.of(Signal.CHEST),HighlightPalette.bands(List.of(Signal.CHEST,Signal.CHEST)));
        assertEquals(0xFFFF931F,HighlightPalette.color(Signal.CHEST));
        assertEquals(255,HighlightPalette.color(Signal.CHEST) >>> 24);
    }
}
