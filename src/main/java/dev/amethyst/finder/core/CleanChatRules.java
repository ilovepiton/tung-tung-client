package dev.amethyst.finder.core;
import java.util.Locale;
/** Narrow cosmetic suppression of the known MCPVP interpolation notice; player chat is untouched. */
public final class CleanChatRules {
 private CleanChatRules(){}
 public static boolean hide(String server,String message,boolean overlay) {
  if(server==null||message==null||overlay)return false;
  String host=server.strip().toLowerCase(Locale.ROOT);
  if(host.endsWith(":25565"))host=host.substring(0,host.length()-6);
  if(!host.equals("mcpvp.club")&&!host.endsWith(".mcpvp.club"))return false;
  return message.strip().matches("⚠?\\s*Your client version \\([0-9.]+\\) is NOT recommended due to\\s+interpolation changes that make movement choppy\\.");
 }
}
