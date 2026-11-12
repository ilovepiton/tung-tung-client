package dev.amethyst.finder.modules;

import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.modules.pvp.PvpAcknowledgements;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client camera tools and normal, server-visible inventory/combat actions. */
public final class ClientModules {
    private final FinderConfig config;
    private LocalPlayer sessionPlayer;
    private Object sessionLevel;
    private LocalPlayer cameraPlayer;
    private ClientInput previousInput;
    private final LockedInput lockedInput = new LockedInput();
    private CameraType previousPerspective;
    private boolean freecamActive;
    private boolean freelookActive;
    private float cameraYaw, cameraPitch;
    private Vec3 cameraPosition = Vec3.ZERO, oldCameraPosition = Vec3.ZERO;
    private int ticks;
    private final dev.amethyst.finder.modules.pvp.TotemSwaps totems = new dev.amethyst.finder.modules.pvp.TotemSwaps();
    private final Set<UUID> nearbyPlayers = new HashSet<>();
    private boolean playerDetectionStarted;
    private String totemState = "";
    private String auraState = "";
    private int auraNextAttack;
    private int playerCount;
    private final FarmAutomation farm;

    public ClientModules(FinderConfig config) { this.config = config; this.farm = new FarmAutomation(config); }

    public static boolean capability(String id) {
        return switch (id) {
            case "freecam", "freelook", "auto_totem", "killaura", "player_detection", "auto_sell", "auto_logout" -> true;
            default -> false;
        };
    }

    public static String reason(String id) {
        return switch (id) {
            case "freecam" -> "Client camera only; the body remains vulnerable and only already loaded chunks are visible.";
            case "freelook" -> "Independent third-person camera; does not change the player's aim.";
            case "auto_totem" -> "Immediately equips an inventory totem whenever the offhand has none, at any health.";
            case "killaura" -> "Visible eligible targets within normal weapon reach; respects PvP targets, friends and attack cooldown.";
            case "player_detection" -> "Notifications for other players actually loaded by the server.";
            default -> "This client does not implement a server-specific bypass for this module.";
        };
    }

    public boolean setEnabled(String id, boolean enabled) {
        if (!capability(id)) return false;
        config.setModuleEnabled(id, enabled);
        if ("auto_sell".equals(id)) farm.reset();
        if ("auto_totem".equals(id)) {
            var client=Minecraft.getInstance();synchronizeSession(client);
            totemState = enabled ? totems.received(client,config) : "Disabled";
        }
        if ("killaura".equals(id)) { auraNextAttack = 0; auraState = enabled ? "Waiting for an eligible target" : "Disabled"; }
        if (enabled && "freecam".equals(id)) config.setModuleEnabled("freelook", false);
        if (enabled && "freelook".equals(id)) config.setModuleEnabled("freecam", false);
        if (!enabled && ("freecam".equals(id) || "freelook".equals(id))) stopCamera(Minecraft.getInstance());
        config.save();
        return true;
    }

    public void setAuraTargetsPlayers(boolean enabled) {
        config.setModuleEnabled("killaura_players", enabled);
        config.pvpTargetPlayers = enabled;
        config.save();
    }

    public boolean auraTargetsPlayers() { return config.pvpTargetPlayers; }

