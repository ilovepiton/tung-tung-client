package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A detached camera must never mine, place or attack at its render position. */
@Mixin(Minecraft.class)
public abstract class FreecamInteractionMixin {
    @Unique private boolean tunTun$isDetached() { return ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.freecamActive(); }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void tunTun$blockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (tunTun$isDetached()) cir.setReturnValue(false);
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void tunTun$blockUse(CallbackInfo ci) { if (tunTun$isDetached()) ci.cancel(); }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void tunTun$blockMining(boolean down, CallbackInfo ci) { if (tunTun$isDetached()) ci.cancel(); }
}
