package dev.amethyst.finder.modules;

/** One armed descent, one connection attempt. No retry loop or follow-up movement. */
public final class RelogPolicy {
    private int belowTicks;
    private boolean armed;
    public void arm() { armed=true;belowTicks=0; }
    public void cancel() { armed=false;belowTicks=0; }
    public boolean observe(double y,int threshold,boolean eligible) {
        if(!armed)return false;
        if(!eligible||!Double.isFinite(y)||y>threshold) { belowTicks=0;return false; }
        if(++belowTicks<20)return false;
        cancel();return true;
    }
}
