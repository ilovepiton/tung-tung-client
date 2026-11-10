package dev.amethyst.finder.ui;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TotemPopLedgerTest {
    @Test void countsOnlyObservedEventsAndNeverInfersRemainingInventory() {
        var ledger = new TotemPopLedger();
        UUID player = UUID.randomUUID();
        assertEquals(0, ledger.pops(player));
        ledger.seen(player, 10);
        ledger.popped(player, 11);
        ledger.popped(player, 12);
        ledger.seen(player, 20);
        assertEquals(2, ledger.pops(player));
        assertEquals(1, ledger.size());
    }

    @Test void absentPlayersExpireAndWorldResetDropsEveryObservation() {
        var ledger = new TotemPopLedger();
        UUID stale = UUID.randomUUID(), live = UUID.randomUUID();
        ledger.popped(stale, 1);
        ledger.popped(live, 10);
        ledger.seen(live, 1202);
        ledger.prune(1202);
        assertEquals(0, ledger.pops(stale));
        assertEquals(1, ledger.pops(live));
        ledger.clear();
        assertEquals(0, ledger.size());
    }

    @Test void boundPrefersRecentPlayersInsteadOfGrowingForAnEntireSession() {
        var ledger = new TotemPopLedger();
        UUID first = new UUID(0, 0);
        ledger.popped(first, 0);
        for (int i = 1; i < 512; i++) ledger.seen(new UUID(0, i), i);
        ledger.seen(first, 512);
        ledger.seen(new UUID(0, 512), 513);
        assertEquals(512, ledger.size());
        assertEquals(1, ledger.pops(first));
        assertEquals(0, ledger.pops(new UUID(0, 1)));
    }
}
