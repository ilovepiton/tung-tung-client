package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Re-scan only successful, relevant client block updates; a changed scan generation cannot publish mixed data. */
@Mixin(LevelChunk.class)
public abstract class ChunkDirtyMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void tung$changed(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk)(Object)this;
        if (chunk.getLevel() instanceof ClientLevel && cir.getReturnValue() != null) {
            ChunkFinderClient.SCANNER.onBlockChanged(chunk, cir.getReturnValue(), state);
            if (ChunkFinderClient.CAVES != null) ChunkFinderClient.CAVES.invalidate(chunk);
        }
    }
}
