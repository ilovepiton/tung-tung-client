package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CaveCoverageTest {
 @Test void incompleteAndDuplicateColumnsNeverCountAsClear() {
  var c=new CaveCoverage();assertFalse(c.clear());
  for(int i=0;i<255;i++)c.finishColumn(i,true);
  c.finishColumn(0,true);assertFalse(c.clear());assertEquals(255,c.columns());
  c.finishColumn(255,true);assertTrue(c.clear());
 }
 @Test void AnyCavityOrVoidBandExcludesTheChunk() {
  for(boolean cavity:new boolean[]{false,true}) {
   var c=new CaveCoverage();for(int i=0;i<256;i++)c.finishColumn(i,cavity||i!=23);
   if(cavity)c.foundCavity();assertTrue(c.rejected());assertFalse(c.clear());
  }
 }
 @Test void rejectsInvalidColumnNumbers() {assertThrows(IllegalArgumentException.class,()->new CaveCoverage().finishColumn(256,true));}
}
