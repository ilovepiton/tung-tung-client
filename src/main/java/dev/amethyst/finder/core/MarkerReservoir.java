package dev.amethyst.finder.core;

import java.util.*;

/** Bounded, deterministic position sample without scan-order or low-Y bias. */
public final class MarkerReservoir {
    public static final int LIMIT=8192;
    private record Entry(BlockMark mark,long rank) {}
    private final PriorityQueue<Entry> entries=new PriorityQueue<>((a,b)->Long.compareUnsigned(b.rank,a.rank));
    public void add(BlockMark mark) {
        long rank=mix(ChunkEvidence.blockKey(mark.x(),mark.y(),mark.z())^((long)mark.signal().ordinal()<<48));
        if(entries.size()<LIMIT)entries.add(new Entry(mark,rank));
        else if(Long.compareUnsigned(rank,entries.peek().rank)<0) { entries.poll();entries.add(new Entry(mark,rank)); }
    }
    public List<BlockMark> snapshot() {
        return entries.stream().map(Entry::mark).sorted(Comparator.comparingInt(BlockMark::y)
                .thenComparingInt(BlockMark::x).thenComparingInt(BlockMark::z).thenComparing(BlockMark::signal)).toList();
    }
    private static long mix(long x) { x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31); }
}
