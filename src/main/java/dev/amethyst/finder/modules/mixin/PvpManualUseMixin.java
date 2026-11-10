package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.modules.pvp.ManualCombatAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes real vanilla right-click placement before/after prediction; does not fabricate an input. */
@Mixin(MultiPlayerGameMode.class)
public abstract class PvpManualUseMixin {
    @Unique private ManualCombatAssist.Observation tuntung$manualUse;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void tuntung$capture(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        tuntung$manualUse = null;
        if (dev.amethyst.finder.modules.pvp.LootProtection.blocksUse(Minecraft.getInstance(),hand,hit)) { cir.setReturnValue(InteractionResult.FAIL); return; }
        if (ChunkFinderClient.PVP == null) return;
        Minecraft client = Minecraft.getInstance();
        if (ChunkFinderClient.PVP.blocksManualRepeat(client)) { cir.setReturnValue(InteractionResult.FAIL); return; }
        tuntung$manualUse = ChunkFinderClient.PVP.captureManualUse(client, hand, hit, player.getItemInHand(hand));
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void tuntung$observe(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        ManualCombatAssist.Observation observation = tuntung$manualUse;
        tuntung$manualUse = null;
        if (observation != null && ChunkFinderClient.PVP != null)
            ChunkFinderClient.PVP.onManualBlockUse(Minecraft.getInstance(), observation, cir.getReturnValue().consumesAction());
    }
}
