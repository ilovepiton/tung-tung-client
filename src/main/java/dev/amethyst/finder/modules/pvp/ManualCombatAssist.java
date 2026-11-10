package dev.amethyst.finder.modules.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.modules.PvpModules;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Assists only the user's accepted vanilla block use, never searches for an enemy or remote placement. */
public final class ManualCombatAssist {
    public record Observation(LocalPlayer player, ClientLevel level, ClientPacketListener connection, InteractionHand hand, ItemStack held,
                              BlockPos pos, boolean anchorAlreadyPresent, Set<Integer> oldCrystals) {}
    private final FinderConfig config;
    private final PvpModules owner;
    private final PvpAccess access = new PvpAccess();
    private LocalPlayer player;
    private ClientLevel level;
    private ClientPacketListener connection;
    private Anchor anchor;
    private Crystal crystal;
    private boolean awaitRelease;
    private boolean advancing;
    // Real button callbacks also arrive while a screen is open. Vanilla's cached
    // right-button flag and KeyMapping.releaseAll do not describe those releases.
    private boolean mouseUseKnown, mouseUseDown;
    private String mouseUseBinding;
    private String lastAssistId;
    private int clock, nextAction, lastCrystalAttackTick = -1, serverReceiptTicks = 6;
    private String anchorStatus = "Manual assist: right-click with a respawn anchor";
    private String crystalStatus = "Manual assist: right-click with an end crystal";

