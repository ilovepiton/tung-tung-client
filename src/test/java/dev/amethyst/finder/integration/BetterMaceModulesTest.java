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
