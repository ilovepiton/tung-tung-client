package dev.amethyst.finder.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerIndicatorGeometryTest {
    @Test void visibleTargetKeepsItsActualProjection() {
        var p = PlayerIndicatorGeometry.project(.5, .25, 1, 800, 600, 24);
        assertFalse(p.offscreen());
        assertEquals(600, p.x(), 1e-9);
        assertEquals(225, p.y(), 1e-9);
        assertEquals(1, Math.hypot(p.directionX(), p.directionY()), 1e-9);
    }

    @Test void offscreenTargetIntersectsPaddedEdgeWithoutChangingDirection() {
        var p = PlayerIndicatorGeometry.project(5, -2, 1, 800, 600, 24);
        assertTrue(p.offscreen());
        assertEquals(776, p.x(), 1e-9);
        assertEquals(.3, (p.y() - 300) / (p.x() - 400), 1e-9);
        assertTrue(p.y() >= 24 && p.y() <= 576);
    }

    @Test void behindTargetKeepsCorrectTurnSideAndAxisCaseIsDefined() {
        var left = PlayerIndicatorGeometry.project(-.5, 0, -1, 800, 600, 24);
        var right = PlayerIndicatorGeometry.project(.5, 0, -1, 800, 600, 24);
        var behind = PlayerIndicatorGeometry.project(0, 0, -1, 800, 600, 24);
        assertEquals(24, left.x(), 1e-9);
        assertEquals(776, right.x(), 1e-9);
        assertEquals(400, behind.x(), 1e-9);
        assertEquals(576, behind.y(), 1e-9);
        assertTrue(behind.offscreen());
    }

    @Test void nearPlaneAndExtremeCoordinatesAlwaysProduceFiniteBoundedResults() {
        for (double w : new double[] {-1, 0, .001, .05, .051, 1, Double.MAX_VALUE})
            for (double x : new double[] {-Double.MAX_VALUE, -2, 0, 2, Double.MAX_VALUE}) {
                var p = PlayerIndicatorGeometry.project(x, .5, w, 800, 600, 24);
                assertNotNull(p);
                assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()));
                assertTrue(p.x() >= 24 - 1e-9 && p.x() <= 776 + 1e-9);
                assertTrue(p.y() >= 24 - 1e-9 && p.y() <= 576 + 1e-9);
                assertEquals(1, Math.hypot(p.directionX(), p.directionY()), 1e-9);
            }
    }

    @Test void invalidInputsNeverReachGuiDrawing() {
        assertNull(PlayerIndicatorGeometry.project(Double.NaN, 0, 1, 800, 600, 24));
        assertNull(PlayerIndicatorGeometry.project(0, Double.POSITIVE_INFINITY, 1, 800, 600, 24));
        assertNull(PlayerIndicatorGeometry.project(0, 0, Double.NaN, 800, 600, 24));
        assertNull(PlayerIndicatorGeometry.project(0, 0, 1, 0, 600, 24));
        assertNull(PlayerIndicatorGeometry.project(0, 0, 1, 800, 600, -1));
        assertNull(PlayerIndicatorGeometry.project(0, 0, 0, 800, 600, 24));
    }
}
