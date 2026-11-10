package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.ModuleKeybinds;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class ModuleKeybindMixin {
    @Shadow @Final private Minecraft minecraft;
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void tuntung$moduleKey(long window,int action,KeyEvent key,CallbackInfo ci) {
        dev.amethyst.finder.KeyboardSounds.handle(minecraft,window,action,key);
        if(window==minecraft.getWindow().handle()&&ChunkFinderClient.MACE!=null)ChunkFinderClient.MACE.keyboard(minecraft,key.key(),action);
        if(ChunkFinderClient.CONFIG!=null&&ModuleKeybinds.handle(minecraft,window,action,key))ci.cancel();
    }
}
