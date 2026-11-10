package dev.amethyst.finder.modules.pvp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnchorHotbarSelectionTest {
    @Test void aFullHotbarNeverBorrowsTheAnchorReturnSlot() {
        for (int original = 0; original < 9; original++) {
            int borrowed = AnchorHotbarSelection.temporarySlot(original, slot -> false, slot -> false);
            assertTrue(borrowed >= 0 && borrowed < 9);
            assertNotEquals(original, borrowed);
        }
    }

    @Test void consumingTheLastAnchorDoesNotPutGlowstoneInItsNowEmptySlot() {
        int borrowed = AnchorHotbarSelection.temporarySlot(0, slot -> slot == 0, slot -> false);
        assertNotEquals(0, borrowed);
        assertEquals(0, AnchorHotbarSelection.restoreSlot(0, 8, 8, true, slot -> false));
    }

    @Test void anEmptyDifferentSlotIsPreferredOverDisplacingAnotherItem() {
        assertEquals(7, AnchorHotbarSelection.temporarySlot(3, slot -> slot == 3 || slot == 7, slot -> false));
    }

    @Test void aSoleBackupAnchorStaysAvailableWhileGlowstoneInventoryCleanupIsPending() {
        // The last item in slot 0 has just been placed. Slot 1 is the only backup
        // anchor; slots 2..8 are occupied by ordinary non-anchor items.
        int borrowed = AnchorHotbarSelection.temporarySlot(0, slot -> slot == 0, slot -> slot == 1);
        assertEquals(2, borrowed);
        int selected = AnchorHotbarSelection.restoreSlot(0, 3, 3, true, slot -> slot == 1);
        assertEquals(1, selected);
        assertNotEquals(borrowed, selected);
    }

    @Test void aHotbarFilledWithAnchorsCanStillBorrowOneWithoutHidingEveryBackup() {
        int borrowed = AnchorHotbarSelection.temporarySlot(0, slot -> slot == 0, slot -> slot != 0);
        assertEquals(1, borrowed);
        assertEquals(2, AnchorHotbarSelection.restoreSlot(0, 0, 0, true, slot -> slot != 0 && slot != borrowed));
    }

    @Test void aGlowstoneRetryReturnsToAnActualAnchorInsteadOfItsOriginalGlowstone() {
        assertEquals(5, AnchorHotbarSelection.restoreSlot(2, 8, 8, true, slot -> slot == 5));
    }

    @Test void theOriginalAnchorStackIsPreferredAndTheNextStackIsUsedWhenItRunsOut() {
        assertEquals(4, AnchorHotbarSelection.restoreSlot(4, 8, 8, true, slot -> slot == 1 || slot == 4));
        assertEquals(1, AnchorHotbarSelection.restoreSlot(4, 8, 8, true, slot -> slot == 1));
    }

    @Test void manualSelectionDuringAnAcknowledgementWaitIsPreserved() {
        assertEquals(-1, AnchorHotbarSelection.restoreSlot(0, 8, 3, true, slot -> slot == 0));
    }

    @Test void offhandAnchorUseKeepsTheOriginalMainHandChoice() {
        assertEquals(3, AnchorHotbarSelection.restoreSlot(3, 8, 8, false, slot -> slot == 0));
    }

    @Test void rapidCompletedCyclesReturnToAnchorBeforeTheBorrowedInventoryIsRestored() {
        // Inventory cleanup may be one or several packets later. Selection must
        // already be on the anchor at each confirmed explosion, with the borrowed
        // glowstone still in the temporary slot until that cleanup receipt arrives.
        for (int original = 0; original < 9; original++) {
            final int anchorSlot = original;
            for (int cycle = 0; cycle < 100; cycle++) {
                int glowstone = AnchorHotbarSelection.temporarySlot(original, slot -> false, slot -> slot == anchorSlot);
                int detonation = (glowstone + 1) % 9;
                if (detonation == original) detonation = (detonation + 1) % 9;
                int selected = AnchorHotbarSelection.restoreSlot(original, detonation, detonation, true, slot -> slot == anchorSlot);
                assertEquals(original, selected);
                assertNotEquals(glowstone, selected);
            }
        }
    }
}
