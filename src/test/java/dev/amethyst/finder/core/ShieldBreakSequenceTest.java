package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.amethyst.finder.core.ShieldBreakSequence.Action.*;
class ShieldBreakSequenceTest {
 @Test void oneClickRequestsExactlyOneAxeStrikeEvenWhenShieldLaterLowers() {
  var combo=new ShieldBreakSequence();assertEquals(WAIT,combo.next(true,true,false));assertEquals(AXE,combo.next(true,true,true));
  for(int i=0;i<100;i++)assertEquals(WAIT,combo.next(true,i%2==0,true));
 }
 @Test void noShieldMeansNoAxeStrike() {var combo=new ShieldBreakSequence();assertEquals(CANCEL,combo.next(true,false,true));assertEquals(WAIT,combo.next(true,true,true));}
 @Test void loweringShieldBeforeCooldownCancelsThePendingAxe() {var combo=new ShieldBreakSequence();assertEquals(WAIT,combo.next(true,true,false));assertEquals(CANCEL,combo.next(true,false,true));}
 @Test void lostTargetAndTimeoutCancelInsteadOfAttacking() {
  var lost=new ShieldBreakSequence();assertEquals(CANCEL,lost.next(false,true,true));
  var timeout=new ShieldBreakSequence();for(int i=0;i<30;i++)assertEquals(WAIT,timeout.next(true,true,false));assertEquals(CANCEL,timeout.next(true,true,true));
 }
 @Test void quickReleaseAndHeldButtonNeverAddAnotherAttack() {
  var gate=new ClickActionGate();gate.mouse(1,true);assertNotNull(gate.consume());gate.mouse(0,true);
  var combo=new ShieldBreakSequence();assertEquals(AXE,combo.next(true,true,true));assertEquals(WAIT,combo.next(true,false,true));
  assertNull(gate.consume());gate.mouse(1,true);assertNotNull(gate.consume());for(int i=0;i<20;i++){gate.mouse(1,true);assertNull(gate.consume());}
 }
}
