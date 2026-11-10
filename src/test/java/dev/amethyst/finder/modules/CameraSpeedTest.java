package dev.amethyst.finder.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraSpeedTest {
    @Test void defaultRetainsThePreviousNormalAndBoostedCameraRates() {
        assertEquals(0.6, CameraSpeed.effectiveBlocksPerTick(CameraSpeed.DEFAULT, false), 1e-12);
        assertEquals(1.8, CameraSpeed.effectiveBlocksPerTick(CameraSpeed.DEFAULT, true), 1e-12);
    }

    @Test void fullRequestedSpeedRangeRemainsSelectable() {
        assertEquals(0.025, CameraSpeed.effectiveBlocksPerTick(CameraSpeed.MIN, false), 1e-12);
        assertEquals(128, CameraSpeed.effectiveBlocksPerTick(CameraSpeed.MAX, false), 1e-12);
        assertEquals(0.5, CameraSpeed.blocksPerSecond(CameraSpeed.MIN, false), 1e-12);
        assertEquals(2560, CameraSpeed.blocksPerSecond(CameraSpeed.MAX, false), 1e-12);
    }

    @Test void sprintBoostScalesEveryConfiguredRateInsteadOfUsingTheOldFixedCap() {
        for (double speed : new double[]{CameraSpeed.MIN, 0.1, CameraSpeed.DEFAULT, 2, 12, CameraSpeed.MAX}) {
            assertEquals(CameraSpeed.effectiveBlocksPerTick(speed, false) * 3,
                    CameraSpeed.effectiveBlocksPerTick(speed, true), 1e-12);
        }
        assertEquals(384, CameraSpeed.effectiveBlocksPerTick(CameraSpeed.MAX, true), 1e-12);
        assertEquals(7680, CameraSpeed.blocksPerSecond(CameraSpeed.MAX, true), 1e-12);
    }

    @Test void malformedOrOutOfRangeSavedValuesCannotPoisonCameraCoordinates() {
        assertEquals(CameraSpeed.MIN, CameraSpeed.sanitize(-1));
        assertEquals(CameraSpeed.MIN, CameraSpeed.sanitize(0));
        assertEquals(CameraSpeed.MAX, CameraSpeed.sanitize(Double.MAX_VALUE));
        for (double value : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(CameraSpeed.DEFAULT, CameraSpeed.sanitize(value));
            assertTrue(Double.isFinite(CameraSpeed.effectiveBlocksPerTick(value, true)));
        }
    }

    @Test void validInteriorValuesArePreservedWithoutPresetRounding() {
        assertEquals(0.0375, CameraSpeed.sanitize(0.0375));
        assertEquals(7.123456789, CameraSpeed.sanitize(7.123456789));
        assertEquals(127.999, CameraSpeed.sanitize(127.999));
    }
}
