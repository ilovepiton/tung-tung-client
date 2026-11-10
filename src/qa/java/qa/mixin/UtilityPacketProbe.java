package qa.mixin;

import qa.CombatUtilityQa;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class UtilityPacketProbe {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void count(Packet<?> packet,CallbackInfo ci){
        if(packet instanceof ServerboundAttackPacket)CombatUtilityQa.attacks++;
        if(packet instanceof ServerboundContainerClickPacket click&&click.buttonNum()==40&&click.containerInput()==ContainerInput.SWAP){
            CombatUtilityQa.swaps++;
            if(CombatUtilityQa.popReceiptAt!=0){CombatUtilityQa.popRefillMs.add((System.nanoTime()-CombatUtilityQa.popReceiptAt)/1e6);CombatUtilityQa.popReceiptAt=0;}
            if(CombatUtilityQa.emptyReceiptAt!=0){CombatUtilityQa.refillMs.add((System.nanoTime()-CombatUtilityQa.emptyReceiptAt)/1e6);CombatUtilityQa.emptyReceiptAt=0;}
        }
    }
}
