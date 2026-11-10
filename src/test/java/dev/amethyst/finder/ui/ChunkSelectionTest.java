package dev.amethyst.finder.ui;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

class ChunkSelectionTest {
    @AfterEach void clear(){ChunkSelection.reset();}
    @Test void emptySelectionAndNoOverrideReturnNullableKeyWithoutUnboxing() {
        ChunkSelection.reset();assertNull(ChunkSelection.selectedKey());assertNull(ChunkSelection.selected());
    }
    @Test void explicitPinIsAvailableBeforeFirstRenderedFrame() {
        ChunkSelection.reset();ChunkSelection.setOverride(Long.valueOf(42));
        assertEquals(Long.valueOf(42),ChunkSelection.selectedKey());assertNull(ChunkSelection.selected());
        ChunkSelection.clearOverride();assertNull(ChunkSelection.selectedKey());
    }
    @Test void selectedCurrentChunkCanClearBackToNoSelection() {
        ChunkSelection.reset();
        ChunkSelection.update(new ChunkSelection.Selected(17,0,17,null,List.of(ChunkSelection.Category.PLAYER),1,false));
        assertEquals(Long.valueOf(17),ChunkSelection.selectedKey());
        ChunkSelection.update(null);assertNull(ChunkSelection.selectedKey());
    }
    @Test void pinPersistsWithoutCurrentSelectionAndScopeResetClearsIt() {
        ChunkSelection.setOverride(Long.valueOf(42));
        ChunkSelection.update(new ChunkSelection.Selected(42,0,42,null,List.of(ChunkSelection.Category.CHEST),0,true));
        ChunkSelection.update(null);assertEquals(Long.valueOf(42),ChunkSelection.selectedKey());
        ChunkSelection.reset();assertNull(ChunkSelection.selectedKey());assertNull(ChunkSelection.overrideKey());
    }
}
