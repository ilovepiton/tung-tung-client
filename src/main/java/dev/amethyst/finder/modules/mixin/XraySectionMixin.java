package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.XrayModule;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionCompiler.class)
public abstract class XraySectionMixin {
    /** Substitute only the mesh compiler's local read, never the world or interaction state. */
    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState tung$hideTerrain(RenderSectionRegion region, BlockPos pos) {
        BlockState state = region.getBlockState(pos);
        return XrayModule.meshState(region, pos, state);
    }
}
