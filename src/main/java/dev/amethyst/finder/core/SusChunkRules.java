package dev.amethyst.finder.core;
/** Horizontal distance to a chunk footprint, including the chunk beneath the camera at tiny ranges. */
public final class SusChunkRules {
 private SusChunkRules() {}
 public static boolean within(int x,int z,double eyeX,double eyeZ,int range) {
  if(!Double.isFinite(eyeX)||!Double.isFinite(eyeZ)||range<1)return false;
  double minX=x*16.0,minZ=z*16.0;
  double dx=Math.max(minX-eyeX,Math.max(0,eyeX-(minX+16))),dz=Math.max(minZ-eyeZ,Math.max(0,eyeZ-(minZ+16)));
  return dx*dx+dz*dz<=range*(double)range;
 }
}
