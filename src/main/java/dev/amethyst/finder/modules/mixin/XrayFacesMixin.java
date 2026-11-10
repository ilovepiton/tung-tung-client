package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.XrayModule;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class XrayFacesMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void tung$exposeFaces(BlockState state, BlockState neighbor, Direction face, CallbackInfoReturnable<Boolean> ci) {
        if (XrayModule.active() && !XrayModule.hidden(state) && XrayModule.hidden(neighbor)) ci.setReturnValue(true);
    }
}
