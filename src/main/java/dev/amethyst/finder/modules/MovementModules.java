package dev.amethyst.finder.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLMouse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Real body/vehicle controls. Server permissions are never replaced with a claimed bypass. */
public final class MovementModules {
    private static final String[] IDS = {"flight", "noclip", "boat_fly", "fast_swim", "super_trident"};
    private static final float FLIGHT_SPEED = 0.08F;
    private static final double SWIM_SPEED = 0.32;
    private final FinderConfig config;
    private LocalPlayer sessionPlayer;
    private Object sessionLevel;
    private ClientFlight clientFlight;
    private ServerFlight serverFlight;
    private SpectatorLease spectator;
    private BoatLease boat;
    private ItemStack ownedTrident;
    private InteractionHand tridentHand;
    private int tridentSlot = -1;
    private boolean tridentKeyOwned;
    private int tridentCooldown;
    private String flightState = "", noclipState = "", boatState = "", swimState = "", tridentState = "";

    public MovementModules(FinderConfig config) { this.config = config; }

    public static boolean capability(String id) {
        return switch (id) {
            case "flight", "noclip", "boat_fly", "fast_swim", "super_trident" -> true;
            default -> false;
        };
    }

    public static String reason(String id) {
        return switch (id) {
            case "flight" -> "Moves your real body with WASD / Space / Shift. Singleplayer grants temporary flight; multiplayer needs server-granted flight permission.";
            case "noclip" -> "Moves your real body through walls using spectator mode. Singleplayer temporarily changes game mode; multiplayer needs existing spectator permission.";
            case "boat_fly" -> "Moves the boat you control with WASD / Space / sprint key (Ctrl by default) in singleplayer. Shift dismounts normally; remote servers need their own vehicle-flight support.";
            case "fast_swim" -> "Applies a 0.32-block/tick swimming speed floor with WASD / Space / Shift, preserving stronger movement and Riptide. Server validation remains authoritative.";
            case "super_trident" -> "Boosts your own Riptide release ×3. Charge and release the trident yourself; multiplayer movement remains server-controlled.";
            default -> "No server-specific movement bypass is implemented.";
        };
    }

