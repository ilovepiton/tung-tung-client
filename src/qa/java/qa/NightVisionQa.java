package qa;

import com.google.gson.GsonBuilder;
import dev.amethyst.finder.*;
import dev.amethyst.finder.modules.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual offline client, transformed production mixins and a disposable sealed cave. */
public final class NightVisionQa implements ClientModInitializer {
    private static final Path OUTPUT = Path.of(System.getProperty("tung.qa.output"));
    private final List<Map<String,Object>> checks = new ArrayList<>();
    private final Map<String,Double> luminance = new ConcurrentHashMap<>();
    private volatile boolean fixtureReady;
    private volatile String error = "";
    private int ticks, since, stage;
    private boolean finished;
    private LightmapRenderStateExtractor probe;
    private final LightmapRenderState state = new LightmapRenderState();
    private static final class Still extends ClientInput {
        @Override public void tick() { keyPresses = Input.EMPTY; }
    }

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        ticks++;
        if (finished) return;
        try {
            if (!error.isEmpty()) throw new AssertionError(error);
            if (ticks - since > 700) throw new AssertionError("Timeout at stage " + stage);
            if (client.player != null) {
                client.player.input = new Still();
                client.player.setYRot(0); client.player.setXRot(0);
                for (var key : client.options.keyMappings) { key.setDown(false); while (key.consumeClick()) {} }
                client.gui.setScreen(null);
            }
            switch (stage) {
                case 0 -> {
                    if (!client.isGameLoadFinished()) return;
                    var config = ChunkFinderClient.CONFIG;
                    config.notifications = false;
                    config.modules.replaceAll((id, enabled) -> false);
                    config.enabled = false; config.save();
                    client.options.pauseOnLostFocus = false;
                    client.options.gamma().set(0.0); client.options.renderDistance().set(2);
                    client.getTutorial().setStep(TutorialSteps.NONE);
                    check("Night Vision is available in the game catalog", ModuleCatalog.available("night_vision"));
                    client.createWorldOpenFlows().createFreshLevel("NightVisionQA",
                        new LevelSettings("NightVisionQA", GameType.CREATIVE,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(93,false,false),
                        lookup -> lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        client.gui.screen());
                    next();
                }
                case 1 -> {
                    if (client.player == null || client.getSingleplayerServer() == null) return;
                    client.getSingleplayerServer().execute(() -> {
                        try {
                            var server = client.getSingleplayerServer(); var world = server.overworld();
                            var player = server.getPlayerList().getPlayers().getFirst();
                            world.getGameRules().set(GameRules.SPAWN_MOBS,false,server);
                            for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++) for (int y=63;y<=70;y++) {
                                boolean shell = x==-6 || x==6 || z==-6 || z==6 || y==63 || y==70;
                                world.setBlock(new BlockPos(x,y,z), (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(),3,512);
                            }
                            for (int x=-3;x<=3;x++) for (int y=64;y<=67;y++)
                                world.setBlock(new BlockPos(x,y,5), (x<0?Blocks.OBSIDIAN:x>0?Blocks.DIAMOND_ORE:Blocks.STONE).defaultBlockState(),3,512);
                            player.teleportTo(world,.5,64,-2.5,Set.of(),0,0,true);
                        } catch (Throwable failure) { error = failure.toString(); }
                        fixtureReady = true;
                    });
                    next();
                }
                case 2 -> {
                    if (!fixtureReady || ticks-since<90 || client.player.getY()<63) return;
                    check("Sealed cave has zero received block and sky light",
                        client.level.getBrightness(LightLayer.BLOCK,client.player.blockPosition())==0
                        && client.level.getBrightness(LightLayer.SKY,client.player.blockPosition())==0);
                    check("Night Vision starts off", !NightVisionModule.active());
                    probe = new LightmapRenderStateExtractor(client.gameRenderer,client);
                    probe.tick(); probe.extract(state,1);
                    check("Baseline uses vanilla lightmap", state.nightVisionEffectIntensity==0 && state.brightness==0);
                    shot(client,"01-cave-off"); next();
                }
                case 3 -> {
                    if (ticks-since<15 || !luminance.containsKey("01-cave-off")) return;
                    ModuleControls.setEnabled(client,"night_vision",true);
                    check("Normal module controls enable Night Vision", NightVisionModule.active());
                    probe.extract(state,1);
                    check("Production mixin brightens lightmap between vanilla ticks", state.needsUpdate && state.brightness==1 && state.nightVisionEffectIntensity==1);
                    check("Enabling does not add a potion effect", !client.player.hasEffect(MobEffects.NIGHT_VISION));
                    check("Gamma preference is preserved", client.options.gamma().get()==0.0);
                    check("Module selection persists", FinderConfig.load().isModuleEnabled("night_vision"));
                    next();
                }
                case 4 -> {
                    if (ticks-since<25) return;
                    shot(client,"02-cave-on"); next();
                }
                case 5 -> {
                    if (ticks-since<15 || !luminance.containsKey("02-cave-on")) return;
                    check("Actual GPU cave pixels become substantially brighter", luminance.get("02-cave-on") > luminance.get("01-cave-off")+30);
                    ModuleControls.setEnabled(client,"night_vision",false);
                    probe.extract(state,1);
                    check("Disable restores vanilla immediately without waiting for a tick", state.needsUpdate && state.brightness==0 && state.nightVisionEffectIntensity==0);
                    next();
                }
                case 6 -> {
                    if (ticks-since<25) return;
                    shot(client,"03-cave-restored"); next();
                }
                case 7 -> {
                    if (ticks-since<15 || !luminance.containsKey("03-cave-restored")) return;
                    check("Actual GPU brightness returns to baseline", Math.abs(luminance.get("03-cave-restored")-luminance.get("01-cave-off"))<5);
                    client.player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,600));
                    client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,600));
                    ModuleControls.setEnabled(client,"night_vision",true); next();
                }
                case 8 -> {
                    if (ticks-since<35) return;
                    probe.tick(); probe.extract(state,1);
                    check("Darkness dimming is suppressed", state.darknessEffectScale==0 && state.brightness==1);
                    ModuleControls.setEnabled(client,"night_vision",false); probe.extract(state,1);
                    check("Real Night Vision and Darkness effects survive both toggles", client.player.hasEffect(MobEffects.NIGHT_VISION) && client.player.hasEffect(MobEffects.DARKNESS));
                    check("Vanilla potion Night Vision resumes on disable", state.nightVisionEffectIntensity>0);
                    client.player.removeEffect(MobEffects.DARKNESS); client.player.removeEffect(MobEffects.NIGHT_VISION);
                    ModuleControls.setEnabled(client,"xray",true); XrayModule.tick(client);
                    probe.tick(); probe.extract(state,1);
                    check("X-ray retains its full-bright lighting", state.brightness==1);
                    ModuleControls.setEnabled(client,"night_vision",true);
                    ModuleControls.setEnabled(client,"xray",false); XrayModule.tick(client);
                    probe.extract(state,1);
                    check("Turning X-ray off keeps independent Night Vision on", state.brightness==1 && NightVisionModule.active());
                    ModuleControls.setEnabled(client,"night_vision",false); probe.extract(state,1);
                    check("Turning both modules off restores vanilla", state.brightness==0 && state.nightVisionEffectIntensity==0);
                    ModuleControls.setEnabled(client,"night_vision",true);
                    client.disconnectWithSavingScreen(); next();
                }
                case 9 -> {
                    check("Disconnect cannot leave Night Vision active", client.level==null && !NightVisionModule.active());
                    finished=true; stage=10; report(); client.stop();
                }
            }
        } catch (Throwable failure) {
            error=failure.toString(); failure.printStackTrace(); finished=true; report(); client.stop();
        }
    }

    private void shot(Minecraft client,String name) {
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
            try {
                image.writeToFile(OUTPUT.resolve(name+".png"));
                double total=0; int count=0;
                for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y++)
                    for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x++) {
                        int pixel=image.getPixel(x,y);
                        total+=((pixel>>16)&255)*.2126+((pixel>>8)&255)*.7152+(pixel&255)*.0722; count++;
                    }
                luminance.put(name,total/count);
            } catch(Exception failure) { error=failure.toString(); }
            finally { image.close(); }
        });
    }
    private void next() { stage++; since=ticks; report(); }
    private void check(String label,boolean passed) {
        checks.add(Map.of("check",label,"passed",passed)); report();
        if(!passed)throw new AssertionError(label);
    }
    private void report() {
        try { Files.writeString(OUTPUT.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(
            Map.of("stage",stage,"error",error,"checks",checks,"luminance",luminance,"userProfileTouched",false))); }
        catch(Exception failure) { throw new RuntimeException(failure); }
    }
}
