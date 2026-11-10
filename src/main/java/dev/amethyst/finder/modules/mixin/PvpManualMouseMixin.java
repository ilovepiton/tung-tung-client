package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Receives real use-button edges even when GUI dispatch consumes the event. */
@Mixin(MouseHandler.class)
public abstract class PvpManualMouseMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void tuntung$manualButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if(window==minecraft.getWindow().handle())dev.amethyst.finder.MouseSounds.handle(minecraft,button.button(),action);
        if (window == 0 || window != minecraft.getWindow().handle() || ChunkFinderClient.PVP == null) return;
        ChunkFinderClient.PVP.onManualMouseButton(minecraft, button.button(), action);
        if(ChunkFinderClient.MACE!=null)ChunkFinderClient.MACE.mouseButton(minecraft,button.button(),action);
    }
}
