package qa;
public class BetterMaceSmoke implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint {
 public void onPreLaunch(){try{
  String[][] probes={{"com.ritesh.feature.Combat","tung$realAxe"},{"com.ritesh.feature.Misc","tung$ownedShieldClick"},{"com.ritesh.feature.Coordinator","tung$pauseWithoutGameplayFocus"}};
  for(String[] p:probes){Class<?> c=Class.forName(p[0],false,Thread.currentThread().getContextClassLoader());boolean found=java.util.Arrays.stream(c.getDeclaredMethods()).anyMatch(m->m.getName().contains(p[1]));if(!found)throw new AssertionError("Missing runtime injection: "+p[0]);System.out.println("BMS_MIXIN_PASS "+p[0]);}
  System.out.println("BMS_MIXIN_SMOKE_PASSED");System.exit(0);
 }catch(Throwable t){t.printStackTrace();System.exit(9);}}
}