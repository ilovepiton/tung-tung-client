package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CaveScanTest {
 static class Ground implements CaveScan.Terrain {
  int reads,cx=-1,cy,cz;boolean voidBand;
  public int sample(int x,int y,int z){
   reads++;
   if(y>64||voidBand&&x==15&&z==15||x==cx&&y==cy&&z==cz)return CaveScan.CAVITY;
   return CaveScan.SOLID;
  }
 }
 @Test void solidTerrainNeedsEveryColumnAndRoofReadsShareBudget() {
  var ground=new Ground();var scan=new CaveScan(-64,319,ground);
  assertFalse(scan.clear());assertFalse(scan.step(1));assertEquals(1,ground.reads);
  while(!scan.step(256))assertFalse(scan.clear());
  assertTrue(scan.clear());assertEquals(256*(256+119),ground.reads);
  int reads=ground.reads;assertTrue(scan.step(256));assertEquals(reads,ground.reads);
 }
 @Test void cavityAtLastColumnBottomCannotSlipThrough() {
  var ground=new Ground();ground.cx=15;ground.cz=15;ground.cy=-59;
  var scan=new CaveScan(-64,319,ground);while(!scan.step(256)){}assertFalse(scan.clear());
 }
 @Test void voidColumnAndIncompleteChunkAreNeverClear() {
  var ground=new Ground();ground.voidBand=true;var scan=new CaveScan(-64,319,ground);
  while(!scan.step(256)){}assertFalse(scan.clear());
 }
 @Test void floorAndSurfaceMarginsAreExcluded() {
  for(int y:new int[]{-64,60}) {
   var ground=new Ground();ground.cx=0;ground.cz=0;ground.cy=y;
   var scan=new CaveScan(-64,319,ground);while(!scan.step(256)){}assertTrue(scan.clear());
  }
 }
}
