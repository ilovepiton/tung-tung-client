package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.XrayModule;
import dev.amethyst.finder.modules.NightVisionModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class XrayLightmapMixin {
    @Shadow private boolean needsUpdate;
    @Unique private boolean tung$wasFullbright;

    @Inject(method = "extract", at = @At("HEAD"))
    private void tung$refreshVanillaOnDisable(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        if (tung$wasFullbright && !XrayModule.active() && !NightVisionModule.active()) needsUpdate = true;
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void tung$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        boolean fullbright = XrayModule.active() || NightVisionModule.active();
        tung$wasFullbright = fullbright;
        if (fullbright) NightVisionModule.brighten(state);
    }
}
