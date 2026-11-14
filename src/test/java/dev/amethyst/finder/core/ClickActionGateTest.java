package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClickActionGateTest {
 @Test void heldUseAndDuplicateDownEventsNeverRepeat() {
  var g=new ClickActionGate();assertNull(g.consume());g.mouse(1,true);assertNotNull(g.consume());
  for(int i=0;i<100;i++){g.mouse(1,true);assertNull(g.consume());}
  g.mouse(0,true);g.mouse(1,true);assertNotNull(g.consume());
 }
 @Test void quickTapCanAttackAfterReleaseButOnlyOnce() {
  var g=new ClickActionGate();g.mouse(1,true);var t=g.consume();g.mouse(0,true);
  assertFalse(t.claimAttack(false));assertTrue(t.claimAttack(true));assertFalse(t.claimAttack(true));
 }
 @Test void focusOrSessionResetInvalidatesPendingTickets() {
  var g=new ClickActionGate();g.mouse(1,true);var t=g.consume();g.reset();assertFalse(t.claimAttack(true));assertNull(g.consume());
 }
 @Test void ignoredClickCannotBecomeADelayedHeldPlacement() {
  var g=new ClickActionGate();g.mouse(1,false);assertNull(g.consume());g.mouse(1,true);assertNull(g.consume());
  g.mouse(0,true);g.mouse(1,true);assertNotNull(g.consume());assertNull(g.consume());
 }
}
