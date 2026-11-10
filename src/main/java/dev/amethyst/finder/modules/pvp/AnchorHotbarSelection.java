package dev.amethyst.finder.modules.pvp;

import java.util.function.IntPredicate;

/** Keeps a borrowed glowstone stack out of the slot to which the anchor hand returns. */
final class AnchorHotbarSelection {
    private AnchorHotbarSelection() {}

    static int temporarySlot(int originalSlot, IntPredicate empty, IntPredicate anchor) {
        for (int slot = 0; slot < 9; slot++)
            if (slot != originalSlot && empty.test(slot)) return slot;
        // A full hotbar still works: borrow a different slot and restore its item
        // from the server receipt. Keep backup anchors available if the manually
        // placed anchor consumed the last item in the original stack.
        for (int slot = 0; slot < 9; slot++)
            if (slot != originalSlot && !anchor.test(slot)) return slot;
        return (originalSlot + 1) % 9;
    }

    static int restoreSlot(int originalSlot, int ownedSlot, int selectedSlot,
                           boolean returnToAnchor, IntPredicate anchor) {
        // A deliberate scroll/key selection belongs to the user, including while
        // inventory cleanup is waiting for a server packet.
        if (selectedSlot != ownedSlot) return -1;
        if (returnToAnchor) {
            if (anchor.test(originalSlot)) return originalSlot;
            for (int slot = 0; slot < 9; slot++) if (anchor.test(slot)) return slot;
        }
        // There may be no anchors left; restore the actual original slot without
        // inventing an item, moving inventory again, or selecting another glowstone.
        return originalSlot;
    }
}
