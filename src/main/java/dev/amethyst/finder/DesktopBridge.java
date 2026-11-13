package dev.amethyst.finder;

import com.google.gson.*;
import dev.amethyst.finder.ui.Theme;
import dev.amethyst.finder.core.FinderTargets;
import dev.amethyst.finder.core.Signal;
import dev.amethyst.finder.core.MineralType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** A token-authenticated loopback bridge; the separate desktop window never reads game process memory. */
public final class DesktopBridge {
    private static final Gson JSON = new Gson();
    private final LoopbackServer server;
    private final Path descriptor;
    private final String token;
    private volatile String state = "{\"connected\":false}";
    private String message = "";
    private long openClientSequence;
    private Object lastWorld, lastPlayer;
    private long worldSession;
    private final TexturePacks texturePacks = new TexturePacks();

    public DesktopBridge() throws IOException {
        byte[] random=new byte[32]; new SecureRandom().nextBytes(random); token=HexFormat.of().formatHex(random);
        descriptor=FabricLoader.getInstance().getConfigDir().resolve("tun-tun-connection.json");
        server=new LoopbackServer(this::request);
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor,JSON.toJson(Map.of("port",server.port(),"token",token)),StandardCharsets.UTF_8);
        Runtime.getRuntime().addShutdownHook(new Thread(this::close,"Tung Tung Client bridge shutdown"));
    }
    public void openClient() { openClientSequence++; }
    private void refreshWorldSession(Minecraft client) {
        if (lastWorld != client.level || lastPlayer != client.player) {
            lastWorld = client.level;
            lastPlayer = client.player;
            worldSession++;
        }
    }
    public void publish(Minecraft client) {
        FinderConfig c=ChunkFinderClient.CONFIG;
        Map<String,Object> snapshot=new LinkedHashMap<>();
        boolean connected=client.level!=null&&client.player!=null;
        refreshWorldSession(client);
        snapshot.put("connected",connected);
        snapshot.put("stream",ChatStreamer.state());
        snapshot.put("streamerMode",StreamerMode.state());
        snapshot.put("vault",ChunkFinderClient.VAULTS.state(client));
        snapshot.put("combatBot",ChunkFinderClient.BOT.state());
        snapshot.put("elytraFinder",ChunkFinderClient.ELYTRA.state());
        snapshot.put("itemFinder",ChunkFinderClient.ITEMS.state(client));
        snapshot.put("lootProtection",dev.amethyst.finder.modules.pvp.LootProtection.status(client));
        snapshot.put("maceBot",ChunkFinderClient.MACE.state());
        snapshot.put("autoTotem",ChunkFinderClient.MODULES.totemDiagnostics(client));
        snapshot.put("friends",Friends.state(c,ChunkFinderClient.PLAYERS.rows(client)));
        snapshot.put("gameVersion", FabricLoader.getInstance().getModContainer(ChunkFinderClient.ID).orElseThrow().getMetadata().getVersion().getFriendlyString());
        snapshot.put("protocolCompatibility", Map.of(
                "installed", FabricLoader.getInstance().isModLoaded("viafabricplus"),
                "mode", "Per-server protocol selection",
                "client", "Minecraft 26.3",
                "note", "Version translation changes the login protocol; each server still decides whether the connection and gameplay actions are accepted."));
        snapshot.put("gameProcessId", ProcessHandle.current().pid());
        snapshot.put("worldSession", worldSession);
        snapshot.put("gameScreenOpen", client.gui.screen() != null);
        snapshot.put("gameScreenType", client.gui.screen() == null ? "" : client.gui.screen().getClass().getSimpleName());
        snapshot.put("mouseGrabbed", client.mouseHandler.isMouseGrabbed());
        snapshot.put("server",client.getCurrentServer()==null?(connected?"Singleplayer":"No connection"):client.getCurrentServer().ip);
        snapshot.put("scanning",c.requiresScan()); snapshot.put("hotkeyAllowed",connected&&client.gui.screen()==null);
        snapshot.put("openClientSequence",openClientSequence); snapshot.put("message",message);
        snapshot.put("player",connected?Map.of("x",client.player.getX(),"y",client.player.getY(),"z",client.player.getZ()):Map.of("x",0,"y",0,"z",0));
        List<Map<String,Object>> modules=new ArrayList<>();
        for(ModuleCatalog.Entry e:ModuleCatalog.ENTRIES) {
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("id",e.id());m.put("name",e.name());m.put("category",e.category());
            m.put("enabled",c.isModuleEnabled(e.id()));m.put("available",ModuleCatalog.available(e.id()));
            String live=dev.amethyst.finder.modules.MovementModules.capability(e.id())?ChunkFinderClient.MOVEMENT.status(e.id())
                    :dev.amethyst.finder.modules.PvpModules.capability(e.id())?ChunkFinderClient.PVP.status(e.id())
                    :dev.amethyst.finder.modules.ClientModules.capability(e.id())?ChunkFinderClient.MODULES.status(e.id()):"";
            if (e.id().equals("auto_relog")) live=ChunkFinderClient.RELOG.status();
            if (e.id().equals("search_chunk")) live="Red mature-cluster candidates · minimum "+c.searchChunkMinClusters+" · received chunks only";
            if (e.id().equals("xray")) live=dev.amethyst.finder.modules.XrayModule.status();
            if (e.id().equals("night_vision")) live=dev.amethyst.finder.modules.NightVisionModule.status();
            if (e.id().equals("invis_mode")) live=dev.amethyst.finder.modules.InvisMode.status();
            if (e.id().equals("item_finder")) live=ChunkFinderClient.ITEMS.status(client);
            if (e.id().equals("elytra_finder")) live=ChunkFinderClient.ELYTRA.status();
            if (e.id().equals("auto_vault")) live=ChunkFinderClient.VAULTS.status();
            if (e.id().equals("client_maps")||e.id().equals("map_in_slot")) live=MapArtModules.status(e.id());
            m.put("note",e.note()+(live==null||live.isEmpty()?"":" · "+live));
            m.put("runtimeStatus",live==null?"":live);
            int bind=ModuleKeybinds.key(e.id());m.put("keybind",bind);m.put("keybindLabel",ModuleKeybinds.label(bind));m.put("keybindConflict",ModuleKeybinds.conflict(client,bind));
            if (dev.amethyst.finder.modules.MovementModules.capability(e.id()))
                m.put("runtimeState",ChunkFinderClient.MOVEMENT.runtimeState(e.id()));
            m.put("donutStatus",ModuleCatalog.detectionStatus(e.id()));modules.add(m);
        }
        snapshot.put("modules",modules);
        Map<String,Object> settings=new LinkedHashMap<>();
        settings.put("whiteGlintAllGear",c.whiteGlintAllGear);
        settings.put("streamerMode",c.streamerMode);settings.put("streamerPieChart",c.streamerPieChart);settings.put("streamerWatermark",c.streamerWatermark);settings.put("moduleSounds",c.moduleSounds);
        settings.put("elytraRange",c.elytraRange);settings.put("elytraPlayerRange",c.elytraPlayerRange);settings.put("elytraWarnPlayers",c.elytraWarnPlayers);
        settings.put("itemFinderRange",c.itemFinderRange);settings.put("itemFinderLimit",c.itemFinderLimit);
        settings.put("itemFinderTargets",ValuableItems.sanitize(c.itemFinderTargets));settings.put("itemFinderChunks",c.itemFinderChunks);
        settings.put("pvpLootProtection",c.pvpLootProtection);settings.put("pvpLootProtectionRange",c.pvpLootProtectionRange);
        settings.put("maceRange",c.maceRange);settings.put("maceChargeCooldown",c.maceChargeCooldown);settings.put("maceChargeReserve",c.maceChargeReserve);settings.put("maceDurabilityReserve",c.maceDurabilityReserve);settings.put("maceRetreatHealth",c.maceRetreatHealth);settings.put("maceMovement",c.maceMovement);settings.put("maceWindCharges",c.maceWindCharges);settings.put("maceShield",c.maceShield);settings.put("macePearls",c.macePearls);settings.put("maceStrafe",c.maceStrafe);
        settings.put("sensitivity",c.sensitivity);settings.put("radiusChunks",c.radiusChunks);
        settings.put("finderTargets",FinderTargets.sanitize(c.finderTargets));
        settings.put("espRange",c.espRange);settings.put("espTracers",c.espTracers);
        settings.put("resourceRange",c.resourceRange);settings.put("resourceLimit",c.resourceLimit);
        settings.put("resourceTargets",c.activeMinerals());
        InputSoundSettings.publish(settings,c);
        snapshot.put("voiceAudio",FabricLoader.getInstance().isModLoaded("voicechat")?dev.amethyst.finder.audio.VoiceInputPlugin.state():Map.of("available",false));
        settings.put("resourceExposedOnly",c.resourceExposedOnly);settings.put("asmrEnabled",c.asmrEnabled);settings.put("asmrProfile",c.asmrProfile);settings.put("asmrVolume",c.asmrVolume);settings.put("asmrKeyProfiles",c.asmrKeyProfiles);
        settings.put("xrayExposedOnly",c.xrayExposedOnly);
        settings.put("playerIndicatorColor",c.playerIndicatorColor);
        settings.put("playerVisualRange",c.playerVisualRange);
        settings.put("playerInfoScale",c.playerInfoScale);
        settings.put("aimCursorRange",c.aimCursorRange);settings.put("streamTarget",c.streamTarget);settings.put("streamTemplates",c.streamTemplates);settings.put("streamInterval",c.streamInterval);
        settings.put("freecamSpeed",c.freecamSpeed());
        settings.put("searchChunkMinClusters",c.searchChunkMinClusters);settings.put("autoRelogY",c.autoRelogY);settings.put("autoRelogDelaySeconds",c.autoRelogDelaySeconds);settings.put("espLimit",c.espLimit);
        settings.put("autoSellItems",c.autoSellItems);settings.put("autoSellCommand",c.autoSellCommand);settings.put("autoSellMenuTitle",c.autoSellMenuTitle);
        settings.put("autoSellIntervalSeconds",c.autoSellIntervalSeconds);settings.put("autoLogoutRange",c.autoLogoutRange);
        settings.put("pvpRange",c.pvpRange);settings.put("pvpDelayTicks",c.pvpDelayTicks);
        settings.put("autoTotemHotbarSlot",c.autoTotemHotbarSlot);
        settings.put("autoTotemReactionDelayMs",c.autoTotemReactionDelayMs);
        settings.put("autoTotemCooldownMs",c.autoTotemCooldownMs);
        settings.put("pvpMaxSelfDamage",c.pvpMaxSelfDamage);settings.put("pvpMinTargetDamage",c.pvpMinTargetDamage);
        settings.put("pvpTotemHealth",c.pvpTotemHealth);settings.put("pvpTargetPlayers",c.pvpTargetPlayers);
        settings.put("pvpTargetHostiles",c.pvpTargetHostiles);settings.put("pvpFriends",c.pvpFriends);
        settings.put("pvpFastAssist",c.pvpFastAssist);settings.put("pvpCrystalRepeat",c.pvpCrystalRepeat);settings.put("pvpFastAttack",c.pvpFastAttack);settings.put("pvpManualBlastChecks",c.pvpManualBlastChecks);
        settings.put("pvpManualAssist",c.pvpManualAssist);settings.put("pvpAnchorCover",c.pvpAnchorCover);
        settings.put("botMinDamage",c.botMinDamage);settings.put("botMaxSelfDamage",c.botMaxSelfDamage);settings.put("botRange",c.botRange);settings.put("botMovement",c.botMovement);settings.put("botStrafe",c.botStrafe);settings.put("botPearls",c.botPearls);settings.put("botBuild",c.botBuild);settings.put("botCrystals",c.botCrystals);settings.put("botAnchors",c.botAnchors);settings.put("botExtinguish",c.botExtinguish);
        snapshot.put("settings",settings);
        snapshot.put("texturePacks",texturePacks.state(client));
        snapshot.put("performance",SmoothProfile.state(client));
        snapshot.put("media",MediaPack.state());
        snapshot.put("players",ChunkFinderClient.PLAYERS.rows(client));
        snapshot.put("playerWaypoint",ChunkFinderClient.PLAYERS.saved());
        snapshot.put("playerWaypointActive",ChunkFinderClient.PLAYERS.active(client)!=null);
        snapshot.put("resourceStatus",ChunkFinderClient.RESOURCES.status());
        snapshot.put("visualStatus",dev.amethyst.finder.ui.WorldOverlay.visualStatus());
        List<Map<String,Object>> findings=new ArrayList<>();
        for(var f:ChunkFinderClient.SCANNER.findings().stream().limit(256).toList()) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("x",f.x());row.put("y",f.y());row.put("z",f.z());row.put("score",f.score());row.put("kind",f.kind().toString());
            row.put("reasons",Theme.reasons(f));row.put("observedGrowth",f.observedGrowth());row.put("disappearedClusters",f.disappearedClusters());
            row.put("loaded",ChunkFinderClient.SCANNER.isLoaded(f));findings.add(row);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Signal signal : Signal.values()) {
                String id = FinderTargets.key(signal);
                if (id != null && c.finderTargetEnabled(signal)) counts.put(id, f.evidence().count(signal));
            }
            row.put("targetCounts",counts);
        }
        snapshot.put("findings",findings); snapshot.put("loadedChunks",ChunkFinderClient.SCANNER.loadedCount());
        state=JSON.toJson(snapshot);
    }
    private LoopbackServer.Response request(LoopbackServer.Request request) {
        try {
            String provided=request.token();
            if(provided==null||!java.security.MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8))) {
                return new LoopbackServer.Response(403,"{\"message\":\"Unauthorized\"}");
            }
            String path=request.path();
            if("/state".equals(path)&&"GET".equals(request.method())) return new LoopbackServer.Response(200,state);
            if(!"POST".equals(request.method())) return new LoopbackServer.Response(405,"{\"message\":\"POST required\"}");
            byte[] body=request.body();
            JsonObject data=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
            CompletableFuture<String> task=new CompletableFuture<>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            Minecraft client=Minecraft.getInstance();
            client.execute(()->{
                if (task.isCancelled() || System.nanoTime() >= deadline) {
                    task.completeExceptionally(new TimeoutException("The game action expired before execution."));
                    return;
                }
                try { mutate(path,data,client);publish(client);task.complete(state); }
                catch(Exception ex) { task.completeExceptionally(ex); }
            });
            try { return new LoopbackServer.Response(200,task.get(3,TimeUnit.SECONDS)); }
            catch (TimeoutException ex) {
                task.cancel(false);
                throw new TimeoutException("Minecraft was busy; the expired action was not applied. Try again after the game resumes.");
            }
        } catch(Exception ex) { return new LoopbackServer.Response(400,JSON.toJson(Map.of("message",String.valueOf(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage())))); }
    }
    private void mutate(String path,JsonObject data,Minecraft client) throws IOException {
        FinderConfig c=ChunkFinderClient.CONFIG;
        refreshWorldSession(client);
        if (data.has("expectedWorldSession") && data.get("expectedWorldSession").getAsLong() != worldSession)
            throw new IllegalArgumentException("The Minecraft world changed before this action could run. Apply it again in the current world.");
        if("/module".equals(path)) {
            String id=data.get("id").getAsString();
            boolean enabled=booleanSetting(data,"enabled",false);
            ModuleControls.setEnabled(client,id,enabled);
            message=(enabled?"Enabled: ":"Disabled: ")+id;
        } else if("/settings".equals(path)) {
            // Validate controls before applying any setting in this request.
            int elytraRange=integerSetting(data,"elytraRange",c.elytraRange,32,512),elytraPlayerRange=integerSetting(data,"elytraPlayerRange",c.elytraPlayerRange,16,1024);
            boolean elytraWarn=booleanSetting(data,"elytraWarnPlayers",c.elytraWarnPlayers);
            int nextItemRange=integerSetting(data,"itemFinderRange",c.itemFinderRange,16,512),nextItemLimit=integerSetting(data,"itemFinderLimit",c.itemFinderLimit,16,192);
            int nextLootRange=integerSetting(data,"pvpLootProtectionRange",c.pvpLootProtectionRange,8,128);
            boolean nextLootProtection=booleanSetting(data,"pvpLootProtection",c.pvpLootProtection),nextItemChunks=booleanSetting(data,"itemFinderChunks",c.itemFinderChunks);
            Map<String,Boolean> nextItemTargets=ValuableItems.sanitize(c.itemFinderTargets);
            if(data.has("itemFinderTargets")) {
                if(!data.get("itemFinderTargets").isJsonObject()||data.getAsJsonObject("itemFinderTargets").size()>50)throw new IllegalArgumentException("Choose up to 50 item types");
                for(var entry:data.getAsJsonObject("itemFinderTargets").entrySet()) {
                    if(ValuableItems.find(entry.getKey())==null||!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Choose a known item type with a boolean selection");
                    nextItemTargets.put(entry.getKey(),entry.getValue().getAsBoolean());
                }
            }
            int maceRange=integerSetting(data,"maceRange",c.maceRange,6,64),maceCooldown=integerSetting(data,"maceChargeCooldown",c.maceChargeCooldown,20,200),maceReserve=integerSetting(data,"maceChargeReserve",c.maceChargeReserve,0,64),maceDurability=integerSetting(data,"maceDurabilityReserve",c.maceDurabilityReserve,1,64);
            double maceHealth=numberSetting(data,"maceRetreatHealth",c.maceRetreatHealth,4,16);
            if(!Double.isFinite(maceHealth)||maceHealth<4||maceHealth>16)throw new IllegalArgumentException("Mace retreat health must be 4–16 HP");
            boolean maceMovement=booleanSetting(data,"maceMovement",c.maceMovement),maceWind=booleanSetting(data,"maceWindCharges",c.maceWindCharges),maceShield=booleanSetting(data,"maceShield",c.maceShield),macePearls=booleanSetting(data,"macePearls",c.macePearls),maceStrafe=booleanSetting(data,"maceStrafe",c.maceStrafe);
            int nextIndicatorColor = integerSetting(data, "playerIndicatorColor", c.playerIndicatorColor, Integer.MIN_VALUE, Integer.MAX_VALUE);
            int nextVisualRange = integerSetting(data, "playerVisualRange", c.playerVisualRange, 16, 1024);
            double nextInfoScale = numberSetting(data, "playerInfoScale", c.playerInfoScale, 0.25, 1.0);
            double nextAimRange = numberSetting(data, "aimCursorRange", c.aimCursorRange, 3.0, 256.0);
            int nextTotemSlot=integerSetting(data,"autoTotemHotbarSlot",c.autoTotemHotbarSlot,1,9);
            int nextTotemReaction=integerSetting(data,"autoTotemReactionDelayMs",c.autoTotemReactionDelayMs,0,1000);
            int nextTotemCooldown=integerSetting(data,"autoTotemCooldownMs",c.autoTotemCooldownMs,100,5000);
            boolean nextFastAssist=booleanSetting(data,"pvpFastAssist",c.pvpFastAssist),nextRepeat=booleanSetting(data,"pvpCrystalRepeat",c.pvpCrystalRepeat),nextFastAttack=booleanSetting(data,"pvpFastAttack",c.pvpFastAttack),nextBlastChecks=booleanSetting(data,"pvpManualBlastChecks",c.pvpManualBlastChecks);
            boolean nextWhiteGlintAllGear=booleanSetting(data,"whiteGlintAllGear",c.whiteGlintAllGear);
            boolean nextStreamerPie=booleanSetting(data,"streamerPieChart",c.streamerPieChart),nextStreamerWatermark=booleanSetting(data,"streamerWatermark",c.streamerWatermark),nextModuleSounds=booleanSetting(data,"moduleSounds",c.moduleSounds);
            boolean nextExposed=booleanSetting(data,"resourceExposedOnly",c.resourceExposedOnly),nextAsmr=booleanSetting(data,"asmrEnabled",c.asmrEnabled);
            var inputSoundUpdate=InputSoundSettings.validate(data,c);
            double nextAsmrVolume=numberSetting(data,"asmrVolume",c.asmrVolume,0,1);
            String nextAsmrProfile=c.asmrProfile;
            if(data.has("asmrProfile")){if(!data.get("asmrProfile").isJsonPrimitive()||!data.getAsJsonPrimitive("asmrProfile").isString()||!KeyboardSounds.PROFILES.contains(data.get("asmrProfile").getAsString()))throw new IllegalArgumentException("Choose a recorded keyboard");nextAsmrProfile=data.get("asmrProfile").getAsString();}
            var nextKeyProfiles=new java.util.LinkedHashMap<>(c.asmrKeyProfiles);
            if(data.has("asmrKeyProfiles")){
                if(!data.get("asmrKeyProfiles").isJsonObject()||data.getAsJsonObject("asmrKeyProfiles").size()>100)throw new IllegalArgumentException("Choose valid keyboard keys");
                nextKeyProfiles.clear();
                for(var entry:data.getAsJsonObject("asmrKeyProfiles").entrySet()){
                    int code;try{code=Integer.parseInt(entry.getKey());}catch(NumberFormatException ex){throw new IllegalArgumentException("Invalid keyboard key");}
                    if(!Integer.toString(code).equals(entry.getKey())||!KeyboardSounds.supported(code)||!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString()||!KeyboardSounds.PROFILES.contains(entry.getValue().getAsString()))throw new IllegalArgumentException("Choose a recorded keyboard for each key");
                    nextKeyProfiles.put(entry.getKey(),entry.getValue().getAsString());
                }
            }
            int nextResourceRange=integerSetting(data,"resourceRange",c.resourceRange,16,128);
            int nextResourceLimit=integerSetting(data,"resourceLimit",c.resourceLimit,32,192);
            Map<String,Boolean> nextResourceTargets=null;
            if(data.has("resourceTargets")) {
                if(!data.get("resourceTargets").isJsonObject())throw new IllegalArgumentException("resourceTargets must be an object");
                nextResourceTargets=c.activeMinerals();
                for(var entry:data.getAsJsonObject("resourceTargets").entrySet()) {
                    if(MineralType.byId(entry.getKey())==null)throw new IllegalArgumentException("Unknown mineral: "+entry.getKey());
                    var value=entry.getValue();
                    if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Mineral selection must be boolean: "+entry.getKey());
                    nextResourceTargets.put(entry.getKey(),value.getAsBoolean());
                }
            }
            if (data.has("finderTargets")) {
                if (!data.get("finderTargets").isJsonObject()) throw new IllegalArgumentException("finderTargets must be an object");
                Map<String, Boolean> nextTargets = FinderTargets.sanitize(c.finderTargets);
                JsonObject supplied = data.getAsJsonObject("finderTargets");
                for (String id : FinderTargets.IDS) if (supplied.has(id)) {
                    JsonElement value = supplied.get(id);
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                        throw new IllegalArgumentException("Finder target must be boolean: " + id);
                    nextTargets.put(id, value.getAsBoolean());
                }
                c.finderTargets = nextTargets;
                ChunkFinderClient.SCANNER.requestRescan();
            }
            if(data.has("sensitivity"))c.sensitivity=data.get("sensitivity").getAsInt();
            if(data.has("radiusChunks"))c.radiusChunks=data.get("radiusChunks").getAsInt();
            if(data.has("espRange"))c.espRange=data.get("espRange").getAsInt();
            if(data.has("espTracers"))c.espTracers=data.get("espTracers").getAsBoolean();
            if(data.has("xrayExposedOnly")) {
                JsonElement value=data.get("xrayExposedOnly");
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException("Exposed ore filter must be boolean.");
                c.xrayExposedOnly=value.getAsBoolean();
            }
            if(data.has("freecamSpeed")) {
                JsonElement value=data.get("freecamSpeed");
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!Double.isFinite(value.getAsDouble()))
                    throw new IllegalArgumentException("Freecam speed must be a finite number.");
                c.freecamSpeed=value.getAsDouble();
            }
            if(data.has("autoSellItems")) {
                var chosen=new java.util.LinkedHashMap<String,Boolean>();
                for(String id:dev.amethyst.finder.modules.FarmItems.IDS) {
                    JsonElement value=data.getAsJsonObject("autoSellItems").get(id);
                    if(value!=null&&(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean()))throw new IllegalArgumentException("Farm selections must be boolean");
                    chosen.put(id,value!=null&&value.getAsBoolean());
                }
                c.autoSellItems=chosen;
            }
            if(data.has("autoSellCommand")) {
                String command=data.get("autoSellCommand").getAsString().strip().replaceFirst("^/", "");
                if(!command.matches("[a-zA-Z0-9_:-]{1,40}"))throw new IllegalArgumentException("Enter a command name only, without arguments");
                c.autoSellCommand=command;
            }
            if(data.has("autoSellMenuTitle")) {
                String title=data.get("autoSellMenuTitle").getAsString().strip();
                if(title.length()>80)throw new IllegalArgumentException("Sell menu title is limited to 80 characters");
                c.autoSellMenuTitle=title;
            }
            if(data.has("autoSellIntervalSeconds"))c.autoSellIntervalSeconds=data.get("autoSellIntervalSeconds").getAsInt();
            if(data.has("autoLogoutRange"))c.autoLogoutRange=data.get("autoLogoutRange").getAsInt();
            if(data.has("searchChunkMinClusters")) { c.searchChunkMinClusters=data.get("searchChunkMinClusters").getAsInt(); }
            if(data.has("autoRelogY"))c.autoRelogY=data.get("autoRelogY").getAsInt();
            if(data.has("autoRelogDelaySeconds"))c.autoRelogDelaySeconds=data.get("autoRelogDelaySeconds").getAsInt();
            if(data.has("espLimit"))c.espLimit=data.get("espLimit").getAsInt();
            if(data.has("pvpRange"))c.pvpRange=data.get("pvpRange").getAsDouble();
            if(data.has("pvpDelayTicks"))c.pvpDelayTicks=data.get("pvpDelayTicks").getAsInt();
            if(data.has("pvpMaxSelfDamage"))c.pvpMaxSelfDamage=data.get("pvpMaxSelfDamage").getAsDouble();
            if(data.has("pvpMinTargetDamage"))c.pvpMinTargetDamage=data.get("pvpMinTargetDamage").getAsDouble();
            if(data.has("pvpTotemHealth"))c.pvpTotemHealth=data.get("pvpTotemHealth").getAsDouble();
            if(data.has("pvpTargetPlayers"))c.pvpTargetPlayers=data.get("pvpTargetPlayers").getAsBoolean();
            if(data.has("pvpTargetHostiles"))c.pvpTargetHostiles=data.get("pvpTargetHostiles").getAsBoolean();
            c.pvpFastAssist=nextFastAssist;c.pvpCrystalRepeat=nextRepeat;c.pvpFastAttack=nextFastAttack;c.pvpManualBlastChecks=nextBlastChecks;
            if(data.has("pvpManualAssist"))c.pvpManualAssist=data.get("pvpManualAssist").getAsBoolean();
            if(data.has("pvpAnchorCover"))c.pvpAnchorCover=data.get("pvpAnchorCover").getAsBoolean();
            if(data.has("pvpFriends"))c.pvpFriends=data.get("pvpFriends").isJsonNull()?"":data.get("pvpFriends").getAsString();
            c.playerIndicatorColor = 0xFF000000 | (nextIndicatorColor & 0x00FFFFFF);
            c.playerVisualRange = nextVisualRange;
            c.playerInfoScale = nextInfoScale;
            c.aimCursorRange = nextAimRange;
            c.autoTotemHotbarSlot=nextTotemSlot;c.autoTotemReactionDelayMs=nextTotemReaction;c.autoTotemCooldownMs=nextTotemCooldown;
            c.whiteGlintAllGear=nextWhiteGlintAllGear;
            c.streamerPieChart=nextStreamerPie;c.streamerWatermark=nextStreamerWatermark;c.moduleSounds=nextModuleSounds;
            c.resourceExposedOnly=nextExposed;c.asmrEnabled=nextAsmr;c.asmrProfile=nextAsmrProfile;c.asmrVolume=nextAsmrVolume;
            c.asmrKeyProfiles=nextKeyProfiles;inputSoundUpdate.apply(c);
            c.resourceRange=nextResourceRange;c.resourceLimit=nextResourceLimit;
            if(nextResourceTargets!=null)c.selectMinerals(nextResourceTargets);
            if(data.has("botMinDamage"))c.botMinDamage=data.get("botMinDamage").getAsDouble();
            if(data.has("botMaxSelfDamage"))c.botMaxSelfDamage=data.get("botMaxSelfDamage").getAsDouble();
            if(data.has("botRange"))c.botRange=integerSetting(data,"botRange",48,8,96);
            if(data.has("botMovement"))c.botMovement=booleanSetting(data,"botMovement",true);
            if(data.has("botStrafe"))c.botStrafe=booleanSetting(data,"botStrafe",true);
            if(data.has("botPearls"))c.botPearls=booleanSetting(data,"botPearls",true);
            if(data.has("botBuild"))c.botBuild=booleanSetting(data,"botBuild",true);
            if(data.has("botCrystals"))c.botCrystals=booleanSetting(data,"botCrystals",true);
            if(data.has("botAnchors"))c.botAnchors=booleanSetting(data,"botAnchors",true);
            if(data.has("botExtinguish"))c.botExtinguish=booleanSetting(data,"botExtinguish",true);
            c.elytraRange=elytraRange;c.elytraPlayerRange=elytraPlayerRange;c.elytraWarnPlayers=elytraWarn;
            c.itemFinderRange=nextItemRange;c.itemFinderLimit=nextItemLimit;c.itemFinderTargets=nextItemTargets;c.itemFinderChunks=nextItemChunks;
            c.pvpLootProtection=nextLootProtection;c.pvpLootProtectionRange=nextLootRange;
            c.maceRange=maceRange;c.maceChargeCooldown=maceCooldown;c.maceChargeReserve=maceReserve;c.maceDurabilityReserve=maceDurability;c.maceRetreatHealth=maceHealth;c.maceMovement=maceMovement;c.maceWindCharges=maceWind;c.maceShield=maceShield;c.macePearls=macePearls;c.maceStrafe=maceStrafe;
            c.sanitize();message="Settings saved";
        } else if("/action".equals(path)) {
            String action=data.get("action").getAsString();
            if("vault_config".equals(action)) {
                if(c.isModuleEnabled("auto_vault")||Boolean.TRUE.equals(ChunkFinderClient.VAULTS.state(client).get("movingKey")))throw new IllegalStateException("Switch the vault helper off and wait for the key move before changing its target");
                String type=data.get("type").getAsString(),reward=data.get("reward").getAsString();
                if(!VaultRewards.validType(type)||VaultRewards.find(type,reward)==null)throw new IllegalArgumentException("Choose a reward available in this vault type");
                c.vaultType=type;if(type.equals("normal"))c.vaultNormalReward=reward;else c.vaultOminousReward=reward;
                message="Vault preview selected · actual rewards remain random";
            }
            else if(java.util.Set.of("friend_add","friend_remove","friend_rule").contains(action)) {
                Friends.change(c,action,data.get("name").getAsString(),data.has("rule")?data.get("rule").getAsString():"",booleanSetting(data,"enabled",true));
                message="Friends updated";
            }
            else if("stream_start".equals(action)){ChatStreamer.start(client,data.get("target").getAsString(),data.get("templates").getAsString(),integerSetting(data,"interval",15,10,300));message="Stream chat started";}
            else if("stream_save".equals(action)){ChatStreamer.save(data.get("target").getAsString(),data.get("templates").getAsString(),integerSetting(data,"interval",15,10,300));message="Stream template saved";}
            else if("stream_stop".equals(action)){ChatStreamer.stop();message="Stream chat stopped";}
            else if("streamer_mode".equals(action)){boolean on=booleanSetting(data,"enabled",false);StreamerMode.set(client,on);message=on?"Streamer Mode enabled · gameplay modules off":"Streamer Mode disabled";}
            else if("voice_mix_test".equals(action)){if(!FabricLoader.getInstance().isModLoaded("voicechat"))throw new IllegalStateException("Simple Voice Chat is not installed");dev.amethyst.finder.audio.VoiceInputPlugin.openMicrophoneTest(client);message="Local voice + ASMR test · speak and press keys · close settings when finished";}
            else if("voice_unmute".equals(action)){if(!FabricLoader.getInstance().isModLoaded("voicechat"))throw new IllegalStateException("Simple Voice Chat is not installed");dev.amethyst.finder.audio.VoiceInputPlugin.unmute();message="Microphone unmuted · voice and ASMR share the channel";}
            else if("mouse_preview".equals(action)){int button=integerSetting(data,"button",0,0,4);String profile=data.has("profile")?data.get("profile").getAsString():MouseSounds.profileFor(button);if(!MouseSounds.play(client,button,profile,false))throw new IllegalArgumentException("Choose a valid recorded mouse click");message="Recorded mouse preview";}
            else if("keyboard_preview".equals(action)){int code=integerSetting(data,"key",4,0,511);String profile=data.has("profile")?data.get("profile").getAsString():KeyboardSounds.profileFor(code);if(!KeyboardSounds.play(client,code,profile))throw new IllegalArgumentException("Choose a valid recorded key");message="Recorded keyboard preview";}
            else if("module_keybind".equals(action)) {
                if(!data.has("module")||!data.get("module").isJsonPrimitive()||!data.getAsJsonPrimitive("module").isString())throw new IllegalArgumentException("Choose a module");
                if(!data.has("key"))throw new IllegalArgumentException("Choose a key");
                int code=integerSetting(data,"key",0,0,511);String id=data.get("module").getAsString();
                ModuleKeybinds.assign(client,id,code);message=ModuleCatalog.find(id).name()+" · Keybind "+ModuleKeybinds.label(code);
            }
            else if("totem_pack".equals(action)){boolean on=booleanSetting(data,"enabled",false);texturePacks.applyTotem(client,on?data.get("variant").getAsString():"",on);message="Totem appearance updated";}
            else if("addon_pack".equals(action)){texturePacks.applyAddon(client,data.get("variant").getAsString(),booleanSetting(data,"enabled",false));message="Texture add-on updated";}
            else if("pig_pack".equals(action)) {
                if(!data.has("variant")||!data.get("variant").isJsonPrimitive()||!data.getAsJsonPrimitive("variant").isString())throw new IllegalArgumentException("A Pig pack variant is required.");
                if(!data.has("enabled"))throw new IllegalArgumentException("The Pig pack switch must be a boolean.");
                boolean on=booleanSetting(data,"enabled",false);
                texturePacks.apply(client,data.get("variant").getAsString(),on);
                message=on?"Applying "+TexturePacks.variant(data.get("variant").getAsString()).name():"Pig pack switched off";
            }
            else if("media_setting".equals(action)) {
                String key=data.has("key")?data.get("key").getAsString():"";
                boolean value=booleanSetting(data,"enabled",false);
                if(!Set.of("sky","crosshair","sounds").contains(key))throw new IllegalArgumentException("Unknown visual setting");
                if(key.equals("sky")&&value&&(!MediaPack.skyMod()||!MediaPack.skyAvailable()))throw new IllegalArgumentException("Choose a sky image and install Custom Skyboxes first.");
                texturePacks.checkIdle();
                if(key.equals("sky"))c.customSkyEnabled=value;
                else if(key.equals("crosshair"))c.thinCrosshair=value;
                else c.packSoundsEnabled=value;
                c.save();texturePacks.refreshMedia(client);message="Applying your visual settings";
            }
            else if("smooth_pvp".equals(action)) {SmoothProfile.apply(client);message="Maximum FPS applied · frame cap removed · visual settings unchanged";}
            else if("restore_smooth_pvp".equals(action)) {SmoothProfile.restore(client);message="Previous FPS limit and VSync restored";}
            else if("find_player".equals(action)) {
                var pin=ChunkFinderClient.PLAYERS.pin(client,data.get("playerId").getAsString());
                message="Saved "+pin.name()+" · "+pin.coordinates();
            }
            else if("clear_player_waypoint".equals(action)){ChunkFinderClient.PLAYERS.clear();message="Saved player marker cleared";}
            else if("clear".equals(action)){ChunkFinderClient.SCANNER.clear();message="History cleared";}
            else if("resume".equals(action)){
                if (client.player != null) {
                    if (client.gui.screen() instanceof PauseScreen) client.gui.setScreen(null);
                    if (client.gui.screen() == null) {
                        client.mouseHandler.grabMouse();
                        client.mouseHandler.setIgnoreFirstMove();
                        message="Back to game";
                    } else message="Close the Minecraft inventory or chat to resume gameplay.";
                } else message="Join a Minecraft world to resume gameplay.";
            }
            else if("export".equals(action))message="CSV: "+FindingExporter.export(ChunkFinderClient.SCANNER.findings(),client.level==null?"":client.level.dimension().toString());
            else throw new IllegalArgumentException("Unknown action");
        } else throw new IllegalArgumentException("Unknown request");
        c.save();ChunkFinderClient.SCANNER.refresh(client);
    }
    static double numberSetting(JsonObject data, String name, double fallback, double minimum, double maximum) {
        if (!data.has(name)) return fallback;
        JsonElement value = data.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(name + " must be a number.");
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < minimum || result > maximum)
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        return result;
    }
    static boolean booleanSetting(JsonObject data,String key,boolean fallback) {
        if(!data.has(key))return fallback;
        var value=data.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key+" must be boolean");
        return value.getAsBoolean();
    }
    static int integerSetting(JsonObject data, String name, int fallback, int minimum, int maximum) {
        double result = numberSetting(data, name, fallback, minimum, maximum);
        if (result != Math.rint(result)) throw new IllegalArgumentException(name + " must be an integer.");
        return (int) result;
    }
    public void close() {
        server.close();
        try {if(Files.exists(descriptor)&&Files.readString(descriptor).contains(token))Files.delete(descriptor);}catch(IOException ignored){}
    }
}
