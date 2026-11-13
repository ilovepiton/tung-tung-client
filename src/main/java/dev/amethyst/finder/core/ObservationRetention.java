package dev.amethyst.finder.core;
import java.util.*;
/** The history quota must never evict observations that are still loaded in the current world. */
public final class ObservationRetention {
 private ObservationRetention() {}
 public static List<Long> evictions(Collection<Long> oldestFirst,Set<Long> loaded,int historyLimit) {
  int historical=0;for(long key:oldestFirst)if(!loaded.contains(key))historical++;
  int excess=historical-Math.max(0,historyLimit);if(excess<=0)return List.of();
  List<Long> result=new ArrayList<>(excess);
  for(long key:oldestFirst)if(!loaded.contains(key)) {result.add(key);if(result.size()==excess)break;}
  return List.copyOf(result);
 }
}
