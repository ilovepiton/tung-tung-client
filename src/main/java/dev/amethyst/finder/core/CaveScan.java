package dev.amethyst.finder.core;

/** Resumable exact terrain walk. Roof discovery shares the block-read budget. */
public final class CaveScan {
    public static final int OTHER=0,SOLID=1,CAVITY=2;
    @FunctionalInterface public interface Terrain {int sample(int x,int y,int z);}
    private final Terrain terrain;
    private final int floor,top;
    private final CaveCoverage coverage=new CaveCoverage();
    private int column,y;
    private boolean roof,done;
    public CaveScan(int minY,int maxYInclusive,Terrain terrain) {
        floor=minY+5;top=maxYInclusive;y=top;this.terrain=terrain;
    }
    public boolean clear() {return done&&coverage.clear();}
    public boolean step(int maxReads) {
        if(maxReads<1)throw new IllegalArgumentException("Read budget must be positive");
        if(done)return true;
        for(int n=0;n<maxReads&&column<256;n++) {
            if(y<floor) {coverage.finishColumn(column,false);return done=true;}
            int kind=terrain.sample(column&15,y,column>>4);
            if(!roof) {
                if(kind==SOLID) {roof=true;y-=5;}
                else y--;
                continue;
            }
            if(kind==CAVITY) {coverage.foundCavity();return done=true;}
            if(--y<floor) {coverage.finishColumn(column,true);column++;roof=false;y=top;}
        }
        return done=column==256;
    }
}
