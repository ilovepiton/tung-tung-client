package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SusChunkRulesTest {
 @Test void nearbyAndFarChunksUseHorizontalFootprint() {
  assertTrue(SusChunkRules.within(0,0,0,0,1));
  assertFalse(SusChunkRules.within(20,0,0,0,128));
  assertTrue(SusChunkRules.within(20,0,0,0,512));
  assertTrue(SusChunkRules.within(-1,-1,0,0,1));
  assertFalse(SusChunkRules.within(0,0,Double.NaN,0,512));
 }
}
