package qa.mixin;

import qa.CombatUtilityQa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class UtilityReceiptProbe {
    @Inject(method="handleEntityEvent",at=@At("HEAD"))
    private void pop(ClientboundEntityEventPacket packet,CallbackInfo ci){
        var c=Minecraft.getInstance();
        if(c.isSameThread()&&c.player!=null&&c.level!=null&&packet.getEventId()==35&&packet.getEntity(c.level)==c.player
                &&!c.player.getMainHandItem().has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION))
            CombatUtilityQa.popReceiptAt=System.nanoTime();
    }
    private boolean armed(){var c=Minecraft.getInstance();return c.isSameThread()&&c.player!=null&&dev.amethyst.finder.ChunkFinderClient.CONFIG.isModuleEnabled("auto_totem")&&c.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);}
    @Inject(method="handleContainerSetSlot",at=@At("HEAD"))
    private void slot(ClientboundContainerSetSlotPacket packet,CallbackInfo ci){
        if(armed()&&packet.getContainerId()==0&&packet.getSlot()==45&&packet.getItem().isEmpty())CombatUtilityQa.emptyReceiptAt=System.nanoTime();
    }
    @Inject(method="handleSetPlayerInventory",at=@At("HEAD"))
    private void inventory(ClientboundSetPlayerInventoryPacket packet,CallbackInfo ci){
        if(armed()&&packet.slot()==40&&packet.contents().isEmpty())CombatUtilityQa.emptyReceiptAt=System.nanoTime();
    }
    @Inject(method="handleContainerContent",at=@At("HEAD"))
    private void content(ClientboundContainerSetContentPacket packet,CallbackInfo ci){if(armed()&&packet.containerId()==0&&packet.items().size()>45&&packet.items().get(45).isEmpty())CombatUtilityQa.emptyReceiptAt=System.nanoTime();}
}
