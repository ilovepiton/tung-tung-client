package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.InvisMode;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class InvisPlayerMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
    private void tuntung$revealPlayer(Avatar entity,AvatarRenderState state,float partialTicks,CallbackInfo ci) {
        if(InvisMode.reveals(entity)){state.isInvisible=false;state.isInvisibleToPlayer=false;}
    }
}
