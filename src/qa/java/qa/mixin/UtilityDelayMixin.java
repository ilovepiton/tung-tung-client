package qa.mixin;

import qa.TotemPacketDelay;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class UtilityDelayMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"),cancellable=true)
    private void delay(Packet<?> packet,CallbackInfo ci){
        if(TotemPacketDelay.hold((ServerCommonPacketListenerImpl)(Object)this,packet))ci.cancel();
    }
}
