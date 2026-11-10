package dev.amethyst.finder;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

/** SDL relative mode and fullscreen window confinement are separate mouse locks. */
public final class CursorSafety {
    private CursorSafety() {}

    public static boolean gameplayOwnsMouse(Window window) {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getWindow() == window && client.mouseHandler != null
                && window.isFocused() && client.gui.screen() == null && client.gui.overlay() == null
                && client.mouseHandler.isMouseGrabbed();
    }

    public static void releaseNative(Window window) {
        if (window == null || window.handle() == 0) return;
        long handle = window.handle();
        if (SDLMouse.SDL_GetWindowRelativeMouseMode(handle)) SDLMouse.SDL_SetWindowRelativeMouseMode(handle, false);
        // GetWindowMouseGrab only reports the currently focused grabbed window;
        // its saved flag can otherwise reactivate confinement on focus return.
        if ((SDLVideo.SDL_GetWindowFlags(handle) & SDLVideo.SDL_WINDOW_MOUSE_GRABBED) != 0)
            SDLVideo.SDL_SetWindowMouseGrab(handle, false);
        if (!SDLMouse.SDL_CursorVisible()) SDLMouse.SDL_ShowCursor();
    }

    public static void tick(Minecraft client) {
        if (client.gui.screen() != null || client.gui.overlay() != null || !client.getWindow().isFocused()) {
            // releaseMouse may return early when its cached flag is already false.
            client.mouseHandler.releaseMouse();
        }
    }
}
