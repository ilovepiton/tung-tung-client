package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShieldBreakSequenceTest {
 @Test void onePressCanRequestOnlyOneAxeAttack(){var g=new ClickActionGate();g.mouse(1,true);var t=g.consume();assertNotNull(t);assertTrue(t.claimAttack(true));assertFalse(t.claimAttack(true));assertNull(g.consume());}
 @Test void ignoredUnshieldedClickDoesNotBecomeADelayedAttack(){var g=new ClickActionGate();g.mouse(1,false);assertNull(g.consume());g.mouse(1,true);assertNull(g.consume());}
 @Test void heldButtonDoesNotRepeatAfterAnAcceptedAttack(){var g=new ClickActionGate();g.mouse(1,true);g.consume().claimAttack(true);for(int i=0;i<40;i++){g.mouse(1,true);assertNull(g.consume());}}
 @Test void newClickRequiresRelease(){var g=new ClickActionGate();g.mouse(1,true);assertNotNull(g.consume());g.mouse(0,false);g.mouse(1,true);assertNotNull(g.consume());}
 @Test void menuOrFocusResetClearsPendingGesture(){var g=new ClickActionGate();g.mouse(1,true);var t=g.consume();g.reset();assertFalse(t.claimAttack(true));assertNull(g.consume());}
}
