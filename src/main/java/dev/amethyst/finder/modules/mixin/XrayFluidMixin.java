package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.XrayModule;
import net.minecraft.client.renderer.block.FluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class XrayFluidMixin {
    /** Includes fluids inside waterlogged useful blocks. */
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void tung$hideFluids(CallbackInfo ci) {
        if (XrayModule.active()) ci.cancel();
    }
}
