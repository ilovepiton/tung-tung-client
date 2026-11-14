package dev.amethyst.finder.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CleanChatRulesTest {
 private static final String MESSAGE="⚠ Your client version (26.3) is NOT recommended due to interpolation changes that make movement choppy.";
 @Test void removesOnlyKnownServerSystemNotice() {
  assertTrue(CleanChatRules.hide("mcpvp.club",MESSAGE,false));
  assertTrue(CleanChatRules.hide("EU.MCPVP.CLUB:25565",MESSAGE,false));
  assertFalse(CleanChatRules.hide("mcpvp.club.evil.test",MESSAGE,false));
  assertFalse(CleanChatRules.hide("another.test",MESSAGE,false));
  assertFalse(CleanChatRules.hide("mcpvp.club",MESSAGE,true));
  assertFalse(CleanChatRules.hide("mcpvp.club","Player: "+MESSAGE,false));
  assertFalse(CleanChatRules.hide("mcpvp.club","Server restarting in 10 seconds",false));
 }
}
