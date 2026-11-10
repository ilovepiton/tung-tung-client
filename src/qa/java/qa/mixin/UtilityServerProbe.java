package qa.mixin;

import qa.CombatUtilityQa;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UtilityServerProbe {
    @Shadow public ServerPlayer player;
    @Inject(method="handleContainerClick",at=@At("HEAD"),cancellable=true)
    private void reject(ServerboundContainerClickPacket packet,CallbackInfo ci){
        if(!player.level().getServer().isSameThread()||packet.buttonNum()!=40||packet.containerInput()!=ContainerInput.SWAP)return;
        CombatUtilityQa.serverSwaps++;
        if(CombatUtilityQa.rejectNext){CombatUtilityQa.rejectNext=false;CombatUtilityQa.rejections++;player.containerMenu.sendAllDataToRemote();ci.cancel();}
    }
}
