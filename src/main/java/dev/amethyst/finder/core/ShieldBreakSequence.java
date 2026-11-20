package dev.amethyst.finder.core;

/** One physical use click can request one axe strike; never follows with a mace attack. */
public final class ShieldBreakSequence {
    public enum Action { WAIT, AXE, CANCEL }
    private int age;
    private boolean done;
    public Action next(boolean valid, boolean blocking, boolean cooldownReady) {
        if (done) return Action.WAIT;
        if (!valid || !blocking || ++age > 30) { done = true; return Action.CANCEL; }
        if (!cooldownReady) return Action.WAIT;
        done = true;
        return Action.AXE;
    }
}
