package dev.amethyst.finder;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.ui.WorldOverlay;
import dev.amethyst.finder.ui.ChunkAnalysisScreen;
import dev.amethyst.finder.ui.PlayerObservationTracker;
import dev.amethyst.finder.ui.DiscoveryScreen;
import dev.amethyst.finder.modules.ClientModules;
import dev.amethyst.finder.modules.PvpModules;
import dev.amethyst.finder.modules.MovementModules;
import dev.amethyst.finder.modules.XrayModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChunkFinderClient implements ClientModInitializer {
    public static final String ID = "amethyst_chunk_finder";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static FinderConfig CONFIG;
    public static ChunkScanner SCANNER;
    public static CaveDetector CAVES;
    public static ClientModules MODULES;
    public static PvpModules PVP;
    public static dev.amethyst.finder.modules.pvp.ArsenalBot BOT;
    public static MovementModules MOVEMENT;
    public static DesktopBridge BRIDGE;
    public static dev.amethyst.finder.modules.AutoRelog RELOG;
    public static PlayerLocator PLAYERS;
    public static ElytraFinder ELYTRA;
    public static ItemFinder ITEMS;
    public static ResourceScanner RESOURCES;
    public static VaultAssistant VAULTS;
    public static dev.amethyst.finder.modules.pvp.MaceBot MACE;
    public static KeyMapping TOGGLE, ANALYSIS, DISCOVERY;

    @Override public void onInitializeClient() {
        CONFIG = FinderConfig.load();
        MediaPack.prepare();
        SCANNER = new ChunkScanner(CONFIG);
        CAVES = new CaveDetector(CONFIG);
        MODULES = new ClientModules(CONFIG);
        RELOG = new dev.amethyst.finder.modules.AutoRelog(CONFIG);
        PVP = new PvpModules(CONFIG);
        BOT = new dev.amethyst.finder.modules.pvp.ArsenalBot(CONFIG);
        MOVEMENT = new MovementModules(CONFIG);
        PLAYERS = new PlayerLocator();
        ELYTRA = new ElytraFinder(CONFIG);
        ITEMS = new ItemFinder(CONFIG);
        RESOURCES = new ResourceScanner(CONFIG);
        VAULTS = new VaultAssistant(CONFIG);
        MACE = new dev.amethyst.finder.modules.pvp.MaceBot(CONFIG);
        DiscoveryCommands.register();
        try { BRIDGE=new DesktopBridge(); }
        catch(java.io.IOException ex) { LOGGER.error("Cannot start Tung Tung Client desktop connection",ex); }
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "finder"));
        TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.amethyst_chunk_finder.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));
        ANALYSIS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.amethyst_chunk_finder.analysis", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
        DISCOVERY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.amethyst_chunk_finder.discovery", InputConstants.Type.KEYBOARD, InputConstants.KEY_O, category));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MOVEMENT.beforeWorldExit(client);
            PVP.reset();
            MODULES.reset(client);
            PlayerObservationTracker.reset();
            SCANNER.close();
        });
        ClientChunkEvents.CHUNK_LOAD.register((level,chunk) -> {
            SCANNER.setScope(HistoryContext.resolve(net.minecraft.client.Minecraft.getInstance(),level,CONFIG));
            SCANNER.onLoad(level,chunk);
            RESOURCES.invalidateChunk(chunk);
            CAVES.invalidate(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{
            SCANNER.onUnload(level,chunk);
            RESOURCES.invalidateChunk(chunk);
            CAVES.invalidate(chunk);
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            MapArtModules.apply();
            RELOG.tick(client);
            WhiteGlint.tick(client);
            BOT.beginClientTick();MACE.beginClientTick();
            MODULES.earlyInventoryTick(client);
            if(!client.getWindow().isFocused())ModuleKeybinds.clearHeld();
            CursorSafety.tick(client);
            ChatStreamer.tick(client);
            StreamerMode.tick(client);
            while(DISCOVERY.consumeClick()) if(client.player!=null && client.gui.screen()==null)
                client.gui.setScreen(new DiscoveryScreen(false));
            while(ANALYSIS.consumeClick()) if(client.player!=null && client.gui.screen()==null)
                client.gui.setScreen(new ChunkAnalysisScreen());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) if (client.player != null && client.gui.screen() == null) {
                CONFIG.enabled = !CONFIG.enabled; CONFIG.save();
                SCANNER.requestRescan();
            }
            if(client.level!=null)SCANNER.setScope(HistoryContext.resolve(client,client.level,CONFIG));
            else HistoryContext.invalidate();
            SCANNER.tick(client);
            CAVES.tick(client);
            boolean botHandled=BOT.handledThisTick()||MACE.handledThisTick();
            if(!botHandled){if(MACE.enabled())MACE.tick(client);else {BOT.tick(client);MODULES.tick(client);}}
            MOVEMENT.tick(client);
            if(!botHandled)PVP.tick(client);
            XrayModule.tick(client);
            PlayerObservationTracker.tick(client);
            RESOURCES.tick(client);
            ELYTRA.tick(client);
            ITEMS.tick(client);
            VAULTS.tick(client);
            if(BRIDGE!=null)BRIDGE.publish(client);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(ID, "world_esp"), WorldOverlay::extract);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "elytra_finder"), dev.amethyst.finder.ui.ElytraHud::extract);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "streamer_watermark"), dev.amethyst.finder.ui.StreamerHud::extract);
        LOGGER.info("Tung Tung Client game layer ready · Minecraft 26.3 · desktop opened normally / J chunk analysis / K finder");
    }
}
