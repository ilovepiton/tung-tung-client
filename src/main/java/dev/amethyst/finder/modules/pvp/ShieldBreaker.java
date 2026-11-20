package dev.amethyst.finder.modules.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.Friends;
import dev.amethyst.finder.core.ClickActionGate;
import dev.amethyst.finder.core.ShieldBreakSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** Crosshair-only manual combo; never triggers from a normal attack or held-use repeat. */
public final class ShieldBreaker {
    public static final String ID = "auto_shield_breaker";
    private final FinderConfig config;
    private final ClickActionGate clicks = new ClickActionGate();
    private ShieldBreakSequence sequence;
    private LocalPlayer self;
    private ClientLevel level;
    private ClientPacketListener connection;
    private Player target;
    private ItemStack mace, axe;
    private int maceSlot = -1, axeSlot = -1, ownedSlot = -1;
    private boolean gestureOwned;
    private String status = "Hold a mace and right-click a raised enemy shield";
    public ShieldBreaker(FinderConfig config) { this.config = config; }
    public boolean busy() { return sequence != null; }
    public String status() { return config.isModuleEnabled(ID) ? status : "Disabled"; }
    public boolean reserves(int slot) { return busy() && (slot == maceSlot || slot == axeSlot); }
    private boolean playable(Minecraft c) {
        return config.isModuleEnabled(ID) && !config.streamerMode && c.player != null && c.level != null
            && c.getConnection() != null && c.gameMode != null && c.player.isAlive() && !c.player.isSpectator()
            && !c.player.isUsingItem() && !c.player.isHandsBusy() && c.gui.screen() == null && c.gui.overlay() == null
            && c.isWindowActive() && c.mouseHandler.isMouseGrabbed() && !c.isPaused()
            && (ChunkFinderClient.MODULES == null || !ChunkFinderClient.MODULES.cameraActive() && !ChunkFinderClient.MODULES.totemSwapPending())
            && (ChunkFinderClient.BOT == null || !ChunkFinderClient.BOT.enabled())
            && (ChunkFinderClient.MACE == null || !ChunkFinderClient.MACE.ownsInventory())
            && (ChunkFinderClient.PVP == null || !ChunkFinderClient.PVP.hasPendingExplosiveAssist())
            && (ChunkFinderClient.HARD_CRYSTAL == null || !ChunkFinderClient.HARD_CRYSTAL.busy());
    }
    public void mouse(Minecraft c, int button, int action) {
        if (action == 1 && c.options.keyAttack.matches(InputConstants.Type.MOUSE.getOrCreate(button)) && busy()) { reset(c); return; }
        if (!c.options.keyUse.matches(InputConstants.Type.MOUSE.getOrCreate(button))) return;
        boolean fresh = !gestureOwned;
        if (action == 0) gestureOwned = false;
        clicks.mouse(action, playable(c) && !busy() && fresh && c.player.getMainHandItem().is(Items.MACE));
        if (action != 1 || clicks.consume() == null) return;
        if (!(c.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player enemy)
                || enemy == c.player || !enemy.isAlive() || enemy.isSpectator() || !enemy.isBlocking()
                || Friends.listed(config, enemy) || !c.player.hasLineOfSight(enemy)
                || !c.player.isWithinEntityInteractionRange(enemy, 0)) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            var stack = c.player.getInventory().getItem(i);
            if (stack.is(ItemTags.AXES) && !stack.isEmpty() && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage() - 1)) { slot = i; break; }
        }
        if (slot < 0) { status = "Put a usable axe in the hotbar"; return; }
        if (!c.player.isWithinAttackRange(c.player.getInventory().getItem(slot), enemy.getBoundingBox(), 0)) return;
        self = c.player; level = c.level; connection = c.getConnection(); target = enemy;
        maceSlot = self.getInventory().getSelectedSlot(); mace = self.getMainHandItem().copy();
        axeSlot = slot; axe = self.getInventory().getItem(slot).copy(); sequence = new ShieldBreakSequence();
        gestureOwned = true; select(axeSlot); status = "Axe selected · waiting for attack cooldown";
    }
    public boolean interceptUse() { return busy() || gestureOwned; }
    private void select(int slot) { self.getInventory().setSelectedSlot(slot); ownedSlot = slot; }
    public void tick(Minecraft c) {
        if (!busy()) return;
        boolean session = self == c.player && level == c.level && connection == c.getConnection();
        boolean valid = session && playable(c) && self.getInventory().getSelectedSlot() == ownedSlot
            && target != null && target.isAlive() && !target.isRemoved() && !target.isSpectator() && !Friends.listed(config, target)
            && c.hitResult instanceof EntityHitResult hit && hit.getEntity() == target && self.hasLineOfSight(target)
            && self.isWithinEntityInteractionRange(target, 0) && self.isWithinAttackRange(self.getMainHandItem(), target.getBoundingBox(), 0)
            && sameWeapon(self.getInventory().getItem(maceSlot), mace) && sameWeapon(self.getInventory().getItem(axeSlot), axe);
        var action = sequence.next(valid, valid && target.isBlocking(), valid && self.getAttackStrengthScale(.5f) >= .9f);
        if (action == ShieldBreakSequence.Action.CANCEL) { finish(c, "Combo cancelled · target, cooldown or held slot changed"); return; }
        if (action == ShieldBreakSequence.Action.AXE) {
            // Vanilla attack synchronizes the selected hotbar slot before its attack packet.
            c.gameMode.attack(self, target);
            self.swing(InteractionHand.MAIN_HAND, self.getMainHandItem().getAttackAnimation(), false);
            finish(c, "Axe attack sent · mace restored · next attack is yours");
        }
    }
    private static boolean sameWeapon(ItemStack current, ItemStack original) {
        // Durability may change after the axe strike; item type and enchantments must still match.
        return !current.isEmpty() && current.getItem() == original.getItem()
            && current.getEnchantments().equals(original.getEnchantments());
    }
    private void finish(Minecraft c, String message) {
        if (self == c.player && level == c.level && connection == c.getConnection() && ownedSlot >= 0
            && self.getInventory().getSelectedSlot() == ownedSlot && sameWeapon(self.getInventory().getItem(maceSlot), mace)) select(maceSlot);
        sequence = null; target = null; self = null; level = null; connection = null;
        mace = axe = null; maceSlot = axeSlot = ownedSlot = -1; status = message;
    }
    public void reset(Minecraft c) { finish(c, "Hold a mace and right-click a raised enemy shield"); clicks.reset(); gestureOwned = false; }
}
