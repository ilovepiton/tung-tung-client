package dev.amethyst.finder.modules;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.Friends;
import dev.amethyst.finder.modules.pvp.ArsenalBot;
import dev.amethyst.finder.modules.pvp.PvpAccess;
import dev.amethyst.finder.modules.pvp.PvpAcknowledgements;
import dev.amethyst.finder.modules.pvp.ManualCombatAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;

/** PvP helpers using ordinary game interactions, bounded action rates and explicit runtime failures. */
public final class PvpModules {
    private static final List<String> IDS = List.of("mace_bot", "combat_bot", "auto_crystal", "auto_anchor", "auto_gapple", "trigger_bot", "auto_weapon", "aim_cursor", "auto_punch", "auto_kill_steal");
    private static final String[] WEAPONS = {"NETHERITE_SWORD", "NETHERITE_AXE", "DIAMOND_SWORD", "DIAMOND_AXE", "IRON_SWORD", "IRON_AXE", "COPPER_SWORD", "COPPER_AXE", "STONE_SWORD", "STONE_AXE", "WOODEN_SWORD", "WOODEN_AXE", "GOLDEN_SWORD", "GOLDEN_AXE"};
    private final FinderConfig config;
    private final PvpAccess access = new PvpAccess();
    private final ManualCombatAssist manual;
    private final dev.amethyst.finder.modules.pvp.BotCombatPlanner botPlanner;
    private int ownedInteractionDepth;
    private final Map<String, String> states = new HashMap<>();
    private final Map<String, Integer> failedUntil = new HashMap<>();
    private LocalPlayer sessionPlayer;
    private Object sessionLevel;
    private int blastBudget;
    private boolean anchorTurn;
    private int ticks, nextActionTick, nextExplosionTick, lastCrystalId = -1, lastCrystalTick = -100;
    private Spot lastAnchor;
    private int lastAnchorTick = -100;
    private AnchorPlan anchor;
    private CrystalPlan crystal;
    private boolean crystalAttackPending;
    private long crystalRemovalRevision;
    private String pause = "Waiting for a world";
    private boolean eating;
    private int foodSlot = -1, foodPreviousSlot = -1, foodStarted, foodCount;
    private long foodInventoryRevision;
    private int weaponSlot = -1, weaponPreviousSlot = -1;