    public ManualCombatAssist(FinderConfig config, PvpModules owner) { this.config = config; this.owner = owner; }
    public boolean hasWork() { return anchor != null || crystal != null || awaitRelease; }
    public boolean ownsInventory() { return anchor != null; }
    public boolean reservesInventorySlot(int slot) {
        return anchor!=null&&(slot==anchor.ownedSlot||slot==anchor.glowSlot
            || anchor.move!=null&&(slot==anchor.move.source||slot==anchor.move.target));
    }
    public boolean blocksRepeat(Minecraft client) {
        if(!config.pvpManualAssist||!sameSession(client)||client.player==null)return false;
        boolean anchorHeld=client.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)||client.player.getOffhandItem().is(Items.RESPAWN_ANCHOR);
        if(anchor!=null)return anchor.stage!=Stage.RESTORE&&anchor.stage!=Stage.WAIT_RESTORE||anchorHeld&&awaitRelease&&useHeld(client);
        if(crystal==null&&anchorHeld&&awaitRelease&&"auto_anchor".equals(lastAssistId))return useHeld(client);
        boolean crystalHeld=client.player.getMainHandItem().is(Items.END_CRYSTAL)||client.player.getOffhandItem().is(Items.END_CRYSTAL);
        if(!crystalHeld){awaitRelease=false;return false;}
        return crystal!=null||(awaitRelease&&useHeld(client));
    }
    public void mouseButton(Minecraft client, int button, int action) {
        if ((action != 0 && action != 1) || !client.options.keyUse.matches(InputConstants.Type.MOUSE.getOrCreate(button))) return;
        mouseUseKnown = true;
        mouseUseBinding = client.options.keyUse.saveString();
        mouseUseDown = action == 1;
        if(action==1&&sameSession(client)&&client.gui.screen()==null) {
            if(crystal!=null&&crystal.entityId<0&&clock>crystal.deadline-36){crystal=null;awaitRelease=false;}
            if(anchor!=null&&anchor.stage==Stage.WAIT_PLACE&&anchor.move==null)finish(client,"New click: previous placement released");
        }
        // Real presses do not auto-repeat. After focus loss, the previous release
        // may have gone to another app; a new gameplay press is still a new gesture.
        // A press during active cleanup remains blocked until its own release.
        if (sameSession(client) && anchor == null && crystal == null
                && (!mouseUseDown || (client.gui.screen() == null && client.gui.overlay() == null))) awaitRelease = false;
    }
    public String status(String id) { return "auto_anchor".equals(id) ? anchorStatus : crystalStatus; }
    public void enabled(String id) {
        if ("auto_anchor".equals(id) && anchor == null) anchorStatus = "Manual assist: right-click with a respawn anchor";
        if ("auto_crystal".equals(id) && crystal == null) crystalStatus = "Manual assist: right-click with an end crystal";
    }

    public Observation capture(Minecraft client, InteractionHand hand, BlockHitResult hit, ItemStack held) {
        if (hasWork() && !sameSession(client)) reset(client);
        if (!config.pvpManualAssist || client.player == null || client.level == null || client.getConnection() == null || hasWork()) return null;
        boolean continueAnchor = held.is(Items.GLOWSTONE) && client.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)
                && config.isModuleEnabled("auto_anchor");
        boolean isAnchor = (held.is(Items.RESPAWN_ANCHOR) || continueAnchor) && config.isModuleEnabled("auto_anchor");
        boolean isCrystal = held.is(Items.END_CRYSTAL) && config.isModuleEnabled("auto_crystal");
        if (!isAnchor && !isCrystal) return null;
        BlockPos pos = continueAnchor ? hit.getBlockPos().immutable()
                : isAnchor ? new BlockPlaceContext(client.player, hand, held, hit).getClickedPos().immutable() : hit.getBlockPos().above();
        Set<Integer> old = new HashSet<>();
        if (isCrystal) for (Entity entity : client.level.entitiesForRendering())
            if (entity.getType() == net.minecraft.world.entity.EntityTypes.END_CRYSTAL && entity.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) < .25) old.add(entity.getId());
        return new Observation(client.player, client.level, client.getConnection(), hand, held.copy(), pos,
                client.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR), Set.copyOf(old));
    }

    public void observed(Minecraft client, Observation use, boolean accepted) {
        if (!accepted || use == null || !config.pvpManualAssist || use.player != client.player || use.level != client.level || use.connection != client.getConnection() || hasWork()) return;
        player = client.player; level = client.level; connection = use.connection;
        try {
            int sequence = PvpAcknowledgements.predictionSequence(client);
            if (((use.held.is(Items.RESPAWN_ANCHOR) && !use.anchorAlreadyPresent)
                    || (use.held.is(Items.GLOWSTONE) && use.anchorAlreadyPresent)) && config.isModuleEnabled("auto_anchor")) {
                anchor = new Anchor(use.pos, sequence, clock + receiptWindow(), clock, player.getInventory().getSelectedSlot(), use.hand == InteractionHand.MAIN_HAND);
                lastAssistId = "auto_anchor";
                anchorStatus = "Manual anchor placement requested · waiting for server block";
            } else if (use.held.is(Items.END_CRYSTAL) && config.isModuleEnabled("auto_crystal")) {
                crystal = new Crystal(use.pos, sequence, clock + receiptWindow(), clock, use.oldCrystals, use.hand);
                lastAssistId = "auto_crystal";
                crystalStatus = "Manual crystal placement requested · waiting for server entity";
            }
            awaitRelease = anchor != null || crystal != null;
        } catch (ReflectiveOperationException ex) { anchorStatus = crystalStatus = "Paused: cannot observe vanilla placement sequence"; }
    }

    public void reset(Minecraft client) {
        restoreSelection(client);
        player = null; level = null; connection = null; anchor = null; crystal = null; awaitRelease = false; lastAssistId = null; clock = nextAction = 0; lastCrystalAttackTick = -1; serverReceiptTicks = 6;
        anchorStatus = "Manual assist: right-click with a respawn anchor";
        crystalStatus = "Manual assist: right-click with an end crystal";
    }
    public void disabled(Minecraft client, String id) {
        if ("auto_crystal".equals(id)) { crystal = null; crystalStatus = "Disabled"; }
        if ("auto_anchor".equals(id) && anchor != null) finish(client, "Anchor assist cancelled · restoring owned inventory");
        if (id.equals(lastAssistId)) awaitRelease = false;
    }
    public void paused(Minecraft client, String reason) {
        clock++;
        if (anchor != null && anchor.stage != Stage.RESTORE && anchor.stage != Stage.WAIT_RESTORE) finish(client, reason);
        if (crystal != null) { crystal = null; crystalStatus = reason; }
        cleanup(client);
        // Opening a screen/releasing focus calls KeyMapping.releaseAll; that is not an actual RMB release.
    }
    public void tick(Minecraft client) {
        clock++;
        if (anchor == null && crystal == null && (!useHeld(client) || !config.pvpManualAssist)) awaitRelease = false;
        if (!sameSession(client)) { if (hasWork()) reset(client); return; }
        advance(client);
    }
    /** Advance on a real spawn/block/inventory/removal packet, without adding a tick delay. */
    public void received(Minecraft client) {
        if(sameSession(client)&&hasWork())advance(client);
    }
    private void advance(Minecraft client) {
        if(advancing)return;
        advancing=true;
        try {
            if (anchor != null) {
                if (!config.pvpManualAssist || !config.isModuleEnabled("auto_anchor")) finish(client, "Anchor assist cancelled");
                if (anchor != null && anchor.stage != Stage.RESTORE && anchor.stage != Stage.WAIT_RESTORE) {
                    if (player.getInventory().getSelectedSlot() != anchor.ownedSlot) finish(client, "Cancelled: hotbar selection changed");
                    else if (player.isUsingItem() || player.isHandsBusy()) finish(client, "Cancelled: another item action started");
                    else tickAnchor(client);
                }
                cleanup(client);
            }
            if (crystal != null) {
                if (!config.pvpManualAssist || !config.isModuleEnabled("auto_crystal")) { crystal = null; crystalStatus = "Crystal assist cancelled"; }
                else tickCrystal(client);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            String message = "Paused: manual interaction unavailable · " + ex.getClass().getSimpleName();
            if (anchor != null) finish(client, message);
            if (crystal != null) { crystal = null; crystalStatus = message; }
        } finally { advancing=false; }
        if (anchor == null && crystal == null && (!useHeld(client) || !config.pvpManualAssist)) awaitRelease = false;
    }

    private void tickCrystal(Minecraft client) throws ReflectiveOperationException {
        if (clock > crystal.deadline) { crystal = null; crystalStatus = "No matching server crystal/removal observed · right-click again"; return; }
        if (!loaded(crystal.pos)) { crystalStatus = "Waiting: manually placed crystal chunk is no longer loaded"; return; }
        if (crystal.entityId >= 0) {
            if (PvpAcknowledgements.entityRemovalRevision(client, crystal.entityId) > crystal.removalRevision) {
                InteractionHand hand = crystal.hand;
                crystal = null; if (config.pvpCrystalRepeat) awaitRelease = false;
                crystalStatus = config.pvpCrystalRepeat?"Crystal removed · hold right click to continue":"Manually placed crystal removal confirmed by server";
                if (config.pvpFastAssist && config.pvpCrystalRepeat) repeatCrystal(client, hand);
                return;
            }
            if (clock < crystal.retryAt || crystal.attacks >= 20) {
                crystalStatus = "Crystal attacked · waiting for server removal"; return;
            }
        }
        // A crystal entity received from the server is the placement receipt.
        // Bukkit does not have to send a block-change acknowledgement for an entity item.
        Entity found = crystal.entityId < 0 ? null : level.getEntity(crystal.entityId);
        if (crystal.entityId < 0) for (Entity entity : level.entitiesForRendering())
            if (access.crystal(entity) && !entity.isRemoved() && entity.isAlive() && !crystal.oldIds.contains(entity.getId())
                    && entity.position().distanceToSqr(Vec3.atBottomCenterOf(crystal.pos)) < .25) { found = entity; break; }
        if (found == null) { crystalStatus = "Waiting for the matching server crystal spawn · adaptive server window " + receiptWindow() + " ticks"; return; }
        if (!crystal.spawnNoted) { noteReceipt(clock - crystal.sentAt); crystal.spawnNoted = true; crystal.deadline = clock + receiptWindow(); }
        if (found.isRemoved() || !found.isAlive()) { crystalStatus = "Waiting for server crystal removal"; return; }
        if (!player.isWithinEntityInteractionRange(found, 0) || !player.isWithinAttackRange(player.getMainHandItem(), found.getBoundingBox(), 0)
                || !player.hasLineOfSight(found)) { crystalStatus = "Waiting: your placed crystal is outside visible normal attack reach"; return; }
        double damage = config.pvpManualBlastChecks?owner.manualSelfDamage(client, found.position(), 6, null):0;
        if (config.pvpManualBlastChecks&&!safe(damage)) { crystalStatus = damageMessage("Crystal", damage); return; }
        String friend = owner.manualFriendAtRisk(client, found.position(), 6, null);
        if (friend != null) { crystalStatus = "Waiting: crystal blast could hurt protected friend " + friend; return; }
        if (clock < nextAction) return;
        crystal.entityId = found.getId(); crystal.removalRevision = PvpAcknowledgements.entityRemovalRevision(client, found.getId());
        owner.assistedAttack(client, found); nextAction = clock + delay(); lastCrystalAttackTick = clock;
        crystal.attacks++; crystal.retryAt = clock + retryTicks();
        crystalStatus = "Your crystal attacked"+(config.pvpManualBlastChecks?" · self upper bound " + number(damage) + " HP":" · manual blast checks OFF");
    }

    private void repeatCrystal(Minecraft client, InteractionHand hand) throws ReflectiveOperationException {
        if (!useHeld(client) || !player.getItemInHand(hand).is(Items.END_CRYSTAL) || player.isUsingItem()) return;
        // Recompute the real crosshair, including entity obstruction. Continue
        // only the held use gesture; never place using an old saved block hit.
        var hit = player.raycastHitResult(1.0F, client.getCameraEntity());
        if (!(hit instanceof BlockHitResult block) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                || !player.isWithinBlockInteractionRange(block.getBlockPos(), 0)) return;
        Observation use = capture(client, hand, block, player.getItemInHand(hand));
        if (use != null) observed(client, use, owner.assistedClick(client, block, hand));
    }

    private void tickAnchor(Minecraft client) throws ReflectiveOperationException {
        Anchor a = anchor;
        if (clock > a.deadline) { finish(client, "Anchor assist stopped: " + anchorStatus + " · no unconfirmed detonation"); return; }
        if (!loaded(a.pos)) { anchorStatus = "Waiting: manually placed anchor chunk is no longer loaded"; return; }
        if (access.anchorWorks(client, a.pos)) { finish(client, "Paused: anchors set spawn at this location"); return; }
        var state = level.getBlockState(a.pos); boolean exists = state.is(Blocks.RESPAWN_ANCHOR);
        if (a.stage == Stage.WAIT_DETONATE) {
            if (!PvpAcknowledgements.blockAcknowledged(client, a.sequence)) { anchorStatus = "Waiting for detonation acknowledgement · adaptive server window " + receiptWindow() + " ticks"; return; }
            if (exists) { anchorStatus = "Detonation acknowledged · waiting for received anchor removal"; return; }
            noteReceipt(clock - a.sentAt);
            finish(client, "Manual anchor detonation confirmed by server removal"); return;
        }
        if (a.stage == Stage.WAIT_PLACE) {
            if (!PvpAcknowledgements.blockAcknowledged(client, a.sequence) || !exists) { anchorStatus = "Waiting for your anchor to be received from server · adaptive window " + receiptWindow() + " ticks"; return; }
            noteReceipt(clock - a.sentAt); a.deadline = clock + receiptWindow();
            String coverProblem = coverProblem(client, a.pos);
            if (coverProblem != null) a.skipCover=true;
            boolean needsCoverItem = config.pvpAnchorCover && !a.skipCover && !level.getBlockState(a.pos.relative(selfDirection(a.pos))).is(Blocks.GLOWSTONE);
            a.stage = access.charges(state) > 0 && !needsCoverItem ? Stage.COVER : Stage.GLOWSTONE;
        }
        if (!exists) { finish(client, "Anchor disappeared before the assisted detonation"); return; }
        if (!player.isWithinBlockInteractionRange(a.pos, 0)) { anchorStatus = "Waiting: anchor outside normal block reach"; return; }
        if (a.stage == Stage.WAIT_SWAP) {
            if (!a.move.confirmed(client)) { anchorStatus = "Waiting for server glowstone inventory swap"; return; }
            noteReceipt(clock - a.sentAt); a.deadline = clock + receiptWindow();
            a.move.confirmed = true; a.glowSlot = a.move.target; select(a, a.glowSlot); a.stage = Stage.CHARGE;
        }
        if (a.stage == Stage.GLOWSTONE) {
            int source = glowstoneSlot();
            if (source < 0) { finish(client, "Anchor left in place: no glowstone · add glowstone and right-click the anchor to retry"); return; }
            if (source < 9) { a.glowSlot = source; select(a, source); a.stage = Stage.CHARGE; }
            else {
                if (!inventorySafe()) { anchorStatus = "Waiting: close inventory with an empty cursor"; return; }
                int destination = temporaryHotbarSlot(a);
                a.move = new Move(source, destination, player.getInventory().getItem(source).copy(), player.getInventory().getItem(destination).copy(),
                        PvpAcknowledgements.inventoryRevision(client, source), PvpAcknowledgements.inventoryRevision(client, destination));
                swap(client, source, destination); a.stage = Stage.WAIT_SWAP; a.sentAt = clock; a.deadline = clock + receiptWindow(); anchorStatus = "Glowstone swap requested · awaiting real server slots"; return;
            }
        }
        if (a.stage == Stage.WAIT_CHARGE) {
            if (!PvpAcknowledgements.blockAcknowledged(client, a.sequence)) { anchorStatus = "Waiting for anchor charge acknowledgement"; return; }
            if (access.charges(state) <= a.previousCharge) { anchorStatus = "Charge not yet received from server"; return; }
            noteReceipt(clock - a.sentAt); a.deadline = clock + receiptWindow(); a.stage = Stage.COVER;
        }
        if (a.stage == Stage.CHARGE) {
            if (access.charges(state) > 0) a.stage = Stage.COVER;
            else {
                if (!glowstoneStillOwned(a)) { finish(client, "Cancelled: glowstone hand or inventory changed"); return; }
                Object hit = anchorHit(client, a.pos);
                if (hit == null) { anchorStatus = "Waiting: anchor has no visible vanilla block hit"; return; }
                if (clock < nextAction) return;
                if (!owner.assistedClick(client, hit, InteractionHand.MAIN_HAND)) { anchorStatus = "Anchor charge interaction was not accepted"; return; }
                rememberOwnedGlowstone(a);
                a.previousCharge = 0; a.sequence = PvpAcknowledgements.predictionSequence(client); a.stage = Stage.WAIT_CHARGE; a.sentAt = clock; a.deadline = clock + receiptWindow();
                nextAction = clock + delay(); anchorStatus = "Charge requested · waiting for received charge"; return;
            }
        }
        if (a.stage == Stage.WAIT_COVER) {
            if (!PvpAcknowledgements.blockAcknowledged(client, a.sequence)) { anchorStatus = "Waiting for glowstone cover acknowledgement"; return; }
            if (!loaded(a.cover) || !level.getBlockState(a.cover).is(Blocks.GLOWSTONE)) { anchorStatus = "Waiting for actual glowstone cover from server"; return; }
            noteReceipt(clock - a.sentAt); a.deadline = clock + receiptWindow(); a.stage = Stage.DETONATE;
        }
        if (a.stage == Stage.COVER) {
            if (!config.pvpAnchorCover || a.skipCover) a.stage = Stage.DETONATE;
            else {
                Direction direction = selfDirection(a.pos); BlockPos cover = a.pos.relative(direction);
                if (loaded(cover) && level.getBlockState(cover).is(Blocks.GLOWSTONE)) { a.cover = cover; a.stage = Stage.DETONATE; }
                else {
                    String problem = coverProblem(client, a.pos);
                    if (problem != null) { a.skipCover=true;a.stage=Stage.DETONATE;tickAnchor(client);return; }
                    if (!glowstoneStillOwned(a)) { finish(client, "Charged anchor remains: glowstone needed for cover · replenish it and retry with glowstone"); return; }
                    Object hit = access.rayToBlock(client, cover.below(), Vec3.atBottomCenterOf(cover).add(0, -.01, 0), true);
                    if (hit == null) { finish(client, "Charged anchor remains: required cover support is not visible · adjust position and retry"); return; }
                    if (clock < nextAction) return;
                    if (!owner.assistedClick(client, hit, InteractionHand.MAIN_HAND)) { anchorStatus = "Glowstone cover placement was not accepted"; return; }
                    rememberOwnedGlowstone(a);
                    a.cover = cover; a.sequence = PvpAcknowledgements.predictionSequence(client); a.stage = Stage.WAIT_COVER; a.sentAt = clock; a.deadline = clock + receiptWindow();
                    nextAction = clock + delay(); anchorStatus = "Self-facing glowstone cover requested · awaiting server block"; return;
                }
            }
        }
        if (a.stage != Stage.DETONATE) return;
        if (config.pvpAnchorCover && !a.skipCover && (a.cover == null || !a.cover.equals(a.pos.relative(selfDirection(a.pos)))
                || !loaded(a.cover) || !level.getBlockState(a.cover).is(Blocks.GLOWSTONE))) {
            a.stage = Stage.COVER; anchorStatus = "Waiting: required cover no longer lies between you and the anchor"; return;
        }
        double damage = config.pvpManualBlastChecks?owner.manualSelfDamage(client, Vec3.atCenterOf(a.pos), 5, a.pos):0;
        if (config.pvpManualBlastChecks&&!safe(damage)) { anchorStatus = damageMessage("Anchor", damage); return; }
        String friend = owner.manualFriendAtRisk(client, Vec3.atCenterOf(a.pos), 5, a.pos);
        if (friend != null) { anchorStatus = "Waiting: anchor blast could hurt protected friend " + friend; return; }
        if (player.getOffhandItem().is(Items.GLOWSTONE)) { anchorStatus = "Waiting: move extra glowstone out of offhand before detonation"; return; }
        int detonate = detonationSlot();
        if (detonate < 0) { anchorStatus = "Waiting: need a non-anchor, non-glowstone hotbar slot for detonation"; return; }
        Object hit = anchorHit(client, a.pos);
        if (hit == null) { anchorStatus = "Waiting: received cover blocks every normal anchor hit"; return; }
        if (clock < nextAction) return;
        select(a, detonate);
        if (!owner.assistedClick(client, hit, InteractionHand.MAIN_HAND)) { anchorStatus = "Anchor detonation interaction was not accepted"; return; }
        a.sequence = PvpAcknowledgements.predictionSequence(client); a.stage = Stage.WAIT_DETONATE; a.sentAt = clock; a.deadline = clock + receiptWindow();
        nextAction = clock + delay(); anchorStatus = "Detonation requested after received cover"+(config.pvpManualBlastChecks?" · self upper bound " + number(damage) + " HP":" · manual blast checks OFF");
    }

    private Object anchorHit(Minecraft client, BlockPos pos) throws ReflectiveOperationException {
        for (double height : new double[]{.99, .5, .01}) {
            Object hit = access.rayToBlock(client, pos, Vec3.atBottomCenterOf(pos).add(0, height, 0), false);
            if (hit != null) return hit;
        }
        return null;
    }
    private String coverProblem(Minecraft client, BlockPos pos) throws ReflectiveOperationException {
        if (!config.pvpAnchorCover) return null;
        BlockPos cover = pos.relative(selfDirection(pos));
        if (!loaded(cover)) return "Required self-facing cover chunk is not loaded";
        if (level.getBlockState(cover).is(Blocks.GLOWSTONE)) return null;
        if (!level.getBlockState(cover).isAir() || !clear(cover)) return "Required self-facing cover cell is blocked";
        if (level.getBlockState(cover.below()).isAir()) return "Required self-facing cover needs a solid block underneath";
        if (!player.isWithinBlockInteractionRange(cover.below(), 0)) return "Required cover support is outside normal reach";
        if (access.rayToBlock(client, cover.below(), Vec3.atBottomCenterOf(cover).add(0, -.01, 0), true) == null)
            return "Required cover support is not directly visible";
        return null;
    }
    private boolean safe(double damage) { return Double.isFinite(damage) && damage <= config.pvpMaxSelfDamage && damage + 2 < player.getHealth() + player.getAbsorptionAmount(); }
    private String damageMessage(String tool, double damage) { return "Waiting: " + tool + " self upper bound " + number(damage) + " HP / limit " + number(config.pvpMaxSelfDamage) + " · increase cover/spacing or adjust the limit"; }
    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private int delay() { return config.pvpFastAssist?0:Math.max(2, config.pvpDelayTicks); }
    /** Learns from actual spawn/block/inventory receipts instead of assuming one server tick rate. */
    private void noteReceipt(int ticks) {
        ticks = Math.clamp(ticks, 1, 40);
        serverReceiptTicks = Math.clamp((serverReceiptTicks * 3 + ticks + 1) / 4, 1, 40);
    }
    private int receiptWindow() { return Math.clamp(serverReceiptTicks * 8 + 24, 80, 160); }
    private int retryTicks() { return config.pvpFastAssist ? Math.clamp(serverReceiptTicks / 3, 1, 8) : Math.max(2, config.pvpDelayTicks); }
    private boolean useHeld(Minecraft client) {
        return mouseUseKnown && client.options.keyUse.saveString().equals(mouseUseBinding) ? mouseUseDown : client.options.keyUse.isDown();
    }
    private boolean sameSession(Minecraft client) { return player != null && level != null && connection != null && client.player == player && client.level == level && client.getConnection() == connection; }
    private boolean loaded(BlockPos pos) { return level != null && level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null; }
    private boolean inventorySafe() { return player.containerMenu == player.inventoryMenu && player.inventoryMenu.getCarried().isEmpty(); }
    private int glowstoneSlot() {
        if (player.getOffhandItem().is(Items.GLOWSTONE)) return 40;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).is(Items.GLOWSTONE)) return slot;
        return -1;
    }
    private int temporaryHotbarSlot(Anchor a) {
        return AnchorHotbarSelection.temporarySlot(a.originalSlot, slot -> player.getInventory().getItem(slot).isEmpty(),
                slot -> player.getInventory().getItem(slot).is(Items.RESPAWN_ANCHOR));
    }
    private int detonationSlot() {
        for (int slot = 0; slot < 9; slot++) if (player.getInventory().getItem(slot).isEmpty()) return slot;
        for (int slot = 0; slot < 9; slot++) if (!player.getInventory().getItem(slot).is(Items.GLOWSTONE) && !player.getInventory().getItem(slot).is(Items.RESPAWN_ANCHOR)) return slot;
        return -1;
    }
    private void select(Anchor a, int slot) { player.getInventory().setSelectedSlot(slot); a.ownedSlot = slot; }
    private boolean glowstoneStillOwned(Anchor a) {
        return a.glowSlot >= 0 && player.getInventory().getSelectedSlot() == a.ownedSlot
                && player.getInventory().getItem(a.glowSlot).is(Items.GLOWSTONE) && a.ownedSlot == a.glowSlot
                && (a.move == null || ItemStack.matches(player.getInventory().getItem(a.glowSlot), a.move.expectedTarget));
    }
    private void rememberOwnedGlowstone(Anchor a) {
        if (a.move != null) {
            a.move.beforeUse = a.move.expectedTarget.copy();
            a.move.expectedTarget = player.getInventory().getItem(a.move.target).copy();
        }
    }
    private Direction selfDirection(BlockPos pos) {
        double dx = player.getX() - (pos.getX() + .5), dz = player.getZ() - (pos.getZ() + .5);
        return Math.abs(dx) > Math.abs(dz) ? (dx < 0 ? Direction.WEST : Direction.EAST) : (dz < 0 ? Direction.NORTH : Direction.SOUTH);
    }
    private boolean clear(BlockPos pos) {
        for (Entity entity : level.entitiesForRendering()) if (!entity.isRemoved() && entity.getBoundingBox().intersects(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1)) return false;
        return true;
    }
    private void swap(Minecraft client, int source, int target) {
        if (!inventorySafe() || source == target || target < 0 || target > 8) throw new IllegalStateException("Unsafe inventory swap");
        int clicked = source == 40 ? target + 36 : source < 9 ? source + 36 : source;
        int button = source == 40 ? 40 : target;
        // No local prediction: vanilla server SWAP broadcasts authoritative slots rather than suppressing matching predictions.
        player.connection.send(new ServerboundContainerClickPacket(player.inventoryMenu.containerId, player.inventoryMenu.getStateId(),
                (short)clicked, (byte)button, ContainerInput.SWAP, new Int2ObjectOpenHashMap<>(),
                HashedStack.create(player.inventoryMenu.getCarried(), player.connection.decoratedHashOpsGenenerator())));
    }
    private void finish(Minecraft client, String message) {
        if (anchor == null) return;
        if (anchor.stage != Stage.RESTORE && anchor.stage != Stage.WAIT_RESTORE) {
            anchor.interruptedStage = anchor.stage;
            reconcileDeniedConsumption(client, anchor);
            anchor.finishMessage = message; anchor.stage = Stage.RESTORE; anchor.deadline = clock + 60;
            restoreSelection(client); cleanup(client);
        }
    }
    private void reconcileDeniedConsumption(Minecraft client, Anchor a) {
        Move move = a.move;
        if (move == null || !move.confirmed || move.beforeUse == null || !sameSession(client) || !inventorySafe()
                || !loaded(a.pos) || !PvpAcknowledgements.blockAcknowledged(client, a.sequence)) return;
        boolean denied;
        try {
            Stage pendingStage = a.stage == Stage.RESTORE || a.stage == Stage.WAIT_RESTORE ? a.interruptedStage : a.stage;
            denied = (pendingStage == Stage.WAIT_CHARGE && level.getBlockState(a.pos).is(Blocks.RESPAWN_ANCHOR)
                    && access.charges(level.getBlockState(a.pos)) <= a.previousCharge)
                    || (pendingStage == Stage.WAIT_COVER && a.cover != null && loaded(a.cover) && !level.getBlockState(a.cover).is(Blocks.GLOWSTONE));
        } catch (ReflectiveOperationException unavailable) { return; }
        if (!denied) return;
        ItemStack source = player.getInventory().getItem(move.source), atTarget = player.getInventory().getItem(move.target);
        if (ItemStack.matches(source, move.beforeTarget) && ItemStack.matches(atTarget, move.beforeUse)
                && ItemStack.matches(source, PvpAcknowledgements.inventorySnapshot(client, move.source))
                && ItemStack.matches(atTarget, PvpAcknowledgements.inventorySnapshot(client, move.target))) move.expectedTarget = move.beforeUse.copy();
    }
    private void restoreSelection(Minecraft client) {
        if (anchor == null || !sameSession(client) || anchor.selectionReleased) return;
        // Release selection ownership once. A later reset/cleanup must not pull
        // the hand back after the user has begun another manual selection.
        anchor.selectionReleased = true;
        int slot = AnchorHotbarSelection.restoreSlot(anchor.originalSlot, anchor.ownedSlot,
                player.getInventory().getSelectedSlot(), anchor.returnToAnchor,
                candidate -> player.getInventory().getItem(candidate).is(Items.RESPAWN_ANCHOR));
        if (slot >= 0) player.getInventory().setSelectedSlot(slot);
    }
    private void cleanup(Minecraft client) {
        if (anchor == null || (anchor.stage != Stage.RESTORE && anchor.stage != Stage.WAIT_RESTORE)) return;
        Anchor a = anchor; Move move = a.move;
        if (!sameSession(client)) { anchor = null; return; }
        if (move == null) { anchorStatus = a.finishMessage; anchor = null; return; }
        if (clock > a.deadline) { anchorStatus = a.finishMessage + " · inventory receipt timed out; no forced overwrite"; anchor = null; return; }
        if (!inventorySafe()) { anchorStatus = a.finishMessage + " · waiting to restore with an empty inventory cursor"; return; }
        reconcileDeniedConsumption(client, a);
        if (a.stage == Stage.WAIT_RESTORE) {
            if (PvpAcknowledgements.inventoryRevision(client, move.source) <= move.sourceRevision || PvpAcknowledgements.inventoryRevision(client, move.target) <= move.targetRevision) return;
            if (ItemStack.matches(PvpAcknowledgements.inventorySnapshot(client, move.source), move.remaining)
                    && ItemStack.matches(PvpAcknowledgements.inventorySnapshot(client, move.target), move.beforeTarget)) {
                anchorStatus = a.finishMessage + " · owned inventory restored by server"; anchor = null;
            } else { anchorStatus = a.finishMessage + " · inventory changed; preserved received state"; anchor = null; }
            return;
        }
        if (!move.confirmed) {
            if (!move.confirmed(client)) { anchorStatus = a.finishMessage + " · awaiting the pending swap receipt"; return; }
            move.confirmed = true;
        }
        ItemStack atSource = player.getInventory().getItem(move.source), atTarget = player.getInventory().getItem(move.target);
        if (!ItemStack.matches(atSource, move.beforeTarget) || !ItemStack.matches(atTarget, move.expectedTarget)) {
            anchorStatus = a.finishMessage + " · owned slots changed; no forced inventory restore"; anchor = null; return;
        }
        if (!ItemStack.matches(atSource, PvpAcknowledgements.inventorySnapshot(client, move.source))
                || !ItemStack.matches(atTarget, PvpAcknowledgements.inventorySnapshot(client, move.target))) {
            anchorStatus = a.finishMessage + " · awaiting actual remaining-item receipt"; return;
        }
        if (ItemStack.matches(atSource, atTarget) && ItemStack.matches(atTarget, move.beforeTarget)) {
            anchorStatus = a.finishMessage + " · remaining owned inventory already restored"; anchor = null; return;
        }
        move.remaining = atTarget.copy(); move.sourceRevision = PvpAcknowledgements.inventoryRevision(client, move.source);
        move.targetRevision = PvpAcknowledgements.inventoryRevision(client, move.target);
        swap(client, move.source, move.target); a.stage = Stage.WAIT_RESTORE;
        anchorStatus = a.finishMessage + " · restoring owned inventory";
    }
    private enum Stage { WAIT_PLACE, GLOWSTONE, WAIT_SWAP, CHARGE, WAIT_CHARGE, COVER, WAIT_COVER, DETONATE, WAIT_DETONATE, RESTORE, WAIT_RESTORE }
    private static final class Anchor {
        final BlockPos pos; final int originalSlot; final boolean returnToAnchor; int ownedSlot, glowSlot = -1, sequence, previousCharge, deadline, sentAt; boolean skipCover, selectionReleased;
        Stage stage = Stage.WAIT_PLACE, interruptedStage; BlockPos cover; Move move; String finishMessage = "Anchor assist finished";
        Anchor(BlockPos pos,int sequence,int deadline,int sentAt,int originalSlot,boolean returnToAnchor) { this.pos=pos;this.sequence=sequence;this.deadline=deadline;this.sentAt=sentAt;this.originalSlot=this.ownedSlot=originalSlot;this.returnToAnchor=returnToAnchor; }
    }
    private static final class Move {
        final int source,target; final ItemStack beforeSource,beforeTarget; ItemStack remaining,expectedTarget,beforeUse; long sourceRevision,targetRevision; boolean confirmed;
        Move(int source,int target,ItemStack beforeSource,ItemStack beforeTarget,long sourceRevision,long targetRevision) {
            this.source=source;this.target=target;this.beforeSource=beforeSource;this.beforeTarget=beforeTarget;this.expectedTarget=beforeSource.copy();this.sourceRevision=sourceRevision;this.targetRevision=targetRevision;
        }
        boolean confirmed(Minecraft client) {
            return PvpAcknowledgements.inventoryRevision(client,source)>sourceRevision && PvpAcknowledgements.inventoryRevision(client,target)>targetRevision
                    && ItemStack.matches(PvpAcknowledgements.inventorySnapshot(client,source),beforeTarget)
                    && ItemStack.matches(PvpAcknowledgements.inventorySnapshot(client,target),beforeSource);
        }
    }
    private static final class Crystal {
        final BlockPos pos; final int sequence, sentAt; final Set<Integer> oldIds; final InteractionHand hand;
        int entityId = -1, attacks, retryAt, deadline; long removalRevision; boolean spawnNoted;
        Crystal(BlockPos pos,int sequence,int deadline,int sentAt,Set<Integer> oldIds,InteractionHand hand) { this.pos=pos;this.sequence=sequence;this.deadline=deadline;this.sentAt=sentAt;this.oldIds=oldIds;this.hand=hand; }
    }
}
