package dev.amethyst.finder.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactPlayerTagLayoutTest {
    @Test void defaultTagIsCompactAndCenteredAbovePlayer() {
        var p = CompactPlayerTagLayout.fit(400, 300, 180, 28, .70, 800, 600);
        assertEquals(.70, p.scale(), 1e-6);
        assertEquals(337, p.x(), 1e-6);
        assertEquals(277.4, p.y(), 1e-4);
        assertEquals(19.6, p.height() * p.scale(), 1e-4);
    }

    @Test void allScreenEdgesKeepTheWholeScaledPlateWithinViewport() {
        for (double scale : new double[]{.45, .7, 1})
            for (double x : new double[]{-100, 0, 400, 800, 1000})
                for (double y : new double[]{-100, 0, 300, 600, 1000}) {
                    var p = CompactPlayerTagLayout.fit(x, y, 180, 28, scale, 800, 600);
                    assertTrue(p.x() >= 4 && p.y() >= 4);
                    assertTrue(p.x() + p.width() * p.scale() <= 796.0001);
                    assertTrue(p.y() + p.height() * p.scale() <= 596.0001);
                }
    }

    @Test void LongNamesAndTinyWindowsShrinkInsteadOfClippingOutsideTheScreen() {
        for (int width : new int[]{1, 24, 80, 800}) {
            var p = CompactPlayerTagLayout.fit(10, 5, 2000, 28, 1, width, 40);
            assertNotNull(p);
            assertTrue(p.x() >= 0 && p.y() >= 0);
            assertTrue(p.x() + p.width() * p.scale() <= width + .0001);
            assertTrue(p.y() + p.height() * p.scale() <= 40.0001);
        }
    }

    @Test void untrustedScaleHasSafeDefaultAndInvalidGeometryIsRejected() {
        assertEquals(.50f, CompactPlayerTagLayout.fit(100, 100, 100, 28, Double.NaN, 800, 600).scale(), 1e-6);
        assertEquals(.16f, CompactPlayerTagLayout.fit(100, 100, 100, 28, -1, 800, 600).scale(), 1e-6);
        assertEquals(1, CompactPlayerTagLayout.fit(100, 100, 100, 28, 9, 800, 600).scale(), 1e-6);
        assertNull(CompactPlayerTagLayout.fit(Double.NaN, 100, 100, 28, .7, 800, 600));
        assertNull(CompactPlayerTagLayout.fit(100, 100, 0, 28, .7, 800, 600));
        assertNull(CompactPlayerTagLayout.fit(100, 100, 100, 28, .7, 0, 600));
    }
}
