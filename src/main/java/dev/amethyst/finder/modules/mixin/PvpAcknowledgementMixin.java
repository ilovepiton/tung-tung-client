package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import dev.amethyst.finder.modules.pvp.PvpAcknowledgements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after vanilla has applied each authoritative packet on the client thread. */
@Mixin(ClientPacketListener.class)
public abstract class PvpAcknowledgementMixin {
    private void tuntung$receipt() {
        if(ChunkFinderClient.PVP!=null)ChunkFinderClient.PVP.onServerCombatUpdate(Minecraft.getInstance());
    }
    private void tuntung$inventoryReceipt() {
        tuntung$inventoryReceipt(false);
    }
    private void tuntung$inventoryReceipt(boolean fullInventory) {
        if(ChunkFinderClient.MODULES!=null)ChunkFinderClient.MODULES.onServerInventoryUpdate(Minecraft.getInstance(),fullInventory);
        tuntung$receipt();
    }
    @Inject(method={"handleAddEntity","handleBlockUpdate","handleChunkBlocksUpdate"},at=@At("TAIL"))
    private void tuntung$worldReceipt(CallbackInfo ci) { tuntung$receipt(); }
    @Inject(method="handleBlockUpdate",at=@At("HEAD"))
    private void tuntung$blockState(net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet,CallbackInfo ci) {
        if(net.minecraft.client.Minecraft.getInstance().isSameThread())PvpAcknowledgements.serverBlockUpdate((ClientPacketListener)(Object)this,packet.getPos(),packet.getBlockState());
    }
    @Inject(method="handleChunkBlocksUpdate",at=@At("HEAD"))
    private void tuntung$chunkBlockState(net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket packet,CallbackInfo ci) {
        if(net.minecraft.client.Minecraft.getInstance().isSameThread())packet.runUpdates((pos,state)->PvpAcknowledgements.serverBlockUpdate((ClientPacketListener)(Object)this,pos,state));
    }
    @Inject(method = "handleBlockChangedAck", at = @At("TAIL"))
    private void tuntung$blockAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
        PvpAcknowledgements.blockAck((ClientPacketListener) (Object) this, packet.sequence());
        tuntung$receipt();
    }

    @Inject(method = "handleSetPlayerInventory", at = @At("TAIL"))
    private void tuntung$inventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        PvpAcknowledgements.inventorySlot((ClientPacketListener) (Object) this, packet.slot(), packet.contents());
        tuntung$inventoryReceipt();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void tuntung$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        var c=Minecraft.getInstance();
        if (packet.getContainerId() == 0) PvpAcknowledgements.inventorySlot((ClientPacketListener) (Object) this,
                PvpAcknowledgements.inventorySlotForMenu(packet.getSlot()), packet.getItem());
        else if(c.player!=null&&packet.getContainerId()==c.player.containerMenu.containerId)
            tuntung$recordMenuSlot(packet.getSlot(),packet.getItem());
        tuntung$inventoryReceipt();
    }

    private void tuntung$recordMenuSlot(int index,net.minecraft.world.item.ItemStack stack) {
        var c=Minecraft.getInstance();var menu=c.player.containerMenu;
        if(index<0||index>=menu.slots.size())return;
        var slot=menu.slots.get(index);
        if(slot.container==c.player.getInventory())PvpAcknowledgements.inventorySlot(
            (ClientPacketListener)(Object)this,slot.getContainerSlot(),stack);
    }

    @Inject(method="handleSetEquipment",at=@At("TAIL"))
    private void tuntung$equipment(ClientboundSetEquipmentPacket packet,CallbackInfo ci) {
        var c=Minecraft.getInstance();
        if(c.player==null||packet.getEntity()!=c.player.getId())return;
        for(var pair:packet.getSlots()) {
            if(pair.getFirst()==net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                PvpAcknowledgements.inventorySlot((ClientPacketListener)(Object)this,40,pair.getSecond());
            else if(pair.getFirst()==net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                PvpAcknowledgements.inventorySlot((ClientPacketListener)(Object)this,c.player.getInventory().getSelectedSlot(),pair.getSecond());
        }
        tuntung$inventoryReceipt();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void tuntung$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (packet.containerId() == 0) for (int slot = 0; slot < Math.min(46, packet.items().size()); slot++)
            PvpAcknowledgements.inventorySlot((ClientPacketListener) (Object) this,
                    PvpAcknowledgements.inventorySlotForMenu(slot), packet.items().get(slot));
        if(packet.containerId()!=0&&Minecraft.getInstance().player!=null
                &&packet.containerId()==Minecraft.getInstance().player.containerMenu.containerId)
            for(int slot=0;slot<packet.items().size();slot++)tuntung$recordMenuSlot(slot,packet.items().get(slot));
        tuntung$inventoryReceipt(packet.containerId()==0);
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    private void tuntung$removed(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        for (int id : packet.entityIds()) PvpAcknowledgements.entityRemoved((ClientPacketListener) (Object) this, id);
        tuntung$receipt();
    }
}
