package dev.amethyst.finder.core;

/** One physical click: one axe strike, then one mace strike after observed unblock. */
public final class ShieldBreakSequence {
    public enum Action { WAIT, AXE, MACE, CANCEL }
    private int age, axeAt = -1;
    private boolean done;
    public Action next(boolean valid, boolean blocking, boolean cooldownReady) {
        if (done) return Action.WAIT;
        if (!valid || ++age > 60) { done = true; return Action.CANCEL; }
        if (axeAt < 0) {
            if (!blocking) { done = true; return Action.CANCEL; }
            if (!cooldownReady) return Action.WAIT;
            axeAt = age;
            return Action.AXE;
        }
        if (blocking) {
            if (age - axeAt > 20) { done = true; return Action.CANCEL; }
            return Action.WAIT;
        }
        if (!cooldownReady) return Action.WAIT;
        done = true;
        return Action.MACE;
    }
}
