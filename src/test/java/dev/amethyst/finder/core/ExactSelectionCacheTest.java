package dev.amethyst.finder.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ExactSelectionCacheTest {
    record Key(long revision, double x, int range, boolean exposed) {}

    @Test void equalInputsReuseTheExactResultWithoutComputing() {
        var cache=new ExactSelectionCache<Key,List<Integer>>();
        var calls=new AtomicInteger();
        var key=new Key(1,.5,64,true);
        var first=cache.get(key,()->{calls.incrementAndGet();return List.of(3,1,2);});
        for(int i=0;i<1000;i++)assertSame(first,cache.get(new Key(1,.5,64,true),()->{calls.incrementAndGet();return List.of();}));
        assertEquals(1,calls.get());
    }

    @Test void everyChangedInputAndExplicitClearRecomputes() {
        var cache=new ExactSelectionCache<Key,Integer>();
        int i=0;
        for(var key:List.of(new Key(1,0,64,true),new Key(2,0,64,true),new Key(2,.01,64,true),new Key(2,.01,65,true),new Key(2,.01,65,false))) {
            int expected=++i;assertEquals(expected,cache.get(key,()->expected));
        }
        cache.clear();assertEquals(99,cache.get(new Key(2,.01,65,false),()->99));
    }

    @Test void failedComputationDoesNotPoisonTheNextAttempt() {
        var cache=new ExactSelectionCache<Integer,String>();
        assertEquals("before",cache.get(1,()->"before"));
        assertThrows(IllegalStateException.class,()->cache.get(2,()->{throw new IllegalStateException();}));
        assertEquals("before",cache.get(1,()->"unexpected"));
        assertEquals("after",cache.get(2,()->"after"));
    }
}
