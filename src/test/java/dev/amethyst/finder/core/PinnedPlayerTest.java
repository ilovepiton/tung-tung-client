package dev.amethyst.finder.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PinnedPlayerTest {
    private static final UUID ID=UUID.fromString("11223344-5566-7788-9900-112233445566");
    @Test void savesTheBlockIncludingNegativeCoordinates() {
        var pin=PinnedPlayer.capture("server-a","overworld",ID,"Player",-.2,64.9,-16.1,1000);
        assertEquals(-1,pin.x());assertEquals(64,pin.y());assertEquals(-17,pin.z());
    }
    @Test void laterObservationsCannotMoveAnExistingPin() {
        var saved=PinnedPlayer.capture("server-a","overworld",ID,"Player",10,70,30,1000);
        var later=PinnedPlayer.capture("server-a","overworld",ID,"Player",500,90,600,2000);
        assertEquals(10,saved.x());assertEquals(30,saved.z());assertEquals(1000,saved.capturedAt());
        assertNotEquals(saved,later);
    }
    @Test void requiresTheOriginalWorldScope() {
        var pin=PinnedPlayer.capture("scope-overworld","overworld",ID,"Player",10,70,30,1000);
        assertTrue(pin.belongsTo("scope-overworld"));assertFalse(pin.belongsTo("scope-nether"));
        assertFalse(pin.belongsTo("another-server"));assertFalse(pin.belongsTo(null));
    }
    @Test void survivesPersistenceWithoutTurningIntoALiveTarget() {
        var pin=PinnedPlayer.capture("server-and-dimension","overworld",ID,"Player",12.9,65,-1.1,1000);
        var gson=new com.google.gson.Gson();
        var restored=gson.fromJson(gson.toJson(pin),PinnedPlayer.class);
        assertEquals(pin,restored);assertEquals(-2,restored.z());assertEquals(1000,restored.capturedAt());
    }
    @Test void rejectsMissingOrInvalidPositions() {
        assertThrows(IllegalArgumentException.class,()->PinnedPlayer.capture("s","d",ID,"Player",Double.NaN,1,1,1000));
        assertThrows(IllegalArgumentException.class,()->PinnedPlayer.capture("s","d",ID,"Player",Double.POSITIVE_INFINITY,1,1,1000));
        assertThrows(IllegalArgumentException.class,()->PinnedPlayer.capture("s","d",ID,"Player",1e90,1,1,1000));
        assertThrows(IllegalArgumentException.class,()->new PinnedPlayer("s","d","not-an-id","Player",1,1,1,1000));
        assertThrows(IllegalArgumentException.class,()->PinnedPlayer.capture("","d",ID,"Player",1,1,1,1000));
    }
}
