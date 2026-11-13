package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ObservationRetentionTest {
 @Test void moreThan2048CurrentChunksCannotEvictAnOldLiveFinding() {
  var keys=new ArrayList<Long>();var loaded=new HashSet<Long>();
  for(long i=0;i<4200;i++){keys.add(i);loaded.add(i);}
  assertTrue(ObservationRetention.evictions(keys,loaded,2048).isEmpty());
  for(long i=4200;i<6300;i++)keys.add(i);
  var removed=ObservationRetention.evictions(keys,loaded,2048);
  assertEquals(52,removed.size());assertEquals(4200L,removed.getFirst());
  assertTrue(Collections.disjoint(removed,loaded));
 }
 @Test void unloadingMakesOldRecordsEligibleForNormalHistoryEviction() {
  assertEquals(List.of(1L),ObservationRetention.evictions(List.of(1L,2L,3L),Set.of(3L),1));
  assertTrue(ObservationRetention.evictions(List.of(1L,2L,3L),Set.of(1L,2L),1).isEmpty());
 }
}
