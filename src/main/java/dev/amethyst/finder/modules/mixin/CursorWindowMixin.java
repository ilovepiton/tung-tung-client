package dev.amethyst.finder.modules.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.amethyst.finder.CursorSafety;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class CursorWindowMixin {
    @Redirect(method = "updateWindowMouseGrab", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLVideo;SDL_SetWindowMouseGrab(JZ)Z"))
    private boolean tuntung$confineOnlyDuringGameplay(long handle, boolean fullscreenGrab) {
        return SDLVideo.SDL_SetWindowMouseGrab(handle,
                fullscreenGrab && CursorSafety.gameplayOwnsMouse((Window) (Object) this));
    }

    @Inject(method = "onFocus", at = @At("RETURN"))
    private void tuntung$releaseOnFocusLoss(boolean focused, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!focused && client != null && client.getWindow() == (Object) this && client.mouseHandler != null) {
            dev.amethyst.finder.ModuleKeybinds.clearHeld();
            client.mouseHandler.releaseMouse();
            KeyMapping.releaseAll();
        }
    }
}
