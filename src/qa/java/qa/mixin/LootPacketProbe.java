package qa.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class LootPacketProbe {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void count(Packet<?> packet,CallbackInfo callback) {
        if(packet instanceof ServerboundAttackPacket)qa.LootQa.attacks++;
        if(packet instanceof ServerboundUseItemOnPacket)qa.LootQa.uses++;
    }
}
