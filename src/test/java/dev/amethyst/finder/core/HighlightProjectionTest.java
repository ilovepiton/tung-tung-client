package dev.amethyst.finder.core;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.amethyst.finder.core.HighlightProjection.Vertex;

class HighlightProjectionTest {
    private static Vertex vertex(double x, double y, double w, float u, float v) {
        return new Vertex(x, y, 0, w, u, v);
    }

    @Test void fullyBehindCameraIsRejected() {
        assertTrue(HighlightProjection.clip(List.of(vertex(-1, -1, -1, 0, 0),
                vertex(1, -1, -1, 1, 0), vertex(1, 1, -1, 1, 1),
                vertex(-1, 1, -1, 0, 1))).isEmpty());
    }

    @Test void crossingNearPlaneRetainsFaceAndInterpolatesTextureCoordinates() {
        var clipped = HighlightProjection.clip(List.of(vertex(-0.01, -0.01, -0.1, 0, 0),
                vertex(0.01, -0.01, 0.1, 1, 0), vertex(0.01, 0.01, 0.1, 1, 1),
                vertex(-0.01, 0.01, -0.1, 0, 1)));
        assertEquals(4, clipped.size());
        var intersections = clipped.stream().filter(v -> v.w() == HighlightProjection.MIN_W).toList();
        assertEquals(2, intersections.size());
        for (Vertex v : intersections) {
            assertEquals(0.005, v.x(), 1e-12);
            assertEquals(0.75f, v.u(), 1e-6f);
        }
        assertSafe(clipped);
    }

    @Test void screenEdgeClippingPreservesUvsAndOrderedWinding() {
        var original = List.of(vertex(-0.5, -0.5, 1, 0, 0), vertex(2, -0.5, 1, 1, 0),
                vertex(2, 0.5, 1, 1, 1), vertex(-0.5, 0.5, 1, 0, 1));
        var clipped = HighlightProjection.clip(original);
        assertEquals(4, clipped.size());
        assertEquals(clipped, HighlightProjection.clip(original));
        var intersections = clipped.stream().filter(v -> v.x() == v.w()).toList();
        assertEquals(2, intersections.size());
        for (Vertex v : intersections) assertEquals(0.6f, v.u(), 1e-6f);
        assertTrue(area(clipped) > 0);
        assertSafe(clipped);
        var reversed = new java.util.ArrayList<>(original);
        java.util.Collections.reverse(reversed);
        assertTrue(area(HighlightProjection.clip(reversed)) < 0);
    }

    @Test void boundariesAreKeptWithoutRepeatedIntersectionVertices() {
        var boundary = List.of(vertex(-0.05, -0.05, 0.05, 0, 0),
                vertex(0.05, -0.05, 0.05, 1, 0), vertex(0.05, 0.05, 0.05, 1, 1),
                vertex(-0.05, 0.05, 0.05, 0, 1));
        assertEquals(boundary, HighlightProjection.clip(boundary));
        var touching = HighlightProjection.clip(List.of(vertex(-2, 0, 1, 0, 0),
                vertex(-1, -0.5, 1, 0.5f, 0), vertex(0, 0.5, 1, 1, 1)));
        assertEquals(3, touching.size());
        assertEquals(touching.size(), touching.stream().distinct().count());
    }

    @Test void invalidFacesCannotEmitNonfiniteGuiCoordinates() {
        Vertex good = vertex(0, 0, 1, 0, 0);
        assertTrue(HighlightProjection.clip(null).isEmpty());
        assertTrue(HighlightProjection.clip(List.of(good, good)).isEmpty());
        for (Vertex bad : List.of(new Vertex(Double.NaN, 0, 0, 1, 0, 0),
                new Vertex(0, 0, Double.POSITIVE_INFINITY, 1, 0, 0),
                new Vertex(0, 0, 0, 1, Float.NaN, 0))) {
            assertTrue(HighlightProjection.clip(List.of(good, good, bad)).isEmpty());
            assertTrue(HighlightProjection.trianglesAsQuads(List.of(good, good, bad)).isEmpty());
        }
        assertTrue(HighlightProjection.clip(Arrays.asList(good, good, null)).isEmpty());
    }

    @Test void finiteExtremeCoordinatesDoNotOverflowIntersectionMath() {
        var clipped = HighlightProjection.clip(List.of(vertex(-Double.MAX_VALUE, -0.5, 1, 0, 0),
                vertex(Double.MAX_VALUE, -0.5, 1, 1, 0),
                vertex(Double.MAX_VALUE, 0.5, 1, 1, 1),
                vertex(-Double.MAX_VALUE, 0.5, 1, 0, 1)));
        assertFalse(clipped.isEmpty());
        assertSafe(clipped);
    }

    @Test void triangleFanQuadsPreservePolygonAreaAndDoNotAddClosingEdges() {
        var polygon = HighlightProjection.clip(List.of(vertex(-2, -0.5, 1, 0, 0),
                vertex(0, -2, 1, 1, 0), vertex(2, -0.5, 1, 1, 1),
                vertex(0, 0.75, 1, 0, 1)));
        var quads = HighlightProjection.trianglesAsQuads(polygon);
        assertEquals((polygon.size() - 2) * 4, quads.size());
        double triangleArea = 0;
        for (int i = 0; i < quads.size(); i += 4) {
            assertEquals(quads.get(i + 2), quads.get(i + 3));
            triangleArea += area(quads.subList(i, i + 3));
        }
        assertEquals(area(polygon), triangleArea, 1e-12);
        assertThrows(UnsupportedOperationException.class, () -> quads.add(polygon.getFirst()));
    }

    private static void assertSafe(List<Vertex> vertices) {
        for (Vertex v : vertices) {
            assertTrue(v.w() >= HighlightProjection.MIN_W);
            assertTrue(v.x() >= -v.w() && v.x() <= v.w());
            assertTrue(v.y() >= -v.w() && v.y() <= v.w());
            assertTrue(Double.isFinite(v.x() / v.w()) && Double.isFinite(v.y() / v.w()));
            assertTrue(Double.isFinite(v.z()) && Float.isFinite(v.u()) && Float.isFinite(v.v()));
        }
    }

    private static double area(List<Vertex> vertices) {
        double area = 0;
        for (int i = 0; i < vertices.size(); i++) {
            Vertex a = vertices.get(i), b = vertices.get((i + 1) % vertices.size());
            area += a.x() / a.w() * (b.y() / b.w()) - b.x() / b.w() * (a.y() / a.w());
        }
        return area * 0.5;
    }
}