    public PvpModules(FinderConfig config) { this.config = config; this.manual = new ManualCombatAssist(config, this);this.botPlanner=new dev.amethyst.finder.modules.pvp.BotCombatPlanner(config,this); }
    public static boolean capability(String id) { return IDS.contains(id); }
    public boolean setEnabled(String id, boolean enabled) {
        if (!capability(id)) return false;
        if("mace_bot".equals(id)){ChunkFinderClient.MACE.setEnabled(Minecraft.getInstance(),enabled);config.save();return true;}
        if("combat_bot".equals(id)){if(enabled&&ChunkFinderClient.MACE.enabled())ChunkFinderClient.MACE.setEnabled(Minecraft.getInstance(),false);ChunkFinderClient.BOT.setEnabled(Minecraft.getInstance(),enabled);config.save();return true;}
        config.setModuleEnabled(id, enabled);
        states.put(id, enabled ? "Waiting for a valid interaction" : "Disabled");
        failedUntil.remove(id);
        if (!enabled && "auto_gapple".equals(id)) stopEating(Minecraft.getInstance());
        if (!enabled && "auto_weapon".equals(id)) restoreWeapon(Minecraft.getInstance());
        if (!enabled && "auto_anchor".equals(id)) anchor = null;
        if (!enabled && "auto_crystal".equals(id)) { crystal = null; crystalAttackPending = false; }
        if (enabled) manual.enabled(id); else manual.disabled(Minecraft.getInstance(), id);
        config.save();
        return true;
    }
    public void clearBotPlans() {
        botPlanner.reset();
        anchor=null;crystal=null;crystalAttackPending=false;nextExplosionTick=nextActionTick=0;
        manual.reset(Minecraft.getInstance());stopEating(Minecraft.getInstance());restoreWeapon(Minecraft.getInstance());
    }
    public void reset() {
        Minecraft client = Minecraft.getInstance();
        manual.reset(client);
        stopEating(client); restoreWeapon(client);
        sessionPlayer = null; sessionLevel = null; anchor = null; lastAnchor = null; crystal = null; crystalAttackPending = false;
        ticks = nextActionTick = nextExplosionTick = 0; lastCrystalId = -1; lastCrystalTick = lastAnchorTick = -100;
        states.clear(); failedUntil.clear(); pause = "Waiting for a world";
    }
    public String status(String id) {
        if (!capability(id)) return "Not implemented by the PvP service";
        if("mace_bot".equals(id))return ChunkFinderClient.MACE.status();
        if("combat_bot".equals(id))return ChunkFinderClient.BOT.status();
        if (!config.isModuleEnabled(id)) return "Disabled";
        if (("auto_anchor".equals(id)||"auto_crystal".equals(id))&&dev.amethyst.finder.modules.pvp.LootProtection.blocked(Minecraft.getInstance()))
            return dev.amethyst.finder.modules.pvp.LootProtection.status(Minecraft.getInstance());
        if (config.pvpManualAssist && ("auto_anchor".equals(id) || "auto_crystal".equals(id))) {
            String detail = manual.status(id);
            return pause.isEmpty() || detail.contains(pause) ? detail : detail + " · " + pause;
        }
        return pause.isEmpty() ? states.getOrDefault(id, "Waiting for a valid interaction") : pause;
    }
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) { if (sessionPlayer != null) reset(); return; }
        if (sessionPlayer != client.player || sessionLevel != client.level) {
            reset(); sessionPlayer = client.player; sessionLevel = client.level;
        }
        ticks++;
        if(ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled())return;
        if (!client.player.isAlive() || client.player.isSpectator()) pause = "Paused: player unavailable";
        else if (client.player.isHandsBusy()) pause = "Paused: player hands are busy";
        else if (client.gui.screen() != null) pause = "Paused: close the game screen";
        else if (!client.isWindowActive()) pause = "Paused: Minecraft window is not focused";
        else if (!client.mouseHandler.isMouseGrabbed()) pause = "Paused: return mouse focus to Minecraft";
        else if (ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.cameraActive()) pause = "Paused: detached camera active";
        else pause = "";
        if (!pause.isEmpty()) { if(ChunkFinderClient.BOT.enabled())ChunkFinderClient.BOT.pauseCombat(); manual.paused(client, pause); stopEating(client); restoreWeapon(client); anchor = null; crystal = null; crystalAttackPending = false; return; }
        if (ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.totemSwapPending()) {
            stopEating(client); restoreWeapon(client); setCombatPause("Paused: waiting for the server totem swap"); return;
        }
        if(ChunkFinderClient.BOT.enabled()) {
            if(ticks<failedUntil.getOrDefault("combat_bot",0)){ChunkFinderClient.BOT.problem(states.getOrDefault("combat_bot","Paused after a runtime error"));return;}
            manual.paused(client,"Combat Bot owns combat");
            run("combat_bot",()->tickBot(client));return;
        }
        if(dev.amethyst.finder.modules.pvp.LootProtection.blocked(client)) {
            manual.paused(client,dev.amethyst.finder.modules.pvp.LootProtection.status(client));
            anchor=null;crystal=null;crystalAttackPending=false;
        } else manual.tick(client);
        if (manual.hasWork()) { stopEating(client); restoreWeapon(client); setCombatPause("Paused: manual Anchor/Crystal assist owns combat actions"); return; }
        if (config.pvpManualAssist) { anchor = null; crystal = null; crystalAttackPending = false; }
        if (!config.isModuleEnabled("auto_gapple") && eating) stopEating(client);
        if (config.isModuleEnabled("auto_gapple")) run("auto_gapple", () -> tickGapple(client));
        if (eating) { restoreWeapon(client); setCombatPause("Paused: consuming a golden apple"); return; }
        if (client.player.isUsingItem() || client.options.keyUse.isDown()) {
            restoreWeapon(client); setCombatPause("Paused: manual item use"); return;
        }
        if (config.isModuleEnabled("aim_cursor")) {
            if (config.isModuleEnabled("auto_kill_steal") && playerTarget(client, true) != null) states.put("aim_cursor", "Paused: Auto Kill Steal owns target aim");
            else run("aim_cursor", () -> tickAim(client));
        }
        if (config.isModuleEnabled("auto_weapon")) run("auto_weapon", () -> tickWeapon(client));
        else restoreWeapon(client);
        if (ticks < nextActionTick) return;
        boolean acted = false;
        if (!config.pvpManualAssist && ticks >= nextExplosionTick && (config.isModuleEnabled("auto_crystal") || config.isModuleEnabled("auto_anchor"))) {
            nextExplosionTick = ticks + delay();
            blastBudget = 12;
            if (anchorTurn && config.isModuleEnabled("auto_anchor")) {
                acted = run("auto_anchor", () -> tickAnchor(client));
                if (!acted && config.isModuleEnabled("auto_crystal")) acted = run("auto_crystal", () -> tickCrystal(client));
            } else {
                acted = config.isModuleEnabled("auto_crystal") && run("auto_crystal", () -> tickCrystal(client));
                if (!acted && config.isModuleEnabled("auto_anchor")) acted = run("auto_anchor", () -> tickAnchor(client));
            }
            anchorTurn = !anchorTurn;
        }
        if (!acted && config.isModuleEnabled("auto_kill_steal")) acted = run("auto_kill_steal", () -> tickKillSteal(client));
        if (!acted && config.isModuleEnabled("auto_punch")) acted = run("auto_punch", () -> tickCrosshairAttack(client, "auto_punch"));
        if (!acted && config.isModuleEnabled("trigger_bot")) acted = run("trigger_bot", () -> tickTrigger(client));
        if (acted) nextActionTick = ticks + (config.pvpFastAttack ? 1 : delay());
    }
    public boolean blocksManualRepeat(Minecraft client) { return ownedInteractionDepth == 0
            && !dev.amethyst.finder.modules.pvp.LootProtection.blocked(client) && manual.blocksRepeat(client); }
    public boolean ownsInventory() { return manual.ownsInventory()||(ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.ownsInventory()); }
    public boolean reservesInventorySlot(int slot) { return manual.reservesInventorySlot(slot)
            || ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.reservesInventorySlot(slot); }
    public boolean ownsCombat() {
        return (ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled()) || (ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled()) || manual.hasWork() || anchor != null || crystal != null || crystalAttackPending || eating
                || config.isModuleEnabled("auto_punch") || config.isModuleEnabled("auto_kill_steal")
                || (ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.totemSwapPending());
    }
    public void onManualMouseButton(Minecraft client, int button, int action) { manual.mouseButton(client, button, action); }
    public void onServerCombatUpdate(Minecraft client) {
        if(dev.amethyst.finder.modules.pvp.LootProtection.blocked(client)) {
            manual.paused(client,dev.amethyst.finder.modules.pvp.LootProtection.status(client));return;
        }
        if((ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled())||(ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled()))return;
        if(client.player==null||client.level==null||client.gameMode==null||!client.player.isAlive()||client.player.isSpectator()
                ||client.player.isHandsBusy()||client.gui.screen()!=null||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed())return;
        if(ChunkFinderClient.MODULES!=null&&(ChunkFinderClient.MODULES.cameraActive()||ChunkFinderClient.MODULES.totemSwapPending()))return;
        manual.received(client);
    }
    public ManualCombatAssist.Observation captureManualUse(Minecraft client, InteractionHand hand, BlockHitResult hit, ItemStack held) {
        if (ownedInteractionDepth != 0 || client.player == null || client.level == null || ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled()) return null;
        // Safe anchor building/charging stays vanilla while explosive helpers
        // are paused. Do not create a combo or latch the user's next click.
        if (dev.amethyst.finder.modules.pvp.LootProtection.blocked(client)) return null;
        if (sessionPlayer != client.player || sessionLevel != client.level) { reset(); sessionPlayer = client.player; sessionLevel = client.level; }
        return manual.capture(client, hand, hit, held);
    }
    public void onManualBlockUse(Minecraft client, ManualCombatAssist.Observation observation, boolean accepted) {
        if (ownedInteractionDepth != 0 || !accepted || observation == null || ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled()) return;
        stopEating(client); restoreWeapon(client);
        manual.observed(client, observation, true);
    }
    public boolean assistedClick(Minecraft client, Object hit, InteractionHand hand) throws ReflectiveOperationException {
        ownedInteractionDepth++;
        try { return access.click(client, hit, hand); }
        finally { ownedInteractionDepth--; }
    }
    public void assistedAttack(Minecraft client, Entity entity) { attack(client, entity); }
    public double manualSelfDamage(Minecraft client, Vec3 center, double power, BlockPos removedAnchor) throws ReflectiveOperationException {
        return Math.ceil(rawDamage(client, center, client.player, power, removedAnchor)) * 1.5;
    }
    public String manualFriendAtRisk(Minecraft client, Vec3 center, double power, BlockPos removedAnchor) throws ReflectiveOperationException {
        for (Entity entity : client.level.entitiesForRendering())
            if (entity instanceof Player && entity != client.player && entity.isAlive() && friend(entity)
                    && rawDamage(client, center, entity, power, removedAnchor) > 1) return entity.getName().getString();
        return null;
    }
    private void setCombatPause(String value) {
        for (String id : IDS) if (!"auto_gapple".equals(id) && config.isModuleEnabled(id)) states.put(id, value);
    }
    private boolean run(String id, Action action) {
        if (ticks < failedUntil.getOrDefault(id, 0)) return false;
        try { return action.run(); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            states.put(id, "Paused: API/action unavailable · " + detail.substring(0, Math.min(detail.length(), 150)));
            failedUntil.put(id, ticks + 40);
            if("combat_bot".equals(id))ChunkFinderClient.BOT.problem(states.get(id));
            return false;
        }
    }
    private int delay() { return Math.max(2, config.pvpDelayTicks); }
    private double range() { return Math.max(2, Math.min(6, config.pvpRange)); }
    private boolean friend(Entity entity) {return Friends.excludes(config,sessionPlayer,entity,ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled()?"bot":"explosions");}
    private boolean target(Entity entity) {return target(entity,"attack");}
    private boolean target(Entity entity,String protection) {
        return entity instanceof LivingEntity && entity != sessionPlayer && entity.isAlive() && !entity.isRemoved()
                && !entity.isSpectator() && entity.isAttackable() && !Friends.excludes(config,sessionPlayer,entity,protection)
                && ((config.pvpTargetPlayers && entity instanceof Player) || (config.pvpTargetHostiles && entity instanceof Enemy))
                && squared(entity.getX() - sessionPlayer.getX(), entity.getY() - sessionPlayer.getY(), entity.getZ() - sessionPlayer.getZ()) <= range() * range();
    }
    private LivingEntity nearest(Minecraft client) {
        if(ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled()) {
            Player p=ChunkFinderClient.BOT.target();return p!=null&&sessionPlayer.distanceToSqr(p)<=range()*range()?p:null;
        }
        LivingEntity best = null; double distance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) if (target(entity,"explosions")) {
            double d = sessionPlayer.distanceToSqr(entity);
            if (d < distance) { best = (LivingEntity) entity; distance = d; }
        }
        return best;
    }
    private int slot(String item) throws ReflectiveOperationException {
        for (int i = 0; i < 9; i++) if (access.item(sessionPlayer.getInventory().getItem(i), item)) return i;
        return -1;
    }
    private boolean withSlot(int selected, Action action) throws ReflectiveOperationException {
        int previous = sessionPlayer.getInventory().getSelectedSlot();
        try { sessionPlayer.getInventory().setSelectedSlot(selected); return action.run(); }
        finally { if (sessionPlayer.getInventory().getSelectedSlot() == selected) sessionPlayer.getInventory().setSelectedSlot(previous); }
    }
    private void attack(Minecraft client, Entity entity) {
        client.gameMode.attack(sessionPlayer, entity);
        sessionPlayer.swing(InteractionHand.MAIN_HAND, sessionPlayer.getMainHandItem().getAttackAnimation(), false);
        sessionPlayer.connection.send(ServerboundPunchPacket.INSTANCE);
    }
    private boolean tickTrigger(Minecraft client) throws ReflectiveOperationException {
        return tickCrosshairAttack(client, "trigger_bot");
    }
    private boolean tickCrosshairAttack(Minecraft client, String id) throws ReflectiveOperationException {
        Entity hit = access.crosshairEntity(client);
        if (hit == null || !target(hit) || !sessionPlayer.hasLineOfSight(hit)
                || !sessionPlayer.isWithinEntityInteractionRange(hit, 0)
                || !sessionPlayer.isWithinAttackRange(sessionPlayer.getMainHandItem(), hit.getBoundingBox(), 0)) {
            states.put(id, "Aim at an eligible target within visible normal weapon reach"); return false;
        }
        if (!config.pvpFastAttack && (sessionPlayer.getAttackStrengthScale(0.5F) < 1 || sessionPlayer.cannotAttackWithItem(sessionPlayer.getMainHandItem(), 0))) {
            states.put(id, "Waiting for full attack cooldown"); return false;
        }
        attack(client, hit); states.put(id, (config.pvpFastAttack?"Fast attack · one request per game tick: ":"Cooldown attack: ") + hit.getName().getString()); return true;
    }
    private boolean visiblePlayer(Entity entity) {
        return entity instanceof Player && target(entity) && sessionPlayer.hasLineOfSight(entity)
                && sessionPlayer.isWithinEntityInteractionRange(entity, 0)
                && sessionPlayer.isWithinAttackRange(sessionPlayer.getMainHandItem(), entity.getBoundingBox(), 0);
    }
    private LivingEntity playerTarget(Minecraft client, boolean lowestHealth) {
        LivingEntity best = null;
        for (Entity entity : client.level.entitiesForRendering()) if (visiblePlayer(entity)) {
            LivingEntity candidate = (LivingEntity) entity;
            if (best == null || (lowestHealth && effectiveHealth(candidate) < effectiveHealth(best))
                    || ((!lowestHealth || effectiveHealth(candidate) == effectiveHealth(best))
                    && sessionPlayer.distanceToSqr(candidate) < sessionPlayer.distanceToSqr(best))) best = candidate;
        }
        return best;
    }
    private static float effectiveHealth(LivingEntity entity) { return entity.getHealth() + entity.getAbsorptionAmount(); }
    private void aimAt(LivingEntity entity) {
        Vec3 direction = entity.getEyePosition().subtract(sessionPlayer.getEyePosition());
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        sessionPlayer.setYRot((float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90));
        sessionPlayer.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, horizontal)));
    }
    private double aimRange() {
        return Double.isFinite(config.aimCursorRange) ? Math.clamp(config.aimCursorRange, 3, 256) : 64;
    }
    private LivingEntity aimTarget(Minecraft client) {
        if (!config.pvpTargetPlayers) return null;
        LivingEntity best = null;
        double nearest = aimRange() * aimRange();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player) || entity == sessionPlayer || !entity.isAlive() || entity.isRemoved()
                    || entity.isSpectator() || !entity.isAttackable()) continue;
            double distance = sessionPlayer.distanceToSqr(entity);
            if (distance > nearest || Friends.excludes(config,sessionPlayer,entity,"aim") || !sessionPlayer.hasLineOfSight(entity)) continue;
            best = (LivingEntity) entity; nearest = distance;
        }
        return best;
    }
    private boolean tickAim(Minecraft client) {
        LivingEntity target = aimTarget(client);
        if (target == null) { states.put("aim_cursor", "No visible eligible received player within " + aimRange() + " blocks"); return false; }
        aimAt(target);
        states.put("aim_cursor", String.format(Locale.ROOT, "Aiming at nearest visible player: %s · %.1f blocks / view range %.0f",
                target.getName().getString(), Math.sqrt(sessionPlayer.distanceToSqr(target)), aimRange()));
        return false;
    }
    private boolean tickKillSteal(Minecraft client) {
        LivingEntity target = playerTarget(client, true);
        if (target == null) { states.put("auto_kill_steal", "No visible eligible player within normal weapon reach"); return false; }
        if (!config.pvpFastAttack && (sessionPlayer.getAttackStrengthScale(0.5F) < 1 || sessionPlayer.cannotAttackWithItem(sessionPlayer.getMainHandItem(), 0))) {
            states.put("auto_kill_steal", "Waiting for cooldown · lowest target " + effectiveHealth(target) + " HP"); return false;
        }
        aimAt(target); attack(client, target);
        states.put("auto_kill_steal", "Attack requested for lowest-health visible player: " + target.getName().getString()); return true;
    }
    private boolean tickWeapon(Minecraft client) throws ReflectiveOperationException {
        Entity hit = access.crosshairEntity(client);
        if (hit == null || !target(hit)) { restoreWeapon(client); states.put("auto_weapon", "Aim at an eligible target"); return false; }
        if (weaponSlot >= 0 && sessionPlayer.getInventory().getSelectedSlot() != weaponSlot) { weaponSlot = weaponPreviousSlot = -1; }
        int best = -1, bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) for (int rank = 0; rank < WEAPONS.length; rank++) {
            if (rank < bestRank && access.item(sessionPlayer.getInventory().getItem(i), WEAPONS[rank])) { best = i; bestRank = rank; }
        }
        if (best < 0) { restoreWeapon(client); states.put("auto_weapon", "Put a stock sword or axe in the hotbar"); return false; }
        if (weaponPreviousSlot < 0) weaponPreviousSlot = sessionPlayer.getInventory().getSelectedSlot();
        weaponSlot = best; sessionPlayer.getInventory().setSelectedSlot(best);
        states.put("auto_weapon", "Selected " + WEAPONS[bestRank].toLowerCase(Locale.ROOT).replace('_', ' ') + " · enchants not ranked"); return false;
    }
    private void restoreWeapon(Minecraft client) {
        if (weaponSlot >= 0 && weaponPreviousSlot >= 0 && client.player == sessionPlayer && sessionPlayer.getInventory().getSelectedSlot() == weaponSlot)
            sessionPlayer.getInventory().setSelectedSlot(weaponPreviousSlot);
        weaponSlot = weaponPreviousSlot = -1;
    }
    private boolean tickGapple(Minecraft client) throws ReflectiveOperationException {return tickGapple(client,config.pvpTotemHealth);}
    private boolean tickGapple(Minecraft client,double threshold) throws ReflectiveOperationException {
        if (eating) {
            ItemStack held = sessionPlayer.getInventory().getItem(foodSlot);
            if (sessionPlayer.getInventory().getSelectedSlot() != foodSlot || ticks - foodStarted > 80
                    || held.getCount() < foodCount || (ticks - foodStarted > 3 && !sessionPlayer.isUsingItem())) {
                boolean observed = PvpAcknowledgements.inventoryRevision(client, foodSlot) > foodInventoryRevision
                        && PvpAcknowledgements.inventorySnapshot(client, foodSlot).getCount() < foodCount;
                stopEating(client); nextActionTick = ticks + delay();
                states.put("auto_gapple", observed ? "Golden apple consumption observed in server inventory" : "Item use finished or interrupted · awaiting inventory confirmation");
            } else { client.options.keyUse.setDown(true); states.put("auto_gapple", "Consuming a golden apple"); }
            return eating;
        }
        if (sessionPlayer.getHealth() + sessionPlayer.getAbsorptionAmount() > threshold) { states.put("auto_gapple", "Waiting for the configured health threshold"); return false; }
        if (sessionPlayer.isUsingItem() || client.options.keyUse.isDown() || ticks < nextActionTick) { states.put("auto_gapple", "Waiting for manual use / cooldown"); return false; }
        int chosen = slot("GOLDEN_APPLE"); if (chosen < 0) chosen = slot("ENCHANTED_GOLDEN_APPLE");
        if (chosen < 0) { states.put("auto_gapple", "Put a golden apple in the hotbar"); return false; }
        restoreWeapon(client);
        foodPreviousSlot = sessionPlayer.getInventory().getSelectedSlot(); foodSlot = chosen;
        sessionPlayer.getInventory().setSelectedSlot(chosen);
        boolean accepted = false;
        try { accepted = access.useItem(client); }
        finally { if (!accepted) { sessionPlayer.getInventory().setSelectedSlot(foodPreviousSlot); foodSlot = foodPreviousSlot = -1; } }
        if (!accepted) { states.put("auto_gapple", "Item use was not accepted"); nextActionTick = ticks + delay(); return false; }
        foodStarted = ticks; foodCount = sessionPlayer.getInventory().getItem(chosen).getCount();
        foodInventoryRevision = PvpAcknowledgements.inventoryRevision(client, chosen); eating = true;
        client.options.keyUse.setDown(true); states.put("auto_gapple", "Consuming a golden apple"); return true;
    }
    private void stopEating(Minecraft client) {
        if (eating) {
            client.options.keyUse.setDown(false);
            if (client.player == sessionPlayer && client.gameMode != null) {
                if (sessionPlayer.isUsingItem()) client.gameMode.releaseUsingItem(sessionPlayer);
                if (sessionPlayer.getInventory().getSelectedSlot() == foodSlot && foodPreviousSlot >= 0) sessionPlayer.getInventory().setSelectedSlot(foodPreviousSlot);
            }
        }
        eating = false; foodSlot = foodPreviousSlot = -1;
    }
    private boolean tickBot(Minecraft client) throws ReflectiveOperationException {
        var bot=ChunkFinderClient.BOT;
        if(!bot.ready()){stopEating(client);restoreWeapon(client);return false;}
        if(eating||sessionPlayer.getHealth()+sessionPlayer.getAbsorptionAmount()<12)tickGapple(client,12);
        if(eating){bot.detail("Healing with a golden apple");return false;}
        if(sessionPlayer.isUsingItem()||client.options.keyUse.isDown()){bot.detail("Paused for your item use");bot.pauseCombat();return false;}
        return botPlanner.tick(client);
    }
    private boolean tickCrystal(Minecraft client) throws ReflectiveOperationException {
        if (crystalAttackPending) {
            Entity attacked = client.level.getEntity(lastCrystalId);
            if ((attacked == null || attacked.isRemoved()) && PvpAcknowledgements.entityRemovalRevision(client, lastCrystalId) > crystalRemovalRevision) {
                crystalAttackPending = false;
                states.put("auto_crystal", "Crystal removal observed from the server"); return false;
            }
            if (ticks - lastCrystalTick < (ChunkFinderClient.BOT.enabled()?16:100)) { states.put("auto_crystal", "Attack requested · waiting for crystal removal"); return false; }
            crystalAttackPending = false; nextExplosionTick = ticks + 20;
            states.put("auto_crystal", "Crystal attack not confirmed · retry delayed"); return false;
        }
        if (crystal != null) {
            for (Entity entity : client.level.entitiesForRendering()) if (access.crystal(entity) && entity.isAlive() && !entity.isRemoved()
                    && distance(crystal.spot.center(1), entity) < .25) {
                crystal = null; states.put("auto_crystal", "Crystal placement observed from server entity spawn"); break;
            }
            if (crystal!=null && ticks >= crystal.deadline) {
                crystal = null; nextExplosionTick = ticks + 20;
                states.put("auto_crystal", "Crystal placement not observed · retry delayed"); return false;
            }
            if(crystal!=null){states.put("auto_crystal", PvpAcknowledgements.blockAcknowledged(client, crystal.sequence)
                    ? "Placement interaction acknowledged · waiting for crystal entity" : "Placement requested · waiting for server acknowledgement");
            return false;}
        }
        LivingEntity enemy = nearest(client);
        if (enemy == null) { states.put("auto_crystal", "No eligible target within PvP range"); return false; }
        int crystalsEvaluated = 0;
        for (Entity entity : client.level.entitiesForRendering()) if (access.crystal(entity) && entity.isAlive() && !entity.isRemoved()) {
            if (!sessionPlayer.isWithinEntityInteractionRange(entity, 0) || !sessionPlayer.hasLineOfSight(entity)) continue;
            if (++crystalsEvaluated > 12 || blastBudget <= 0) break;
            Vec3 center = new Vec3(entity.getX(), entity.getY(), entity.getZ());
            Blast blast = blast(client, center, enemy, 6, null);
            if (!blast.allowed) continue;
            if (entity.getId() == lastCrystalId && ticks - lastCrystalTick < 20) { states.put("auto_crystal", "Waiting for crystal removal"); return false; }
            lastCrystalId = entity.getId(); crystalRemovalRevision = PvpAcknowledgements.entityRemovalRevision(client, lastCrystalId);
            attack(client, entity); lastCrystalTick = ticks; crystalAttackPending = true;
            states.put("auto_crystal", "Crystal attack requested · " + blast.label()); return true;
        }
        int crystalSlot = slot("END_CRYSTAL");
        if (crystalSlot < 0) { states.put("auto_crystal", "Put end crystals in the hotbar"); return false; }
        List<Spot> candidates = spotsAround(enemy, -2, 0);
        candidates.sort(Comparator.comparingDouble(s -> distance(s.center(1), enemy)));
        int evaluated = 0;
        for (Spot base : candidates) {
            if (!loaded(client, base) || !sessionPlayer.isWithinBlockInteractionRange(base.pos(), 0) || !inActionRange(base.center(1))) continue;
            Object state = client.level.getBlockState(base.pos());
            if (!(access.block(state, "OBSIDIAN") || access.block(state, "BEDROCK"))
                    || !access.air(client.level.getBlockState(base.above().pos())) || !access.air(client.level.getBlockState(base.above().above().pos()))
                    || !clear(client, base.x, base.y + 1, base.z, base.x + 1, base.y + 3, base.z + 1)) continue;
            Object hit = access.rayToBlock(client, base.pos(), base.center(.99), false); if (hit == null) continue;
            if (++evaluated > 12 || blastBudget <= 0) break;
            Blast blast = blast(client, base.center(1), enemy, 6, null); if (!blast.allowed) continue;
            if(ChunkFinderClient.BOT.enabled())ArsenalBot.look(client,base.center(1));
            boolean accepted = withSlot(crystalSlot, () -> access.click(client, hit));
            if (accepted) crystal = new CrystalPlan(base, PvpAcknowledgements.predictionSequence(client), ticks + (ChunkFinderClient.BOT.enabled()?24:100));
            states.put("auto_crystal", accepted ? "Crystal placement requested · " + blast.label() : "Crystal placement was not accepted");
            return accepted;
        }
        states.put("auto_crystal", "No reachable clear base meets blast limits · cover may be needed"); return false;
    }
    private boolean tickAnchor(Minecraft client) throws ReflectiveOperationException {
        if (client.level.dimension().toString().contains("the_nether")) { anchor = null; states.put("auto_anchor", "Paused: anchors do not explode in the Nether"); return false; }
        if (anchor != null && ticks > anchor.deadline) {
            anchor = null; nextExplosionTick = ticks + 20;
            states.put("auto_anchor", "Anchor interaction not confirmed · retry delayed"); return false;
        }
        if (anchor != null && anchor.stage == Stage.WAIT_DETONATE) {
            if (!loaded(client, anchor.spot)) { states.put("auto_anchor", "Waiting: anchor chunk is no longer loaded"); return false; }
            if (!PvpAcknowledgements.blockAcknowledged(client, anchor.sequence)) { states.put("auto_anchor", "Detonation requested · awaiting server acknowledgement"); return false; }
            if (access.block(client.level.getBlockState(anchor.spot.pos()), "RESPAWN_ANCHOR")) {
                states.put("auto_anchor", "Detonation acknowledged · waiting for anchor removal"); return false;
            }
            lastAnchor = anchor.spot; lastAnchorTick = ticks; anchor = null;
            states.put("auto_anchor", "Anchor detonation confirmed by acknowledged block removal"); return false;
        }
        LivingEntity enemy = nearest(client);
        if (enemy == null) { anchor = null; states.put("auto_anchor", "No eligible target within PvP range"); return false; }
        if (anchor != null && !anchor.target.equals(enemy.getUUID())) anchor = null;
        if (anchor == null) {
            List<Spot> candidates = spotsAround(enemy, -1, 1);
            candidates.sort(Comparator.comparingDouble(s -> distance(s.center(.5), enemy)));
            int evaluated = 0;
            for (Spot pos : candidates) {
                if (!loaded(client, pos) || !inActionRange(pos.center(.5)) || !sessionPlayer.isWithinBlockInteractionRange(pos.pos(), 0)) continue;
                if (lastAnchor != null && lastAnchor.equals(pos) && ticks - lastAnchorTick < 40) continue;
                Object state = client.level.getBlockState(pos.pos()); boolean exists = access.block(state, "RESPAWN_ANCHOR");
                if (!exists && (!access.air(state) || access.air(client.level.getBlockState(pos.below().pos())) || !clear(client, pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1))) continue;
                if (!exists && !sessionPlayer.isWithinBlockInteractionRange(pos.below().pos(), 0)) continue;
                if (access.anchorWorks(client, pos.pos())) continue;
                Object hit = exists ? access.rayToBlock(client, pos.pos(), pos.center(.5), false)
                        : access.rayToBlock(client, pos.below().pos(), pos.center(-.01), true);
                if (hit == null) continue;
                if (++evaluated > 12 || blastBudget <= 0) break;
                Blast blast = blast(client, pos.center(.5), enemy, 5, exists ? pos.pos() : null); if (!blast.allowed) continue;
                anchor = new AnchorPlan(pos, enemy.getUUID(), exists ? (access.charges(state) > 0 ? Stage.DETONATE : Stage.CHARGE) : Stage.PLACE, ticks + (ChunkFinderClient.BOT.enabled()?24:100));
                break;
            }
            if (anchor == null) { states.put("auto_anchor", "No reachable anchor site meets blast / dimension limits"); return false; }
        }
        Spot pos = anchor.spot;
        if (!loaded(client, pos) || !sessionPlayer.isWithinBlockInteractionRange(pos.pos(), 0) || !inActionRange(pos.center(.5))) { anchor = null; states.put("auto_anchor", "Anchor moved outside normal interaction reach"); return false; }
        if (access.anchorWorks(client, pos.pos())) { anchor = null; states.put("auto_anchor", "Paused: anchors set spawn at this location"); return false; }
        Object state = client.level.getBlockState(pos.pos()); boolean exists = access.block(state, "RESPAWN_ANCHOR");
        if (anchor.stage == Stage.WAIT_PLACE) {
            if (!PvpAcknowledgements.blockAcknowledged(client, anchor.sequence)) { states.put("auto_anchor", "Placement requested · awaiting server acknowledgement"); return false; }
            if (!exists) { states.put("auto_anchor", "Waiting to observe the placed anchor"); return false; }
            anchor.stage = access.charges(state) > 0 ? Stage.DETONATE : Stage.CHARGE;
        }
        if (anchor.stage == Stage.WAIT_CHARGE) {
            if (!PvpAcknowledgements.blockAcknowledged(client, anchor.sequence)) { states.put("auto_anchor", "Charge requested · awaiting server acknowledgement"); return false; }
            if (!exists || access.charges(state) <= anchor.previousCharge) { states.put("auto_anchor", "Waiting to observe anchor charge"); return false; }
            anchor.stage = Stage.DETONATE;
        }
        if (blastBudget <= 0) { states.put("auto_anchor", "Waiting for the next bounded blast scan"); return false; }
        Blast blast = blast(client, pos.center(.5), enemy, 5, exists ? pos.pos() : null);
        if (!blast.allowed) { states.put("auto_anchor", "Paused: blast limits changed · " + blast.label()); return false; }
        if (anchor.stage == Stage.PLACE) {
            int chosen = slot("RESPAWN_ANCHOR"); if (chosen < 0) { states.put("auto_anchor", "Put respawn anchors in the hotbar"); return false; }
            if (!access.air(state)) { anchor = null; states.put("auto_anchor", "Anchor site is no longer empty"); return false; }
            if (!sessionPlayer.isWithinBlockInteractionRange(pos.below().pos(), 0)) { states.put("auto_anchor", "Anchor support is outside normal block reach"); return false; }
            Object hit = access.rayToBlock(client, pos.below().pos(), pos.center(-.01), true); if (hit == null) { states.put("auto_anchor", "Anchor support is not visible from above"); return false; }
            if (!withSlot(chosen, () -> access.click(client, hit))) { states.put("auto_anchor", "Anchor placement was not accepted"); return false; }
            anchor.sequence = PvpAcknowledgements.predictionSequence(client);
            anchor.stage = Stage.WAIT_PLACE; anchor.deadline = ticks + (ChunkFinderClient.BOT.enabled()?24:100); states.put("auto_anchor", "Placement requested · awaiting server anchor"); return true;
        }
        if (!exists) { anchor = null; states.put("auto_anchor", "Anchor disappeared"); return false; }
        Object hit = access.rayToBlock(client, pos.pos(), pos.center(.5), false); if (hit == null) { states.put("auto_anchor", "Anchor is not directly visible"); return false; }
        int charge = access.charges(state);
        if (anchor.stage == Stage.CHARGE) {
            if (charge > 0) { anchor.stage = Stage.DETONATE; return false; }
            int chosen = slot("GLOWSTONE"); if (chosen < 0) { states.put("auto_anchor", "Put glowstone in the hotbar"); return false; }
            if (!withSlot(chosen, () -> access.click(client, hit))) { states.put("auto_anchor", "Anchor charging was not accepted"); return false; }
            anchor.sequence = PvpAcknowledgements.predictionSequence(client);
            anchor.previousCharge = charge; anchor.stage = Stage.WAIT_CHARGE; anchor.deadline = ticks + (ChunkFinderClient.BOT.enabled()?24:100);
            states.put("auto_anchor", "Charge requested · awaiting observed charge"); return true;
        }
        if (charge <= 0) { anchor.stage = Stage.CHARGE; states.put("auto_anchor", "Anchor has no observed charge"); return false; }
        if (access.item(sessionPlayer.getOffhandItem(), "GLOWSTONE")) { states.put("auto_anchor", "Move glowstone out of the offhand before detonation"); return false; }
        int chosen = detonateSlot(); if (chosen < 0) { states.put("auto_anchor", "Need a non-anchor, non-glowstone hotbar slot"); return false; }
        if (!withSlot(chosen, () -> access.click(client, hit))) { states.put("auto_anchor", "Detonation interaction was not accepted"); return false; }
        anchor.sequence = PvpAcknowledgements.predictionSequence(client);
        anchor.stage = Stage.WAIT_DETONATE; anchor.deadline = ticks + (ChunkFinderClient.BOT.enabled()?24:100);
        states.put("auto_anchor", "Detonation requested · " + blast.label()); return true;
    }
    private int detonateSlot() throws ReflectiveOperationException {
        for (int i = 0; i < 9; i++) if (sessionPlayer.getInventory().getItem(i).isEmpty()) return i;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = sessionPlayer.getInventory().getItem(i);
            if (!access.item(stack, "GLOWSTONE") && !access.item(stack, "RESPAWN_ANCHOR")) return i;
        }
        return -1;
    }
    private Blast blast(Minecraft client, Vec3 center, LivingEntity enemy, double power, BlockPos removedAnchor) throws ReflectiveOperationException {
        blastBudget--;
        double self = Math.ceil(rawDamage(client, center, sessionPlayer, power, removedAnchor)) * 1.5;
        double enemyHint = rawDamage(client, center, enemy, power, removedAnchor);
        if(ChunkFinderClient.BOT.enabled()) {
            self=dev.amethyst.finder.modules.pvp.BlastEstimates.damage(client,sessionPlayer,rawDamage(client,center,sessionPlayer,power,removedAnchor))*1.2+1;
            enemyHint=dev.amethyst.finder.modules.pvp.BlastEstimates.damage(client,enemy,enemyHint);
        }
        boolean allowed = self <= config.pvpMaxSelfDamage && self + 2 < sessionPlayer.getHealth() + sessionPlayer.getAbsorptionAmount() && enemyHint >= config.pvpMinTargetDamage;
        if (allowed) for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != sessionPlayer && entity.isAlive() && friend(entity)
                    && rawDamage(client, center, entity, power, removedAnchor) > 1) { allowed = false; break; }
        }
        return new Blast(allowed, self, enemyHint);
    }
    private double rawDamage(Minecraft client, Vec3 center, Entity entity, double power, BlockPos removedAnchor) throws ReflectiveOperationException {
        double diameter = power * 2, distance = Math.sqrt(distance(center, entity)); if (distance > diameter) return 0;
        double influence = (1 - distance / diameter) * access.exposure(client, center, entity, removedAnchor);
        return (influence * influence + influence) / 2 * 7 * diameter + 1;
    }
    private boolean inActionRange(Vec3 pos) { return distance(pos, sessionPlayer) <= range() * range(); }
    private static boolean loaded(Minecraft client, Spot spot) {
        return client.level.getChunkSource().getChunk(spot.x >> 4, spot.z >> 4, ChunkStatus.FULL, false) != null;
    }
    private static List<Spot> spotsAround(Entity target, int lowY, int highY) {
        List<Spot> result = new ArrayList<>(); int x = (int)Math.floor(target.getX()), y = (int)Math.floor(target.getY()), z = (int)Math.floor(target.getZ());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = lowY; dy <= highY; dy++) result.add(new Spot(x + dx, y + dy, z + dz));
        return result;
    }
    private static boolean clear(Minecraft client, double x0, double y0, double z0, double x1, double y1, double z1) {
        for (Entity entity : client.level.entitiesForRendering()) if (!entity.isRemoved()) {
            var b = entity.getBoundingBox();
            if (b.maxX > x0 && b.minX < x1 && b.maxY > y0 && b.minY < y1 && b.maxZ > z0 && b.minZ < z1) return false;
        }
        return true;
    }
    private static double distance(Vec3 point, Entity entity) { return squared(point.x - entity.getX(), point.y - entity.getY(), point.z - entity.getZ()); }
    private static double squared(double x, double y, double z) { return x*x + y*y + z*z; }
    @FunctionalInterface private interface Action { boolean run() throws ReflectiveOperationException; }
    private record Spot(int x, int y, int z) {
        BlockPos pos() { return new BlockPos(x, y, z); }
        Spot above() { return new Spot(x, y + 1, z); }
        Spot below() { return new Spot(x, y - 1, z); }
        Vec3 center(double height) { return new Vec3(x + .5, y + height, z + .5); }
    }
    private record Blast(boolean allowed, double self, double target) {
        String label() { return String.format(Locale.ROOT, "self estimate %.1f / target estimate %.1f", self, target); }
    }
    private record CrystalPlan(Spot spot, int sequence, int deadline) {}
    private enum Stage { PLACE, WAIT_PLACE, CHARGE, WAIT_CHARGE, DETONATE, WAIT_DETONATE }
    private static final class AnchorPlan {
        final Spot spot; final UUID target; Stage stage; int deadline, previousCharge, sequence = -1;
        AnchorPlan(Spot spot, UUID target, Stage stage, int deadline) { this.spot = spot; this.target = target; this.stage = stage; this.deadline = deadline; }
    }
}
