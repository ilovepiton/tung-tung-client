package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Preserve a builder-owned vanilla break across ticks without holding the player's attack key. */
@Mixin(Minecraft.class)
public abstract class BuilderMiningMixin {
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void builderOwnsBreak(boolean attack,CallbackInfo ci) {
        if(ChunkFinderClient.AUTO_BUILDER.ownsMining((Minecraft)(Object)this))ci.cancel();
    }
}
