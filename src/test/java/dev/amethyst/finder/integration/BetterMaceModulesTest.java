package dev.amethyst.finder.integration;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BetterMaceModulesTest {
 private Object config() throws Exception {return Class.forName("com.ritesh.config.ModConfig").getConstructor().newInstance();}
 @Test void pinnedOriginalSchemaMatchesEveryDesktopSetting() throws Exception {
  Object cfg=config();BetterMaceModules.validateSchema(cfg);
  assertEquals(19,BetterMaceModules.FEATURES.size());
  for(var feature:BetterMaceModules.FEATURES)for(String key:feature.settings())assertTrue(BetterMaceModules.OPTIONS.containsKey(key),key);
 }
 @Test void originalAutomaticAimIsNotReplaced(){
  assertFalse(BetterMaceModules.handles("aim_cursor"));assertFalse(BetterMaceModules.replaces("aim_cursor"));
  assertNotNull(BetterMaceModules.find("bms_aim_assist"));
 }
 @Test void nativeStunQueueAndRecursionGuardExistInPinnedJar() throws Exception {
  Class<?> slam=Class.forName("com.ritesh.feature.StunSlam",false,getClass().getClassLoader());
  assertNotNull(slam.getMethod("queueMaceSlam",net.minecraft.world.entity.Entity.class,int.class,int.class,int.class));
  assertNotNull(slam.getMethod("setInSlamCombo",boolean.class));
 }
 @Test void writesRealNativeFieldsAndEnums() throws Exception {
  Object cfg=config();
  BetterMaceModules.applySetting(cfg,"breachSwapBackTicks",new JsonPrimitive(5));
  assertEquals(5,cfg.getClass().getField("breachSwapBackTicks").getInt(cfg));
  BetterMaceModules.applySetting(cfg,"aimMode",new JsonPrimitive("ALWAYS"));
  assertEquals("ALWAYS",((Enum<?>)cfg.getClass().getField("aimMode").get(cfg)).name());
  BetterMaceModules.applySetting(cfg,"combatTargetMobs",new JsonPrimitive(false));
  assertFalse(cfg.getClass().getField("combatTargetMobs").getBoolean(cfg));
 }
 @Test void rejectsUnknownInvalidAndWrongTypeWithoutMutation() throws Exception {
  Object cfg=config();int previous=cfg.getClass().getField("breachSwapBackTicks").getInt(cfg);
  for(var value:new JsonPrimitive[]{new JsonPrimitive(0),new JsonPrimitive(6),new JsonPrimitive(2.5),new JsonPrimitive("3"),new JsonPrimitive(true)})assertThrows(IllegalArgumentException.class,()->BetterMaceModules.applySetting(cfg,"breachSwapBackTicks",value));
  assertThrows(IllegalArgumentException.class,()->BetterMaceModules.applySetting(cfg,"class",new JsonPrimitive("x")));
  assertThrows(IllegalArgumentException.class,()->BetterMaceModules.applySetting(cfg,"aimMode",new JsonPrimitive("INVALID")));
  assertEquals(previous,cfg.getClass().getField("breachSwapBackTicks").getInt(cfg));
 }
}
