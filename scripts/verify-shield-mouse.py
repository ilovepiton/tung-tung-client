"""Execute the production ShieldBreaker against a small fake client, not a live server."""
from pathlib import Path
import subprocess,json
R=Path(__file__).resolve().parents[1];W=R/'build/shield-mouse-qa';S=W/'src';S.mkdir(parents=True,exist_ok=True)
def put(name,text):
 p=S/(name.replace('.','/')+'.java');p.parent.mkdir(parents=True,exist_ok=True);package=name.rsplit('.',1)[0];p.write_text('package '+package+';\n'+text)
put('com.mojang.blaze3d.platform.InputConstants','public class InputConstants {public enum Type {MOUSE;public int getOrCreate(int n){return n;}}}')
put('dev.amethyst.finder.FinderConfig','public class FinderConfig {public boolean on=true,streamerMode;public boolean isModuleEnabled(String id){return on;}}')
put('dev.amethyst.finder.integration.BetterMaceModules','''public class BetterMaceModules {
public static boolean nativeProvider=false,on=true;
public static boolean replaces(String id){return nativeProvider;}public static boolean enabled(String id){return on;}
public static void manualStun(net.minecraft.client.Minecraft c,net.minecraft.world.entity.player.Player p,int slot,Runnable attack){attack.run();net.minecraft.client.Minecraft.events.add("native-queued:"+slot);}
}''')
put('dev.amethyst.finder.Friends','public class Friends {public static boolean listed(FinderConfig c,net.minecraft.world.entity.player.Player p){return p.friend;}}')
put('dev.amethyst.finder.ChunkFinderClient','''public class ChunkFinderClient {
public static Gate MODULES=new Gate(),BOT=new Gate(),MACE=new Gate(),PVP=new Gate(),HARD_CRYSTAL=new Gate();
public static class Gate {public boolean cameraActive(){return false;}public boolean totemSwapPending(){return false;}public boolean enabled(){return false;}public boolean ownsInventory(){return false;}public boolean hasPendingExplosiveAssist(){return false;}public boolean busy(){return false;}}
}''')
put('net.minecraft.core.registries.BuiltInRegistries','public class BuiltInRegistries {public static final Registry ITEM=new Registry();public static class Registry {public String getKey(Object item){return "minecraft:"+item;}}}')
put('net.minecraft.tags.ItemTags','public class ItemTags {public static final String AXES="axes";}')
put('net.minecraft.world.item.Items','public class Items {public static final String MACE="mace",AXE="axe";}')
put('net.minecraft.world.item.ItemStack','''public class ItemStack {
public String item;public ItemStack(String s){item=s;}public boolean is(String id){return item.equals(id)||id.equals("axes")&&item.equals("axe");}public boolean isEmpty(){return item.equals("empty");}public boolean isDamageableItem(){return false;}public int getDamageValue(){return 0;}public int getMaxDamage(){return 100;}public Object getItem(){return item;}public ItemStack copy(){return new ItemStack(item);}public String getEnchantments(){return "same";}public Object getAttackAnimation(){return "swing";}
}''')
put('net.minecraft.world.InteractionHand','public enum InteractionHand {MAIN_HAND}')
put('net.minecraft.world.entity.player.Player','''public class Player {
public boolean blocking=true,friend;public boolean isAlive(){return true;}public boolean isSpectator(){return false;}public boolean isBlocking(){return blocking;}public Object getBoundingBox(){return this;}
}''')
put('net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket','public record ServerboundSetCarriedItemPacket(int slot){}')
put('net.minecraft.client.multiplayer.ClientPacketListener','''public class ClientPacketListener {public void send(net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket p){net.minecraft.client.Minecraft.events.add("restore-packet:"+p.slot());}}''')
put('net.minecraft.client.player.LocalPlayer','''public class LocalPlayer extends net.minecraft.world.entity.player.Player {
public boolean reachable=true;public net.minecraft.client.multiplayer.ClientPacketListener connection=new net.minecraft.client.multiplayer.ClientPacketListener();public Inv inv=new Inv();
public static class Inv {public int slot=2;public net.minecraft.world.item.ItemStack[] items=new net.minecraft.world.item.ItemStack[9];public Inv(){for(int i=0;i<9;i++)items[i]=new net.minecraft.world.item.ItemStack("empty");items[2]=new net.minecraft.world.item.ItemStack("mace");items[5]=new net.minecraft.world.item.ItemStack("axe");}public int getSelectedSlot(){return slot;}public void setSelectedSlot(int v){slot=v;net.minecraft.client.Minecraft.events.add("select:"+v);}public net.minecraft.world.item.ItemStack getItem(int v){return items[v];}}
public Inv getInventory(){return inv;}public net.minecraft.world.item.ItemStack getMainHandItem(){return inv.items[inv.slot];}public boolean hasLineOfSight(Object p){return true;}public boolean isWithinEntityInteractionRange(Object p,int n){return reachable;}public boolean isWithinAttackRange(Object i,Object b,int n){return reachable;}public boolean isUsingItem(){return false;}public boolean isHandsBusy(){return false;}public void swing(net.minecraft.world.InteractionHand h,Object a,boolean b){net.minecraft.client.Minecraft.events.add("swing:"+getMainHandItem().item);}public float getAttackStrengthScale(float n){return 0;}
}''')
put('net.minecraft.world.phys.EntityHitResult','public class EntityHitResult {private Object entity;public EntityHitResult(Object e){entity=e;}public Object getEntity(){return entity;}}')
put('net.minecraft.client.Minecraft','''public class Minecraft {
public static java.util.List<String> events=new java.util.ArrayList<>();public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();public Object level=new Object(),hitResult;public Options options=new Options();public Gui gui=new Gui();public Mouse mouseHandler=new Mouse();public Mode gameMode=new Mode();
public static class Key {int button;Key(int n){button=n;}public boolean matches(int n){return button==n;}}
public static class Options {public Key keyUse=new Key(1),keyAttack=new Key(0);}
public static class Gui {public Object screen(){return null;}public Object overlay(){return null;}}
public static class Mouse {public boolean isMouseGrabbed(){return true;}}
public static class Mode {public boolean fail;public void attack(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.entity.player.Player target){if(fail)throw new IllegalStateException();events.add("attack:"+p.getMainHandItem().item);}}
public boolean isPaused(){return false;}public boolean isWindowActive(){return true;}public net.minecraft.client.multiplayer.ClientPacketListener getConnection(){return player.connection;}
}''')
put('qa.ShieldMouseChecks','''import dev.amethyst.finder.FinderConfig;import dev.amethyst.finder.modules.pvp.ShieldBreaker;import net.minecraft.client.Minecraft;import net.minecraft.world.entity.player.Player;import net.minecraft.world.phys.EntityHitResult;
public class ShieldMouseChecks {
static int checks;static void check(boolean b,String name){if(!b)throw new AssertionError(name+" "+Minecraft.events);checks++;}
static Minecraft setup(){Minecraft.events.clear();Minecraft c=new Minecraft();c.hitResult=new EntityHitResult(new Player());return c;}
public static void main(String[] args){
var c=setup();var s=new ShieldBreaker(new FinderConfig());s.mouse(c,1,1);s.use(c);
check(Minecraft.events.equals(java.util.List.of("select:5","attack:axe","swing:axe","select:2","restore-packet:2")),"falling/cooldown-zero click must complete before any tick");
check(c.player.inv.slot==2,"mace restored immediately");s.tick(c);s.mouse(c,1,1);s.use(c);check(Minecraft.events.stream().filter(e->e.startsWith("attack:")).count()==1,"hold never repeats");
s.mouse(c,1,0);s.mouse(c,1,1);s.use(c);check(Minecraft.events.stream().filter(e->e.equals("attack:axe")).count()==2,"new click may attack once");
c=setup();s=new ShieldBreaker(new FinderConfig());((Player)((EntityHitResult)c.hitResult).getEntity()).blocking=false;s.mouse(c,1,1);s.use(c);check(Minecraft.events.isEmpty(),"unshielded player never causes swap");
c=setup();s=new ShieldBreaker(new FinderConfig());s.mouse(c,0,1);check(Minecraft.events.isEmpty(),"left click stays manual");check(!s.status().contains("Native attack trigger"),"left click does not overwrite the last use diagnostic");
c=setup();s=new ShieldBreaker(new FinderConfig());c.player.reachable=false;s.mouse(c,1,1);s.use(c);check(Minecraft.events.isEmpty(),"no reach never swaps");
c=setup();s=new ShieldBreaker(new FinderConfig());c.player.inv.items[5]=new net.minecraft.world.item.ItemStack("empty");s.mouse(c,1,1);s.use(c);check(Minecraft.events.isEmpty()&&s.status().contains("hotbar"),"missing axe diagnostic");
c=setup();var disabled=new FinderConfig();disabled.on=false;s=new ShieldBreaker(disabled);s.mouse(c,1,1);s.use(c);check(Minecraft.events.isEmpty()&&s.status().contains("Disabled"),"disabled module never acts");
c=setup();s=new ShieldBreaker(new FinderConfig());c.gameMode.fail=true;s.mouse(c,1,1);s.use(c);check(c.player.inv.slot==2&&!s.busy(),"attack exception still restores mace and releases ownership");
check(Minecraft.events.stream().noneMatch(e->e.equals("attack:mace")),"no automatic mace follow-up");
dev.amethyst.finder.integration.BetterMaceModules.nativeProvider=true;
c=setup();var nativeConfig=new FinderConfig();nativeConfig.on=false;s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);s.use(c);
check(Minecraft.events.equals(java.util.List.of("select:5","attack:axe","swing:axe","native-queued:2","select:2","restore-packet:2")),"native provider enabled must dispatch RMB even when legacy flag is false");
s.mouse(c,1,1);s.use(c);check(Minecraft.events.stream().filter(e->e.startsWith("native-queued:")).count()==1,"native held RMB cannot queue a second combo");
s.mouse(c,1,0);s.mouse(c,1,1);s.use(c);check(Minecraft.events.stream().filter(e->e.startsWith("native-queued:")).count()==2,"native new RMB starts one new combo");
c=setup();c.player.inv.items[0]=new net.minecraft.world.item.ItemStack("pickaxe");s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);s.use(c);check(Minecraft.events.contains("select:5")&&!Minecraft.events.contains("select:0"),"pickaxe cannot be chosen instead of an axe");
c=setup();((Player)((EntityHitResult)c.hitResult).getEntity()).blocking=false;s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);s.use(c);check(Minecraft.events.isEmpty(),"native unshielded target does not trigger on RMB");
c=setup();dev.amethyst.finder.integration.BetterMaceModules.on=false;s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);s.use(c);check(Minecraft.events.isEmpty(),"native disabled toggle blocks RMB regardless of provider presence");
dev.amethyst.finder.integration.BetterMaceModules.on=true;
c=setup();c.player.inv.slot=0;c.player.inv.items[0]=new net.minecraft.world.item.ItemStack("wind_charge");s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);
check(Minecraft.events.isEmpty(),"raw press must not inspect or act on old hotbar selection");
c.player.inv.slot=2;s.use(c);check(Minecraft.events.contains("native-queued:2"),"wind-charge to mace selection in the same tick is resolved at vanilla use");
c=setup();s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);s.mouse(c,1,0);check(s.use(c),"short click released before vanilla use must still dispatch");check(!s.interceptUse(),"released short click must not latch use ownership");check(!s.use(c),"short click cannot dispatch a second time");
c=setup();s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);c.player.inv.slot=0;c.player.inv.items[0]=new net.minecraft.world.item.ItemStack("wind_charge");check(!s.use(c)&&Minecraft.events.isEmpty(),"mace to wind-charge selection before use stays a normal wind-charge use");check(s.status().contains("minecraft:wind_charge"),"diagnostic reports actual vanilla-use item");
c=setup();s=new ShieldBreaker(nativeConfig);s.mouse(c,1,1);s.mouse(c,1,0);s.mouse(c,1,1);check(s.use(c)&&s.use(c),"two genuine presses queued in one frame each dispatch once");check(Minecraft.events.stream().filter(e->e.startsWith("native-queued:")).count()==2,"queued press must not be hidden by held-use ownership");s.use(c);check(Minecraft.events.stream().filter(e->e.startsWith("native-queued:")).count()==2,"held repeat cannot manufacture a third press");
System.out.println("PASS: "+checks+" production mouse-handler checks on fake client; no live server claim");
}}
''')
java=Path.home()/'Library/Application Support/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home/bin'
files=[str(p) for p in S.rglob('*.java')]+[str(R/'src/main/java/dev/amethyst/finder/core/ClickActionGate.java'),str(R/'src/main/java/dev/amethyst/finder/modules/pvp/ShieldBreaker.java')]
subprocess.run([str(java/'javac'),'-d',str(W/'classes'),*files],check=True)
subprocess.run([str(java/'java'),'-ea','-cp',str(W/'classes'),'qa.ShieldMouseChecks'],check=True)
out=R/'artifacts/shield-use-event-1.36.4';out.mkdir(exist_ok=True);(out/'mouse-checks.json').write_text(json.dumps({'passed':True,'checks':28,'productionHandler':True,'fakeClient':True,'liveServerTested':False},indent=2)+'\n')
