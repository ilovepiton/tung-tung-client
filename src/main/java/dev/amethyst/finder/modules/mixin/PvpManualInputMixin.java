package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops held-RMB repeats across block/entity/air paths while an assist owns the hand. */
@Mixin(Minecraft.class)
public abstract class PvpManualInputMixin {
    @Shadow private int rightClickDelay;
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void tuntung$ownedUse(CallbackInfo ci) {
        if (ChunkFinderClient.PVP != null && ChunkFinderClient.PVP.blocksManualRepeat((Minecraft) (Object) this)) ci.cancel();
    }
    @Inject(method="startUseItem",at=@At("RETURN"))
    private void tuntung$fastHeldCrystal(CallbackInfo ci) {
        Minecraft client=(Minecraft)(Object)this;
        var config=ChunkFinderClient.CONFIG;
        if(config!=null&&config.pvpManualAssist&&config.pvpFastAssist&&config.pvpCrystalRepeat&&config.isModuleEnabled("auto_crystal")
                &&client.player!=null&&(client.player.getMainHandItem().is(net.minecraft.world.item.Items.END_CRYSTAL)
                ||client.player.getOffhandItem().is(net.minecraft.world.item.Items.END_CRYSTAL)))rightClickDelay=Math.min(rightClickDelay,1);
    }
}
