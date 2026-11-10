package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.XrayModule;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public abstract class XrayVisibilityMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void tung$unoccludeSections(BlockPos pos, CallbackInfo ci) {
        if (XrayModule.active()) ci.cancel();
    }
}
