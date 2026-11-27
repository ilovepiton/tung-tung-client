package dev.amethyst.finder;
public final class FriendFaceProbe {
 public static void main(String[] args)throws Exception{
  long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
  while(System.nanoTime()<end){String face=PlayerFaces.friendFace("Notch","");if(face.startsWith("data:image/png;base64,")){
   var png=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(face.substring(face.indexOf(',')+1))));
   if(png.getWidth()!=8||png.getHeight()!=8)throw new AssertionError("Wrong face dimensions");
   System.out.println("PASS: official name lookup → session skin → 8×8 face; cache reused case-insensitively: "+face.equals(PlayerFaces.friendFace("NOTCH","")));return;
  }Thread.sleep(100);}throw new AssertionError("Public profile face did not load within the probe timeout");
 }
}
