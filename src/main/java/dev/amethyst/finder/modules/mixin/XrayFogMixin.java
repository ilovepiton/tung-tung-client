package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.XrayModule;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class XrayFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void tung$clearFog(CallbackInfoReturnable<FogData> ci) {
        if (!XrayModule.active()) return;
        FogData fog = ci.getReturnValue();
        fog.environmentalStart = fog.renderDistanceStart = 1_000_000;
        fog.environmentalEnd = fog.renderDistanceEnd = 1_000_001;
    }
}
