package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.CursorSafety;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class CursorMouseMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean mouseGrabbed;
    @Shadow public abstract void setIgnoreFirstMove();

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void tuntung$releaseWithoutWarpAfterFocusLoss(CallbackInfo ci) {
        if (!minecraft.getWindow().isFocused()) {
            // Do not warp the pointer back into Minecraft after the user changes apps.
            mouseGrabbed = false;
            setIgnoreFirstMove();
            CursorSafety.releaseNative(minecraft.getWindow());
            ci.cancel();
        }
    }

    @Inject(method = "releaseMouse", at = @At("RETURN"))
    private void tuntung$releaseFullscreenConfinement(CallbackInfo ci) {
        CursorSafety.releaseNative(minecraft.getWindow());
    }
}