    public boolean setEnabled(String id, boolean enabled) {
        if (!capability(id)) return false;
        if (enabled && "flight".equals(id)) {
            config.setModuleEnabled("noclip", false);
            releaseSpectator();
        }
        if (enabled && "noclip".equals(id)) {
            config.setModuleEnabled("flight", false);
            releaseFlight();
        }
        config.setModuleEnabled(id, enabled);
        if (enabled) {
            switch (id) {
                case "flight" -> flightState = "Waiting for the next game tick";
                case "noclip" -> noclipState = "Waiting for the next game tick";
                case "boat_fly" -> boatState = "Waiting for the next game tick";
                case "fast_swim" -> swimState = "Waiting for the next game tick";
                case "super_trident" -> {tridentState="Ready: use your Riptide trident";}
                default -> { }
            }
        }
        if (!enabled) {
            switch (id) {
                case "flight" -> releaseFlight();
                case "noclip" -> releaseSpectator();
                case "boat_fly" -> releaseBoat();
                case "super_trident" -> releaseTrident(Minecraft.getInstance());
                default -> { }
            }
        }
        config.save();
        return true;
    }

    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (sessionPlayer != null) reset(client);
            return;
        }
        if (client.player != sessionPlayer || client.level != sessionLevel) {
            if (sessionPlayer != null) reset(client);
            sessionPlayer = client.player;
            sessionLevel = client.level;
        }
        if (!client.player.isAlive()) {
            reset(client);
            return;
        }
        if (tridentCooldown > 0) tridentCooldown--;
        String pause = client.gui.screen() != null ? "Paused: close the Minecraft screen"
                : client.gui.overlay() != null ? "Paused: wait for the game overlay to close"
                : !client.isWindowActive() ? "Paused: focus the Minecraft window"
                : !client.mouseHandler.isMouseGrabbed() ? "Paused: return mouse control to Minecraft"
                : client.getCameraEntity() != client.player ? "Paused: return to your own player view"
                : ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.freecamActive()
                    ? "Paused: turn off Freecam to control your body" : "";
        if (!pause.isEmpty()) {
            releaseOwned(client);
            flightState = noclipState = boatState = swimState = tridentState = pause;
            return;
        }
        if (config.isModuleEnabled("noclip")) tickNoClip(client);
        else { releaseSpectator(); noclipState = ""; }
        if (config.isModuleEnabled("flight") && !config.isModuleEnabled("noclip")) tickFlight(client);
        else { releaseFlight(); flightState = config.isModuleEnabled("noclip") ? "Paused while NoClip is active" : ""; }
        if (config.isModuleEnabled("boat_fly")) tickBoat(client);
        else { releaseBoat(); boatState = ""; }
        if (config.isModuleEnabled("fast_swim")) tickSwim(client);
        else swimState = "";
        if (config.isModuleEnabled("super_trident")) tickTrident(client);
        else { releaseTrident(client); tridentState = ""; }
    }

    /** World/player/death transitions disable body controls and restore only state owned by this module. */
    public void reset(Minecraft client) {
        releaseOwned(client);
        sessionPlayer = null;
        sessionLevel = null;
        boolean changed = false;
        for (String id : IDS) {
            changed |= config.isModuleEnabled(id);
            config.setModuleEnabled(id, false);
        }
        if (changed) config.save();
        tridentCooldown = 0;
        flightState = noclipState = boatState = swimState = tridentState = "";
    }

    /** Runs before Minecraft halts/saves its integrated server; queued restoration must finish first. */
    public void beforeWorldExit(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        reset(client);
        if (server == null || server.isShutdown()) return;
        try {
            server.submit(() -> { }).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            ChunkFinderClient.LOGGER.warn("Movement restoration could not finish before world exit", ex);
        }
    }

    private void releaseOwned(Minecraft client) {
        releaseFlight();
        releaseSpectator();
        releaseBoat();
        releaseTrident(client);
    }

    private void tickFlight(Minecraft client) {
        LocalPlayer player = client.player;
        if (player.isPassenger() || player.isSpectator()) {
            releaseFlight();
            flightState = "Paused: leave the vehicle or spectator mode";
            return;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (serverFlight != null && serverFlight.prepared && serverFlight.owner == null) {
            serverFlight.restore();
            serverFlight = null;
        }
        if (server != null && serverFlight == null) {
            serverFlight = new ServerFlight(server, player.getUUID());
            serverFlight.start();
        }
        Abilities abilities = player.getAbilities();
        if (!abilities.mayfly) {
            releaseClientFlight();
            flightState = server != null ? "Waiting for singleplayer flight permission" : "Unavailable: this server has not granted flight permission";
            return;
        }
        if (clientFlight == null) clientFlight = serverFlight != null && serverFlight.prepared && serverFlight.owner != null
                ? new ClientFlight(player, serverFlight.oldFlying, serverFlight.oldSpeed)
                : new ClientFlight(player, abilities.flying, abilities.getFlyingSpeed());
        abilities.flying = true;
        abilities.setFlyingSpeed(FLIGHT_SPEED);
        if (!clientFlight.sent) {
            player.onUpdateAbilities();
            clientFlight.sent = true;
        }
        flightState = "Body flight active · WASD / Space / Shift";
    }

    private void tickNoClip(Minecraft client) {
        LocalPlayer player = client.player;
        if (player.isPassenger()) {
            releaseSpectator();
            noclipState = "Paused: leave the vehicle";
            return;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (spectator != null && spectator.prepared && spectator.owner == null) {
            spectator.restore();
            spectator = null;
        }
        if (server != null && spectator == null && !player.isSpectator()) {
            spectator = new SpectatorLease(server, player.getUUID());
            spectator.start();
        }
        noclipState = player.isSpectator()
                ? (spectator != null ? "Body NoClip active · temporary spectator mode · WASD / Space / Shift"
                        : "Body NoClip active · server-granted spectator mode · WASD / Space / Shift")
                : server != null ? "Waiting for singleplayer spectator mode" : "Unavailable: this server has not granted spectator mode";
    }

    private void tickBoat(Minecraft client) {
        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AbstractBoat) || vehicle.getControllingPassenger() != client.player) {
            releaseBoat();
            boatState = "Waiting: sit in the controlling seat of a boat";
            return;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            releaseBoat();
            boatState = "Unavailable: boat flight requires singleplayer server support";
            return;
        }
        if (boat != null && boat.prepared && boat.serverBoat == null) releaseBoat();
        if (boat == null || boat.clientBoat != vehicle) {
            releaseBoat();
            boat = new BoatLease(server, client.player.getUUID(), vehicle);
            boat.start();
        }
        if (boat.serverBoat == null) {
            boatState = "Waiting for singleplayer boat control";
            return;
        }
        // Sneak dismounts a boat in vanilla before END_CLIENT_TICK, so it cannot be descent.
        Vec3 direction = direction(client, false, client.options.keySprint.isDown());
        Vec3 velocity = new Vec3(direction.x * 0.28, direction.y * 0.18, direction.z * 0.28);
        vehicle.setNoGravity(true);
        vehicle.setDeltaMovement(velocity);
        boat.move(velocity);
        boatState = "Body boat flight active · WASD / Space / sprint key (Ctrl default) · Shift dismounts";
    }

    private void tickSwim(Minecraft client) {
        LocalPlayer player = client.player;
        if (player.isPassenger() || player.isSpectator() || player.getAbilities().flying) {
            swimState = "Paused: swim on foot without flight or spectator mode";
            return;
        }
        if (!player.isInWater()) {
            swimState = "Waiting: enter water on foot";
            return;
        }
        if (player.isAutoSpinAttack() || player.isUsingItem() || player.isHandsBusy()) {
            swimState = "Paused: preserving Riptide or the current item action";
            return;
        }
        Vec3 direction = direction(client, true);
        if (direction.lengthSqr() > 0) {
            Vec3 before = player.getDeltaMovement();
            Vec3 unit = direction.normalize();
            if (before.dot(unit) >= SWIM_SPEED) {
                swimState = "Ready: existing movement exceeds the swimming boost floor";
                return;
            }
            Vec3 velocity = unit.scale(SWIM_SPEED);
            // Do not cap faster sprint/Depth Strider/Dolphin's Grace momentum.
            if (before.x * before.x + before.z * before.z > velocity.x * velocity.x + velocity.z * velocity.z
                    && before.x * unit.x + before.z * unit.z > 0)
                velocity = new Vec3(before.x, velocity.y, before.z);
            if (Math.abs(unit.y) < 0.001 || before.y * unit.y > velocity.y * unit.y)
                velocity = new Vec3(velocity.x, before.y, velocity.z);
            player.setDeltaMovement(velocity);
            swimState = "Swimming boost active · 0.32 blocks/tick floor · server controls movement";
        } else swimState = "Ready: swim with WASD / Space / Shift";
    }

    private void tickTrident(Minecraft client) {
        tridentState=client.player.isAutoSpinAttack()?"Manual Riptide active · launch boosted ×3":"Ready: charge and release your Riptide trident";
    }

    private static InteractionHand riptideHand(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(Items.TRIDENT) && !stack.nextDamageWillBreak()
                    && EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0) return hand;
        }
        return null;
    }

    private static Vec3 direction(Minecraft client, boolean pitch) {
        return direction(client, pitch, client.options.keyShift.isDown());
    }

    private static Vec3 direction(Minecraft client, boolean pitch, boolean descend) {
        double forward = (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
        double side = (client.options.keyLeft.isDown() ? 1 : 0) - (client.options.keyRight.isDown() ? 1 : 0);
        double up = (client.options.keyJump.isDown() ? 1 : 0) - (descend ? 1 : 0);
        double yaw = Math.toRadians(client.player.getYRot()), tilt = pitch ? Math.toRadians(client.player.getXRot()) : 0;
        Vec3 result = new Vec3(-Math.sin(yaw) * Math.cos(tilt) * forward + Math.cos(yaw) * side,
                -Math.sin(tilt) * forward + up, Math.cos(yaw) * Math.cos(tilt) * forward + Math.sin(yaw) * side);
        return result.lengthSqr() > 1 ? result.normalize() : result;
    }

    private void releaseClientFlight() {
        if (clientFlight != null) { clientFlight.restore(); clientFlight = null; }
    }
    private void releaseFlight() {
        releaseClientFlight();
        if (serverFlight != null) { serverFlight.restore(); serverFlight = null; }
    }
    private void releaseSpectator() {
        if (spectator != null) { spectator.restore(); spectator = null; }
    }
    private void releaseBoat() {
        if (boat != null) { boat.restore(); boat = null; }
    }
    private void releaseTrident(Minecraft client) {
        if (sessionPlayer != null && ownsTridentUse(sessionPlayer)) {
            if (client.player == sessionPlayer && client.gameMode != null) client.gameMode.releaseUsingItem(sessionPlayer);
            else sessionPlayer.stopUsingItem();
        }
        clearTridentOwnership(client);
    }

    private void clearTridentOwnership(Minecraft client) {
        restoreTridentKey(client);
        ownedTrident = null;
        tridentHand = null;
        tridentSlot = -1;
    }

    private boolean ownsTridentUse(LocalPlayer player) {
        return ownedTrident != null && player.isUsingItem() && player.getUsedItemHand() == tridentHand
                && ItemStack.isSameItemSameComponents(player.getUseItem(), ownedTrident)
                && ItemStack.isSameItemSameComponents(player.getItemInHand(tridentHand), ownedTrident)
                && (tridentHand == InteractionHand.OFF_HAND || player.getInventory().getSelectedSlot() == tridentSlot);
    }

    private void restoreTridentKey(Minecraft client) {
        if (tridentKeyOwned && client.options.keyUse.isDown()) {
            boolean gameplay = client.gui.screen() == null && client.gui.overlay() == null
                    && client.isWindowActive() && client.mouseHandler.isMouseGrabbed();
            client.options.keyUse.setDown(gameplay && physicalUseDown(client));
        }
        tridentKeyOwned = false;
    }

    private static boolean inventoryReserved(Minecraft client) {
        return (ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.totemSwapPending())
                || (ChunkFinderClient.PVP != null && (ChunkFinderClient.PVP.ownsInventory()
                    || ChunkFinderClient.PVP.blocksManualRepeat(client)));
    }

    /** Minecraft 26.3 uses SDL: keyboard values are scancodes and mouse buttons are one-based. */
    private static boolean physicalUseDown(Minecraft client) {
        try {
            InputConstants.Key key = InputConstants.getKey(client.options.keyUse.saveString());
            if (key.equals(InputConstants.UNKNOWN)) return false;
            if (key.getType() == InputConstants.Type.KEYBOARD) return InputConstants.isKeyDown(key.getValue());
            int button = key.getValue();
            return key.getType() == InputConstants.Type.MOUSE && button >= 1 && button <= 31
                    && (SDLMouse.nSDL_GetMouseState(0L, 0L) & (1 << (button - 1))) != 0;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public String status(String id) {
        if (!capability(id)) return reason(id);
        if (!config.isModuleEnabled(id)) return "Disabled";
        if (sessionPlayer == null) return "Waiting for a world";
        Minecraft client = Minecraft.getInstance();
        if (client.player != sessionPlayer || client.level != sessionLevel) return "Waiting for the current world";
        // Permanent server prerequisites must remain readable while the desktop pauses input.
        if (client.getSingleplayerServer() == null) {
            if (id.equals("flight") && !sessionPlayer.getAbilities().mayfly)
                return "Unavailable: this server has not granted flight permission";
            if (id.equals("noclip") && !sessionPlayer.isSpectator())
                return "Unavailable: this server has not granted spectator mode";
            if (id.equals("boat_fly"))
                return "Unavailable: boat flight requires singleplayer server support";
        }
        String state = switch (id) {
            case "flight" -> flightState;
            case "noclip" -> noclipState;
            case "boat_fly" -> boatState;
            case "fast_swim" -> swimState;
            case "super_trident" -> tridentState;
            default -> "";
        };
        return state.isEmpty() ? "Waiting for the next game tick" : state;
    }

    /** Stable UI state, independent from the enabled preference. No empty status means active. */
    public String runtimeState(String id) {
        if (!capability(id)) return "unavailable";
        if (!config.isModuleEnabled(id)) return "disabled";
        String state = status(id);
        if (state.startsWith("Paused:")) return "paused";
        if (state.startsWith("Unavailable:")) return "unavailable";
        if (state.startsWith("Waiting")) return "waiting";
        if (state.startsWith("Ready:")) return "ready";
        if ((state.startsWith("Body ") && state.contains(" active"))
                || state.startsWith("Swimming boost active") || state.startsWith("Charging Riptide")
                || state.startsWith("Riptide released")) return "active";
        return "waiting";
    }

    private static final class ClientFlight {
        final LocalPlayer player;
        final boolean flying;
        final float speed;
        final GameType mode;
        boolean sent;
        ClientFlight(LocalPlayer player, boolean originalFlying, float originalSpeed) {
            this.player = player;
            flying = originalFlying;
            speed = originalSpeed;
            mode = player.gameMode();
        }
        void restore() {
            if (player.gameMode() != mode) return;
            Abilities abilities = player.getAbilities();
            if (abilities.flying) abilities.flying = flying && abilities.mayfly;
            if (abilities.getFlyingSpeed() == FLIGHT_SPEED) abilities.setFlyingSpeed(speed);
            if (player == Minecraft.getInstance().player && player.isAlive()) player.onUpdateAbilities();
        }
    }

    /** Captures/restores abilities only on the authoritative integrated server thread. */
    private static final class ServerFlight {
        final IntegratedServer server;
        final UUID playerId;
        volatile boolean released;
        volatile boolean prepared;
        volatile ServerPlayer owner;
        GameType mode;
        boolean oldMayfly, oldFlying;
        float oldSpeed;
        ServerFlight(IntegratedServer server, UUID playerId) { this.server = server; this.playerId = playerId; }
        void start() {
            server.execute(() -> {
                if (released) { prepared = true; return; }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive()) { prepared = true; return; }
                owner = player;
                mode = player.gameMode();
                Abilities abilities = player.getAbilities();
                oldMayfly = abilities.mayfly;
                oldFlying = abilities.flying;
                oldSpeed = abilities.getFlyingSpeed();
                abilities.mayfly = abilities.flying = true;
                abilities.setFlyingSpeed(FLIGHT_SPEED);
                player.onUpdateAbilities();
                prepared = true;
            });
        }
        void restore() {
            released = true;
            server.execute(() -> {
                if (owner == null || owner.gameMode() != mode) return;
                Abilities abilities = owner.getAbilities();
                if (abilities.mayfly) abilities.mayfly = oldMayfly;
                if (abilities.flying) abilities.flying = oldFlying;
                if (abilities.getFlyingSpeed() == FLIGHT_SPEED) abilities.setFlyingSpeed(oldSpeed);
                owner.onUpdateAbilities();
            });
        }
    }

    private static final class SpectatorLease {
        final IntegratedServer server;
        final UUID playerId;
        volatile boolean released;
        volatile boolean prepared;
        volatile ServerPlayer owner;
        GameType original;
        boolean oldFlying, oldMayfly, oldInstabuild, oldInvulnerable, oldMayBuild;
        SpectatorLease(IntegratedServer server, UUID playerId) { this.server = server; this.playerId = playerId; }
        void start() {
            server.execute(() -> {
                if (released) { prepared = true; return; }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || player.isSpectator()) { prepared = true; return; }
                owner = player;
                original = player.gameMode();
                Abilities abilities = player.getAbilities();
                oldFlying = abilities.flying;
                oldMayfly = abilities.mayfly;
                oldInstabuild = abilities.instabuild;
                oldInvulnerable = abilities.invulnerable;
                oldMayBuild = abilities.mayBuild;
                player.setGameMode(GameType.SPECTATOR);
                prepared = true;
            });
        }
        void restore() {
            released = true;
            server.execute(() -> {
                if (owner == null || owner.gameMode() != GameType.SPECTATOR || original == null) return;
                Abilities abilities = owner.getAbilities();
                boolean restoreFlying = abilities.flying;
                boolean restoreMayfly = abilities.mayfly;
                boolean restoreInstabuild = !abilities.instabuild;
                boolean restoreInvulnerable = abilities.invulnerable;
                boolean restoreMayBuild = !abilities.mayBuild;
                boolean externalFlying = abilities.flying, externalMayfly = abilities.mayfly;
                boolean externalInstabuild = abilities.instabuild, externalInvulnerable = abilities.invulnerable;
                boolean externalMayBuild = abilities.mayBuild;
                owner.setGameMode(original);
                abilities.flying = restoreFlying ? oldFlying : externalFlying;
                abilities.mayfly = restoreMayfly ? oldMayfly : externalMayfly;
                abilities.instabuild = restoreInstabuild ? oldInstabuild : externalInstabuild;
                abilities.invulnerable = restoreInvulnerable ? oldInvulnerable : externalInvulnerable;
                abilities.mayBuild = restoreMayBuild ? oldMayBuild : externalMayBuild;
                owner.onUpdateAbilities();
            });
        }
    }

    private static final class BoatLease {
        final IntegratedServer server;
        final UUID playerId, vehicleId;
        final Entity clientBoat;
        final boolean oldClientGravity;
        volatile boolean released;
        volatile boolean prepared;
        volatile Entity serverBoat;
        boolean oldServerGravity;
        BoatLease(IntegratedServer server, UUID playerId, Entity clientBoat) {
            this.server = server; this.playerId = playerId; this.clientBoat = clientBoat;
            vehicleId = clientBoat.getUUID(); oldClientGravity = clientBoat.isNoGravity();
        }
        void start() {
            server.execute(() -> {
                if (released) { prepared = true; return; }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity vehicle = player == null ? null : player.getVehicle();
                if (vehicle == null || !vehicle.getUUID().equals(vehicleId) || vehicle.getControllingPassenger() != player) { prepared = true; return; }
                serverBoat = vehicle;
                oldServerGravity = vehicle.isNoGravity();
                vehicle.setNoGravity(true);
                prepared = true;
            });
        }
        void move(Vec3 velocity) {
            server.execute(() -> {
                if (released || serverBoat == null) return;
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.getVehicle() != serverBoat || serverBoat.getControllingPassenger() != player) return;
                serverBoat.setDeltaMovement(velocity);
            });
        }
        void restore() {
            released = true;
            if (clientBoat.isNoGravity()) clientBoat.setNoGravity(oldClientGravity);
            server.execute(() -> {
                if (serverBoat != null && serverBoat.isNoGravity()) serverBoat.setNoGravity(oldServerGravity);
            });
        }
    }
}
