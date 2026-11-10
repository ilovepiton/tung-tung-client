package dev.amethyst.finder.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkSelectionMathTest {
    @Test void negativeChunkBoundsUseFullExclusiveWorldHeight() {
        var b=ChunkSelectionMath.Bounds.chunk(-2,-3,-64,320);
        assertEquals(-32,b.minX());assertEquals(-16,b.maxX());
        assertEquals(-48,b.minZ());assertEquals(-32,b.maxZ());
        assertEquals(-64,b.minY());assertEquals(320,b.maxY());
    }
    @Test void inclusiveMinecraftMaximumBecomesExclusiveTopFace() {
        assertEquals(320,ChunkSelectionMath.exclusiveWorldMaxY(319));
        assertEquals(256,ChunkSelectionMath.exclusiveWorldMaxY(255));
        var b=ChunkSelectionMath.Bounds.chunk(0,0,-64,ChunkSelectionMath.exclusiveWorldMaxY(319));
        assertEquals(384,b.maxY()-b.minY());
    }
    @Test void largeWorldCoordinatesNeverOverflowChunkMultiplication() {
        var b=ChunkSelectionMath.Bounds.chunk(Integer.MAX_VALUE,Integer.MIN_VALUE,0,256);
        assertEquals(34359738352d,b.minX());assertEquals(-34359738368d,b.minZ());
        assertEquals(16,b.maxX()-b.minX());
    }
    @Test void selectionIntersectsWholeHeightNotObservationY() {
        var b=ChunkSelectionMath.Bounds.chunk(1,0,-64,320);
        assertEquals(16,ChunkSelectionMath.rayDistance(b,0,200,8,1,0,0),1e-9);
        assertEquals(Double.POSITIVE_INFINITY,ChunkSelectionMath.rayDistance(b,0,321,8,1,0,0));
    }
    @Test void exclusiveParallelMaximumDoesNotSelectTwoNeighborChunks() {
        var a=ChunkSelectionMath.Bounds.chunk(0,0,-64,320);
        var b=ChunkSelectionMath.Bounds.chunk(1,0,-64,320);
        assertEquals(Double.POSITIVE_INFINITY,ChunkSelectionMath.rayDistance(a,16,10,-10,0,0,1));
        assertEquals(10,ChunkSelectionMath.rayDistance(b,16,10,-10,0,0,1),1e-9);
    }
    @Test void behindCameraChunksAndInvalidDirectionsAreNotSelected() {
        var b=ChunkSelectionMath.Bounds.chunk(-2,0,-64,320);
        assertEquals(Double.POSITIVE_INFINITY,ChunkSelectionMath.rayDistance(b,0,64,8,1,0,0));
        assertEquals(Double.POSITIVE_INFINITY,ChunkSelectionMath.rayDistance(b,0,64,8,0,0,0));
        assertEquals(Double.POSITIVE_INFINITY,ChunkSelectionMath.rayDistance(b,0,64,8,Double.NaN,0,0));
    }
    @Test void edgesCrossingNearCameraPlaneAreClippedRatherThanDropped() {
        var clipped=ChunkSelectionMath.clipSegment(new ChunkSelectionMath.ClipPoint(0,0,-1),new ChunkSelectionMath.ClipPoint(0,0,2)).orElseThrow();
        assertEquals(.05,clipped.a().w(),1e-9);assertEquals(2,clipped.b().w());
    }
    @Test void offscreenEdgesClipToBoundedScreenCoordinates() {
        var clipped=ChunkSelectionMath.clipSegment(new ChunkSelectionMath.ClipPoint(-10,.5,1),new ChunkSelectionMath.ClipPoint(10,.5,1)).orElseThrow();
        assertEquals(-1,clipped.a().x(),1e-9);assertEquals(1,clipped.b().x(),1e-9);
        assertEquals(.5,clipped.a().y(),1e-9);
        assertTrue(ChunkSelectionMath.clipSegment(new ChunkSelectionMath.ClipPoint(2,0,1),new ChunkSelectionMath.ClipPoint(3,0,1)).isEmpty());
    }
    @Test void whollyBehindAndNonfiniteSegmentsNeverEmit() {
        assertTrue(ChunkSelectionMath.clipSegment(new ChunkSelectionMath.ClipPoint(0,0,-2),new ChunkSelectionMath.ClipPoint(1,1,-1)).isEmpty());
        assertTrue(ChunkSelectionMath.clipSegment(new ChunkSelectionMath.ClipPoint(Double.NaN,0,1),new ChunkSelectionMath.ClipPoint(1,1,1)).isEmpty());
    }
    @Test void smoothFadeIsBoundedContinuousAndTimeBased() {
        assertEquals(0,ChunkSelectionMath.fade(-100,200));assertEquals(0,ChunkSelectionMath.fade(0,200));
        assertEquals(.5,ChunkSelectionMath.fade(100,200),1e-9);assertEquals(1,ChunkSelectionMath.fade(300,200));
        assertEquals(0,ChunkSelectionMath.fade(Double.NaN,200));
    }
    @Test void vanillaRectangleWindingSurvivesGuiBackFaceCulling() {
        // Actual ColoredRectangleRenderState order from official 26.3: TL, BL, BR, TR.
        assertFalse(ChunkSelectionMath.reverseForGui(0,0,0,10,20,10,20,0));
        // The former line batch used the opposite order and was invisible on the actual GPU.
        assertTrue(ChunkSelectionMath.reverseForGui(0,0,20,0,20,10,0,10));
    }
    @Test void fanTrianglesNormalizeEitherProjectedWindingWithoutLosingTheirGeometry() {
        assertTrue(ChunkSelectionMath.reverseForGui(0,0,20,0,20,10,20,10));
        assertFalse(ChunkSelectionMath.reverseForGui(0,0,20,10,20,0,20,0));
        assertFalse(ChunkSelectionMath.reverseForGui(0,0,20,0,20,0,20,0));
    }
}
