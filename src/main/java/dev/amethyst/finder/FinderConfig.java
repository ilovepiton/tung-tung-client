package dev.amethyst.finder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.amethyst.finder.core.ScanOptions;
import dev.amethyst.finder.core.Signal;
import dev.amethyst.finder.core.FinderTargets;
import dev.amethyst.finder.core.MineralType;
import dev.amethyst.finder.modules.CameraSpeed;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public final class FinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = false, storage = true, amethyst = true, builds = true;
    public boolean hud = true, notifications = true;
    public int sensitivity = 65, radiusChunks = 12, budgetMs = 2;
    public String language = "en";
    public Map<String, Boolean> modules = new HashMap<>();
    public Map<String,Integer> moduleKeybinds = new HashMap<>(Map.of("aim_cursor",com.mojang.blaze3d.platform.InputConstants.KEY_R,"combat_bot",com.mojang.blaze3d.platform.InputConstants.KEY_P));
    public Map<String, Boolean> finderTargets = FinderTargets.defaults();
    public int espRange = 128, espLimit = 512;
    public int searchChunkMinClusters=4, autoRelogY=0, autoRelogDelaySeconds=5;
    public int elytraRange=256,elytraPlayerRange=256;
    public boolean elytraWarnPlayers=true;
    public int itemFinderRange=128,itemFinderLimit=96;
    public Map<String,Boolean> itemFinderTargets=ValuableItems.sanitize(null);
    public boolean itemFinderChunks=true;
    public boolean pvpLootProtection=true;
    public int pvpLootProtectionRange=32;
    public int resourceRange = 64, resourceLimit = 96;
    public Map<String,Boolean> resourceTargets = MineralType.defaults();
    public boolean espTracers = true;
    public boolean xrayExposedOnly = true;
    public int playerIndicatorColor = 0xFFFF9D43;
    public int playerVisualRange = 256;
    public double playerInfoScale = 0.50;
    public double aimCursorRange = 64.0;
    public double freecamSpeed = CameraSpeed.DEFAULT;
    public Map<String,Boolean> autoSellItems = dev.amethyst.finder.modules.FarmItems.sanitize(null);
    public String autoSellCommand = "sell", autoSellMenuTitle = "";
    public int autoSellIntervalSeconds = 5, autoLogoutRange = 32;
    public boolean chunkHud = true, showHistoricalChunks = true;
    public int chunkRangeBlocks = 256, chunkLimit = 64, chunkMemoryVersion = 1;
    public String historyProfile = "default";
    public double pvpRange = 4, pvpMaxSelfDamage = 6, pvpMinTargetDamage = 4, pvpTotemHealth = 10;
    public int pvpDelayTicks = 6;
    /** Auto Totem Pro-compatible defaults. Slot is shown as the familiar 1–9 hotbar number. */
    public int autoTotemHotbarSlot = 6, autoTotemReactionDelayMs = 70, autoTotemCooldownMs = 700;
    public boolean pvpTargetPlayers = true, pvpTargetHostiles = false;
    public boolean pvpManualAssist = true, pvpAnchorCover = true;
    public boolean pvpFastAssist = true, pvpCrystalRepeat = true, pvpFastAttack = true;
    public boolean pvpManualBlastChecks = false;
    public String pvpFriends = "";
    public Map<String,Map<String,Boolean>> friendRules=new java.util.LinkedHashMap<>();
    public int maceRange=24,maceChargeCooldown=60,maceChargeReserve=2,maceDurabilityReserve=4;
    public double maceRetreatHealth=8;
    public boolean maceMovement=true,maceWindCharges=true,maceShield=true,macePearls=true,maceStrafe=true;
    public int botRange=48,botKeybindVersion=0,botCombatVersion=0;
    public double botMinDamage=1.5,botMaxSelfDamage=10;
    public boolean botMovement=true,botStrafe=true,botPearls=true,botBuild=true,botCrystals=true,botAnchors=true,botExtinguish=true;
    public String pigPackVariant = "black";
    public boolean whiteGlintAllGear = true;
    public boolean performanceProfileActive = false, performancePreviousVsync = true;
    public int performancePreviousFps = 120;
    public boolean customSkyEnabled = false;
    public boolean asmrEnabled = false, resourceExposedOnly = true;
    public String asmrProfile = "red";
    public java.util.Map<String,String> asmrKeyProfiles = new java.util.LinkedHashMap<>();
    public double asmrVolume = .35;
    public volatile boolean mouseSoundsEnabled=false,voiceInputSounds=true,voicePolish=true;
    public String mouseSoundProfile="click01";
    public double mouseSoundVolume=.35;
    public volatile double voiceEffectsVolume=.28,voiceGain=1.35;
    public java.util.Map<String,String> mouseButtonProfiles=new java.util.LinkedHashMap<>();
    public java.util.Map<String,Double> asmrKeyVolumes=new java.util.LinkedHashMap<>(),mouseButtonVolumes=new java.util.LinkedHashMap<>();
    public String streamTarget="",streamTemplates=ChatStreamer.defaults();
    public int streamInterval=15;
    public boolean streamerMode=false,streamerPieChart=true,streamerWatermark=true,moduleSounds=true;
    public boolean clientMapsEnabled=true,mapInSlotEnabled=true;
    public String vaultType="ominous",vaultNormalReward="trident",vaultOminousReward="heavy_core";
    public String vaultReward(){return "normal".equals(vaultType)?vaultNormalReward:vaultOminousReward;}
    public boolean thinCrosshair = true, packSoundsEnabled = true;
    public transient String saveError = "";

    public ScanOptions options() { return new ScanOptions(true, amethyst, true, sensitivity, finderTargets, enabled); }
    public boolean finderTargetEnabled(Signal signal) { return FinderTargets.enabled(finderTargets, signal); }
    public double freecamSpeed() { return CameraSpeed.sanitize(freecamSpeed); }
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("tun-tun-client.json"); }
    public boolean isModuleEnabled(String id) {
        if ("chunk_finder".equals(id)) return enabled;
        if ("client_maps".equals(id)) return clientMapsEnabled;
        if ("map_in_slot".equals(id)) return mapInSlotEnabled;
        return modules != null && Boolean.TRUE.equals(modules.get(id));
    }
    public void setModuleEnabled(String id, boolean value) {
        if ("chunk_finder".equals(id)) enabled = value;
        else if ("client_maps".equals(id)) clientMapsEnabled=value;
        else if ("map_in_slot".equals(id)) mapInSlotEnabled=value;
        else { if (modules == null) modules = new HashMap<>(); modules.put(id, value); }
        if(value&&"diamond_esp".equals(id)) {
            resourceTargets=MineralType.sanitize(resourceTargets);resourceTargets.put("diamond",true);
        }
        if(value&&"ore_esp".equals(id)&&java.util.Arrays.stream(MineralType.values()).noneMatch(m->m!=MineralType.DIAMOND&&(resourceTargets==null||!Boolean.FALSE.equals(resourceTargets.get(m.id))))) {
            resourceTargets=MineralType.sanitize(resourceTargets);
            for(var m:MineralType.values())if(m!=MineralType.DIAMOND)resourceTargets.put(m.id,true);
        }
    }
    public boolean mineralEnabled(MineralType mineral) {
        return isModuleEnabled(mineral.module()) && (resourceTargets==null || !Boolean.FALSE.equals(resourceTargets.get(mineral.id)));
    }
    public Map<String,Boolean> activeMinerals() {
        Map<String,Boolean> active=new java.util.LinkedHashMap<>();
        for(var mineral:MineralType.values())active.put(mineral.id,mineralEnabled(mineral));
        return active;
    }
    /** The picker changes the effective selection and its two legacy group switches atomically. */
    public void selectMinerals(Map<String,Boolean> selected) {
        resourceTargets=MineralType.sanitize(selected);
        setModuleEnabled("diamond_esp",Boolean.TRUE.equals(resourceTargets.get("diamond")));
        setModuleEnabled("ore_esp",java.util.Arrays.stream(MineralType.values()).anyMatch(m->m!=MineralType.DIAMOND&&Boolean.TRUE.equals(resourceTargets.get(m.id))));
    }
    /** New gameplay categories inherit K until the player explicitly changes their in-game toggle. */
    public boolean chunkModuleEnabled(String id) {
        if (id.equals("chest_chunks") || id.equals("amethyst_chunks") || id.equals("amethyst_growth")) {
            if (modules != null && modules.containsKey(id)) return Boolean.TRUE.equals(modules.get(id));
            return enabled && (id.equals("chest_chunks") || amethyst);
        }
        return isModuleEnabled(id);
    }
    public boolean requiresScan() {
        return enabled || isModuleEnabled("search_chunk") || isModuleEnabled("storage_esp") || isModuleEnabled("amethyst_esp")
                || isModuleEnabled("base_chunks") || isModuleEnabled("spawner_chunks")
                || chunkModuleEnabled("chest_chunks") || chunkModuleEnabled("amethyst_chunks")
                || chunkModuleEnabled("amethyst_growth");
    }
    public static FinderConfig load() {
        if (!Files.exists(path())) return new FinderConfig();
        try {
            FinderConfig c = GSON.fromJson(Files.readString(path()), FinderConfig.class);
            if (c == null) throw new IOException("Empty configuration");
            boolean migrateLanguage = !"en".equals(c.language);
            c.modules= c.modules==null?new HashMap<>():c.modules;
            c.modules.put("combat_bot",false); // Explicitly arm again after each application/session restart.
            boolean migrateChunkMemory = c.chunkMemoryVersion < 1;
            if (migrateChunkMemory) {
                c.showHistoricalChunks = true;
                c.chunkRangeBlocks = 256;
                c.chunkLimit = 64;
                c.chunkMemoryVersion = 1;
            }
            c.migrateBotKeybind();if(c.botCombatVersion<1){if(c.botRange==24)c.botRange=48;c.botCombatVersion=1;}c.sanitize();c.setModuleEnabled("auto_sell",false);c.setModuleEnabled("auto_logout",false);c.setModuleEnabled("auto_relog",false);
            if(c.espLimit==192)c.espLimit=512;
            if (migrateLanguage || migrateChunkMemory) c.save();
            return c;
        } catch (IOException | RuntimeException ex) {
            ChunkFinderClient.LOGGER.warn("Cannot read Chunk Finder settings; using defaults", ex);
            return new FinderConfig();
        }
    }
    private void migrateBotKeybind() {
        if(botKeybindVersion>=1)return;
        if(moduleKeybinds==null)moduleKeybinds=new HashMap<>();
        int p=com.mojang.blaze3d.platform.InputConstants.KEY_P,r=com.mojang.blaze3d.platform.InputConstants.KEY_R;
        if(!moduleKeybinds.containsKey("combat_bot")) {
            if(java.util.Objects.equals(moduleKeybinds.get("aim_cursor"),p)) {
                moduleKeybinds.remove("aim_cursor");if(!moduleKeybinds.containsValue(r))moduleKeybinds.put("aim_cursor",r);
            }
            if(!moduleKeybinds.containsValue(p))moduleKeybinds.put("combat_bot",p);
        }
        botKeybindVersion=1;
    }
    public void sanitize() {
        autoSellItems=dev.amethyst.finder.modules.FarmItems.sanitize(autoSellItems);
        autoSellIntervalSeconds=Math.clamp(autoSellIntervalSeconds,3,120); autoLogoutRange=Math.clamp(autoLogoutRange,8,256);
        if(autoSellCommand==null||!autoSellCommand.matches("[a-zA-Z0-9_:-]{1,40}"))autoSellCommand="sell";
        if(autoSellMenuTitle==null)autoSellMenuTitle="";
        autoSellMenuTitle=autoSellMenuTitle.strip().substring(0,Math.min(80,autoSellMenuTitle.strip().length()));
        itemFinderRange=Math.clamp(itemFinderRange,16,512);itemFinderLimit=Math.clamp(itemFinderLimit,16,192);
        itemFinderTargets=ValuableItems.sanitize(itemFinderTargets);pvpLootProtectionRange=Math.clamp(pvpLootProtectionRange,8,128);
        if(TexturePacks.VARIANTS.stream().noneMatch(v->v.id().equals(pigPackVariant)))pigPackVariant="black";
        elytraRange=Math.clamp(elytraRange,32,512);elytraPlayerRange=Math.clamp(elytraPlayerRange,16,1024);
        maceRange=Math.clamp(maceRange,6,64);maceChargeCooldown=Math.clamp(maceChargeCooldown,20,200);maceChargeReserve=Math.clamp(maceChargeReserve,0,64);maceDurabilityReserve=Math.clamp(maceDurabilityReserve,1,64);maceRetreatHealth=Math.clamp(finite(maceRetreatHealth,8),4,16);
        if(!VaultRewards.validType(vaultType==null?"":vaultType))vaultType="ominous";
        if(VaultRewards.find("normal",vaultNormalReward)==null)vaultNormalReward="trident";
        if(VaultRewards.find("ominous",vaultOminousReward)==null)vaultOminousReward="heavy_core";
        streamTarget=streamTarget==null?"":streamTarget.substring(0,Math.min(16,streamTarget.length()));streamTemplates=streamTemplates==null?ChatStreamer.defaults():streamTemplates.substring(0,Math.min(12000,streamTemplates.length()));streamInterval=Math.clamp(streamInterval,10,300);
        sensitivity = Math.clamp(sensitivity, 10, 90);
        radiusChunks = Math.clamp(radiusChunks, 2, 32);
        budgetMs = Math.clamp(budgetMs, 1, 6);
        language = "en";
        if (modules == null) modules = new HashMap<>();
        if(moduleKeybinds==null)moduleKeybinds=new HashMap<>();
        var cleanBinds=new HashMap<String,Integer>();var usedKeys=new java.util.HashSet<Integer>();
        for(var entry:ModuleCatalog.ENTRIES){Integer code=moduleKeybinds.get(entry.id());if(code!=null&&code!=0&&ModuleKeybinds.valid(code)&&usedKeys.add(code))cleanBinds.put(entry.id(),code);}
        moduleKeybinds=cleanBinds;
        finderTargets = FinderTargets.sanitize(finderTargets);
        espRange = Math.clamp(espRange, 16, 256);
        espLimit = Math.clamp(espLimit, 32, 1024);
        searchChunkMinClusters=Math.clamp(searchChunkMinClusters,1,64);autoRelogY=Math.clamp(autoRelogY,-64,64);autoRelogDelaySeconds=Math.clamp(autoRelogDelaySeconds,3,30);
        resourceRange = Math.clamp(resourceRange, 16, 128);
        resourceLimit = Math.clamp(resourceLimit, 32, 192);
        resourceTargets = MineralType.sanitize(resourceTargets);
        playerIndicatorColor = 0xFF000000 | (playerIndicatorColor & 0x00FFFFFF);
        playerVisualRange = Math.clamp(playerVisualRange, 16, 1024);
        playerInfoScale = Math.clamp(finite(playerInfoScale, 0.50), 0.25, 1.0);
        asmrVolume=Math.clamp(finite(asmrVolume,.35),0,1);if(!KeyboardSounds.PROFILES.contains(asmrProfile==null?"":asmrProfile))asmrProfile="red";
        if(asmrKeyProfiles==null)asmrKeyProfiles=new java.util.LinkedHashMap<>();
        asmrKeyProfiles.entrySet().removeIf(e->{try{return !KeyboardSounds.supported(Integer.parseInt(e.getKey()))||e.getValue()==null||!KeyboardSounds.PROFILES.contains(e.getValue());}catch(RuntimeException x){return true;}});
        mouseSoundVolume=Math.clamp(finite(mouseSoundVolume,.35),0,1);voiceEffectsVolume=Math.clamp(finite(voiceEffectsVolume,.28),0,1);voiceGain=Math.clamp(finite(voiceGain,1.35),.5,3);
        if(!MouseSounds.PROFILES.contains(mouseSoundProfile==null?"":mouseSoundProfile))mouseSoundProfile="click01";
        if(mouseButtonProfiles==null)mouseButtonProfiles=new java.util.LinkedHashMap<>();
        mouseButtonProfiles.entrySet().removeIf(e->{try{int n=Integer.parseInt(e.getKey());return n<0||n>4||!MouseSounds.PROFILES.contains(e.getValue()==null?"":e.getValue());}catch(Exception x){return true;}});
        if(asmrKeyVolumes==null)asmrKeyVolumes=new java.util.LinkedHashMap<>();if(mouseButtonVolumes==null)mouseButtonVolumes=new java.util.LinkedHashMap<>();
        asmrKeyVolumes.entrySet().removeIf(e->{try{return !KeyboardSounds.supported(Integer.parseInt(e.getKey()))||e.getValue()==null||!Double.isFinite(e.getValue())||e.getValue()<0||e.getValue()>1;}catch(Exception x){return true;}});
        mouseButtonVolumes.entrySet().removeIf(e->{try{int n=Integer.parseInt(e.getKey());return n<0||n>4||e.getValue()==null||!Double.isFinite(e.getValue())||e.getValue()<0||e.getValue()>1;}catch(Exception x){return true;}});
        aimCursorRange = Math.clamp(finite(aimCursorRange, 64.0), 3.0, 256.0);
        freecamSpeed = CameraSpeed.sanitize(freecamSpeed);
        chunkRangeBlocks = Math.clamp(chunkRangeBlocks, 16, 256);
        chunkLimit = Math.clamp(chunkLimit, 16, 64);
        historyProfile = historyProfile == null || historyProfile.isBlank() ? "default" : historyProfile.trim();
        if (historyProfile.length() > 64) historyProfile = historyProfile.substring(0,64);
        pvpRange = Math.clamp(finite(pvpRange, 4), 2, 6);
        pvpDelayTicks = Math.clamp(pvpDelayTicks, 2, 20);
        pvpMaxSelfDamage = Math.clamp(finite(pvpMaxSelfDamage, 6), 1, 12);
        pvpMinTargetDamage = Math.clamp(finite(pvpMinTargetDamage, 4), 1, 12);
        pvpTotemHealth = Math.clamp(finite(pvpTotemHealth, 10), 1, 20);
        autoTotemHotbarSlot=Math.clamp(autoTotemHotbarSlot,1,9);
        autoTotemReactionDelayMs=Math.clamp(autoTotemReactionDelayMs,0,1000);
        autoTotemCooldownMs=Math.clamp(autoTotemCooldownMs,100,5000);
        botRange=Math.clamp(botRange,8,96);botMinDamage=Math.clamp(finite(botMinDamage,1.5),.5,12);botMaxSelfDamage=Math.clamp(finite(botMaxSelfDamage,10),1,16);Friends.sanitize(this);
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    public void save() {
        sanitize();
        try {
            Files.createDirectories(path().getParent());
            Path temporary = path().resolveSibling("tun-tun-client.json.tmp");
            Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
            try { Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING); }
            saveError = "";
        } catch (IOException ex) {
            saveError = ex.getMessage();
            ChunkFinderClient.LOGGER.error("Cannot save Chunk Finder settings", ex);
        }
    }
    public String text(String ru, String en) { return en; }
}
