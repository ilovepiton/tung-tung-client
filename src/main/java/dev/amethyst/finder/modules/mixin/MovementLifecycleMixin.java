package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restore temporary singleplayer movement permissions before Minecraft saves the world. */
@Mixin(Minecraft.class)
public abstract class MovementLifecycleMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void tungBeforeDisconnect(Screen screen, boolean transferring, boolean clearResources, CallbackInfo ci) {
        restoreMovement();
    }

    @Inject(method = "exitWorldAndClose", at = @At("HEAD"))
    private void tungBeforeExit(CallbackInfo ci) {
        restoreMovement();
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void tungBeforeLevelClear(Screen screen, CallbackInfo ci) {
        restoreMovement();
    }

    @Unique
    private void restoreMovement() {
        if (ChunkFinderClient.MOVEMENT != null) ChunkFinderClient.MOVEMENT.beforeWorldExit((Minecraft) (Object) this);
    }
}
