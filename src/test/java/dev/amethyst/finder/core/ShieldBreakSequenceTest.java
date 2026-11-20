package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.amethyst.finder.core.ShieldBreakSequence.Action.*;
class ShieldBreakSequenceTest {
    @Test void waitsForCooldownThenUsesExactlyOneAxeAndOneMace() {
        var combo = new ShieldBreakSequence();
        assertEquals(WAIT, combo.next(true, true, false));
        assertEquals(AXE, combo.next(true, true, true));
        assertEquals(WAIT, combo.next(true, true, true));
        assertEquals(WAIT, combo.next(true, false, false));
        assertEquals(MACE, combo.next(true, false, true));
        for (int i=0; i<100; i++) assertEquals(WAIT, combo.next(true, false, true));
    }
    @Test void shieldMustBeRaisedBeforeTheAxeStrike() {
        var combo = new ShieldBreakSequence();
        assertEquals(CANCEL, combo.next(true, false, true));
        assertEquals(WAIT, combo.next(true, true, true));
    }
    @Test void blockedAxeNeverSpamsAndNeverAttemptsAMaceThroughTheShield() {
        var combo = new ShieldBreakSequence(); int axe=0,mace=0,cancel=0;
        for(int i=0;i<100;i++) { var a=combo.next(true,true,true); if(a==AXE)axe++;if(a==MACE)mace++;if(a==CANCEL)cancel++; }
        assertEquals(1,axe);assertEquals(0,mace);assertEquals(1,cancel);
    }
    @Test void lostTargetOrChangedSlotCancelsBeforeAnyFurtherAttack() {
        var combo = new ShieldBreakSequence(); assertEquals(AXE,combo.next(true,true,true));
        assertEquals(CANCEL,combo.next(false,false,true));assertEquals(WAIT,combo.next(true,false,true));
    }
    @Test void aQuickReleasedClickStillCompletesButHeldClickCannotStartAnotherCombo() {
        var gate=new ClickActionGate();gate.mouse(1,true);assertNotNull(gate.consume());gate.mouse(0,true);
        var combo=new ShieldBreakSequence();assertEquals(AXE,combo.next(true,true,true));assertEquals(MACE,combo.next(true,false,true));
        assertNull(gate.consume());gate.mouse(1,true);assertNotNull(gate.consume());
        for(int i=0;i<20;i++){gate.mouse(1,true);assertNull(gate.consume());}
    }
}