    private void synchronizeSession(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (sessionPlayer != null) reset(client);
            return;
        }
        if (sessionPlayer != client.player || sessionLevel != client.level) {
            if (sessionPlayer != null) reset(client);
            sessionPlayer = client.player;
            sessionLevel = client.level;
        }
    }

    public void tick(Minecraft client) {
        synchronizeSession(client);
        if(client.player==null||client.level==null)return;
        if (!client.player.isAlive()) {
            disableCamera(client);
            totemState = auraState = "Paused: player unavailable";
            return;
        }
        if (farm.tick(client)) return;
        boolean freecam = config.isModuleEnabled("freecam");
        boolean freelook = config.isModuleEnabled("freelook") && !freecam;
        if (freecam != freecamActive || freelook != freelookActive) {
            stopCamera(client);
            if (freecam || freelook) startCamera(client, freecam);
        }
        if (cameraActive()) client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (freecamActive) moveFreecam(client);
        if (dev.amethyst.finder.modules.pvp.TotemSwaps.enabled(config) || totems.pending()) totemState=totems.received(client,config);
        else totemState = "";
        if (config.isModuleEnabled("killaura")) tickAura(client);
        else auraState = "";
        if (++ticks % 10 == 0 && config.isModuleEnabled("player_detection")) tickPlayerDetection(client);
        else if (!config.isModuleEnabled("player_detection")) {
            nearbyPlayers.clear();
            playerDetectionStarted = false;
            playerCount = 0;
        }
    }

    /** Call on disconnect; camera modes never carry a detached body into another world. */
    public void reset(Minecraft client) {
        disableCamera(client);
        farm.reset();
        config.setModuleEnabled("auto_sell",false);
        config.setModuleEnabled("auto_logout",false);
        config.save();
        sessionPlayer = null;
        sessionLevel = null;
        nearbyPlayers.clear();
        playerDetectionStarted = false;
        playerCount = 0;
        ticks = 0;
        auraNextAttack = 0; totems.reset();
        totemState = auraState = "";
    }

    private void disableCamera(Minecraft client) {
        stopCamera(client);
        boolean changed = config.isModuleEnabled("freecam") || config.isModuleEnabled("freelook");
        config.setModuleEnabled("freecam", false);
        config.setModuleEnabled("freelook", false);
        if (changed) config.save();
    }

    private void startCamera(Minecraft client, boolean freecam) {
        cameraPlayer = client.player;
        cameraYaw = cameraPlayer.getYRot();
        cameraPitch = cameraPlayer.getXRot();
        cameraPosition = oldCameraPosition = cameraPlayer.getEyePosition();
        previousPerspective = client.options.getCameraType();
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        freecamActive = freecam;
        freelookActive = !freecam;
        if (freecam) {
            previousInput = cameraPlayer.input;
            lockedInput.tick();
            cameraPlayer.input = lockedInput;
            cameraPlayer.setSprinting(false);
            if (client.gameMode != null) {
                client.gameMode.stopDestroyBlock();
                if (cameraPlayer.isUsingItem()) client.gameMode.releaseUsingItem(cameraPlayer);
            }
        }
    }

    private void stopCamera(Minecraft client) {
        if (cameraPlayer == null && previousPerspective == null) return;
        if (cameraPlayer != null && cameraPlayer.input == lockedInput && previousInput != null) {
            cameraPlayer.input = previousInput;
        }
        if (previousPerspective != null) client.options.setCameraType(previousPerspective);
        previousPerspective = null;
        previousInput = null;
        cameraPlayer = null;
        freecamActive = freelookActive = false;
        client.mouseHandler.setIgnoreFirstMove();
    }

    private void moveFreecam(Minecraft client) {
        oldCameraPosition = cameraPosition;
        if (client.gui.screen() != null || !client.mouseHandler.isMouseGrabbed()) return;
        double forward = (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
        double side = (client.options.keyLeft.isDown() ? 1 : 0) - (client.options.keyRight.isDown() ? 1 : 0);
        double up = (client.options.keyJump.isDown() ? 1 : 0) - (client.options.keyShift.isDown() ? 1 : 0);
        double yaw = Math.toRadians(cameraYaw), pitch = Math.toRadians(cameraPitch);
        Vec3 movement = new Vec3(
            -Math.sin(yaw) * Math.cos(pitch) * forward + Math.cos(yaw) * side,
            -Math.sin(pitch) * forward + up,
            Math.cos(yaw) * Math.cos(pitch) * forward + Math.sin(yaw) * side
        );
        if (movement.lengthSqr() > 1) movement = movement.normalize();
        double speed = CameraSpeed.effectiveBlocksPerTick(config.freecamSpeed(), client.options.keySprint.isDown());
        cameraPosition = cameraPosition.add(movement.scale(speed));
    }

    /** Used by the mouse mixin after vanilla sensitivity/inversion calculation. */
    public boolean turnCamera(LocalPlayer player, double dx, double dy) {
        if (!cameraActive() || player != cameraPlayer) return false;
        cameraYaw = Mth.wrapDegrees(cameraYaw + (float) (dx * 0.15));
        cameraPitch = Mth.clamp(cameraPitch + (float) (dy * 0.15), -90, 90);
        return true;
    }

    public boolean cameraActive() {
        Minecraft client = Minecraft.getInstance();
        return (freecamActive || freelookActive) && client.player == cameraPlayer && client.level == sessionLevel;
    }
    public boolean freecamActive() { return cameraActive() && freecamActive; }
    public boolean freelookActive() { return cameraActive() && freelookActive; }
    public float cameraYaw() { return cameraYaw; }
    public float cameraPitch() { return cameraPitch; }
    public Vec3 cameraPosition(float partialTicks) {
        if (freelookActive()) return cameraPlayer.getEyePosition(partialTicks);
        return oldCameraPosition.lerp(cameraPosition, Mth.clamp(partialTicks, 0, 1));
    }

    public void earlyInventoryTick(Minecraft client) {
        synchronizeSession(client);
        if(dev.amethyst.finder.modules.pvp.TotemSwaps.enabled(config)||totems.pending()) totemState=totems.tick(client,config);
    }
    public void onServerInventoryUpdate(Minecraft client) {
        onServerInventoryUpdate(client,false);
    }
    public void onServerInventoryUpdate(Minecraft client, boolean fullInventory) {
        synchronizeSession(client);
        if (dev.amethyst.finder.modules.pvp.TotemSwaps.enabled(config)||totems.pending())
            totemState=totems.inventoryReceived(client,config,fullInventory);
    }
    public void onSelfTotemPop(Minecraft client) {
        synchronizeSession(client);totemState=totems.popped(client,config);
    }
    public void onSelfDeath(Minecraft client) { synchronizeSession(client);totems.died(client); }
    public java.util.Map<String,Object> totemDiagnostics(Minecraft client) {
        return totems.diagnostics(client);
    }
    public boolean totemSwapPending() {
        return dev.amethyst.finder.modules.pvp.TotemSwaps.enabled(config)&&totems.pending();
    }

    private void tickAura(Minecraft client) {
        LocalPlayer player = client.player;
        if(dev.amethyst.finder.ChunkFinderClient.PVP!=null&&dev.amethyst.finder.ChunkFinderClient.PVP.ownsCombat()) {
            auraState="Paused: another combat helper owns attacks · disable Auto Punch / Kill Steal or stop its combo";return;
        }
        if (freecamActive() || freelookActive() || client.gui.screen() != null || client.gui.overlay()!=null || client.gameMode == null || !client.isWindowActive() || client.isPaused()
            || !client.mouseHandler.isMouseGrabbed() || player.isSpectator() || player.isUsingItem()
            || player.isHandsBusy() || client.options.keyUse.isDown()) {
            auraState = "Paused: return to the game without camera / item use";
            return;
        }
        LivingEntity nearest = null;
        double reach = Math.max(2, Math.min(6, config.pvpRange)), bestDistance = reach * reach;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity target) || target == player || !target.isAlive() || target.isRemoved()
                || !target.isAttackable() || target.isSpectator() || auraFriend(player, target)) continue;
            if (!((config.pvpTargetHostiles && target instanceof Enemy) || (auraTargetsPlayers() && target instanceof Player))) continue;
            double distance = player.distanceToSqr(target);
            if (distance >= bestDistance || !player.hasLineOfSight(target)
                || !player.isWithinAttackRange(player.getMainHandItem(), target.getBoundingBox(), 0)) continue;
            nearest = target;
            bestDistance = distance;
        }
        if (nearest == null) {
            auraState = "No visible eligible target within normal weapon reach";
            return;
        }
        if (ticks < auraNextAttack || (!config.pvpFastAttack&&(player.getAttackStrengthScale(0.5F) < 1 || player.cannotAttackWithItem(player.getMainHandItem(), 0)))) {
            auraState = "Waiting for attack cooldown · " + nearest.getName().getString(); return;
        }
        client.gameMode.attack(player, nearest);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
        player.connection.send(ServerboundPunchPacket.INSTANCE);
        auraNextAttack = ticks + (config.pvpFastAttack?1:Math.max(2, config.pvpDelayTicks));
        auraState = "Attack requested · " + nearest.getName().getString();
    }

    private boolean auraFriend(LocalPlayer player, Entity entity) {
        return dev.amethyst.finder.Friends.excludes(config,player,entity,"attack");
    }

    private void tickPlayerDetection(Minecraft client) {
        Set<UUID> current = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player other) || other == client.player || !other.isAlive() || other.isSpectator()) continue;
            current.add(other.getUUID());
            if (playerDetectionStarted && !nearbyPlayers.contains(other.getUUID()) && config.notifications) {
                int distance = Math.round(client.player.distanceTo(other));
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("[TUNG TUNG CLIENT] " + config.text("Рядом игрок: ", "Player nearby: ")
                    + other.getName().getString() + " · " + distance + " m"));
            }
        }
        nearbyPlayers.clear();
        nearbyPlayers.addAll(current);
        playerCount = current.size();
        playerDetectionStarted = true;
    }

    public String status(String id) {
        if (!capability(id)) return reason(id);
        if (!config.isModuleEnabled(id)) return "auto_sell".equals(id)&&farm.status(id).startsWith("Stopped:")?farm.status(id):config.text("Выключено", "Disabled");
        if (sessionPlayer == null) return config.text("Ожидание подключения к миру", "Waiting for a world");
        return switch (id) {
            case "freecam" -> freecamActive() ? "Camera active · " + CameraSpeed.blocksPerSecond(config.freecamSpeed(), false)
                    + " blocks/s · Ctrl boost ×3 · WASD / Space / Shift" : config.text("Ожидание", "Waiting");
            case "freelook" -> freelookActive() ? config.text("Свободный обзор · направление тела сохранено", "Free view · body aim unchanged") : config.text("На паузе во время Freecam", "Paused while Freecam is active");
            case "auto_sell", "auto_logout" -> farm.status(id);
            case "auto_totem" -> totemState;
            case "killaura" -> auraState;
            case "player_detection" -> config.text("Загруженных игроков рядом: ", "Loaded players nearby: ") + playerCount;
            default -> "";
        };
    }

    private static final class LockedInput extends ClientInput {
        @Override public void tick() { this.keyPresses = Input.EMPTY; }
        @Override public void makeJump() { /* A detached camera never jumps the body. */ }
    }
}
