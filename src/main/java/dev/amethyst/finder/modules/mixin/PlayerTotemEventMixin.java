package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ui.PlayerObservationTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only packets already applied by vanilla on the client thread. */
@Mixin(ClientPacketListener.class)
public abstract class PlayerTotemEventMixin {
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void tuntung$observedTotem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        PlayerObservationTracker.entityEvent((ClientPacketListener) (Object) this, packet);
        var c=net.minecraft.client.Minecraft.getInstance();
        if((Object)this==c.getConnection()&&c.level!=null&&packet.getEntity(c.level)==c.player&&dev.amethyst.finder.ChunkFinderClient.MODULES!=null) {
            if(packet.getEventId()==35)dev.amethyst.finder.ChunkFinderClient.MODULES.onSelfTotemPop(c);
            else if(packet.getEventId()==3)dev.amethyst.finder.ChunkFinderClient.MODULES.onSelfDeath(c);
        }
        if(packet.getEventId()==3&&c.level!=null)
            dev.amethyst.finder.modules.pvp.LootProtection.observedDeath(c,packet.getEntity(c.level));
        if(c.level!=null&&dev.amethyst.finder.ChunkFinderClient.MACE!=null)dev.amethyst.finder.ChunkFinderClient.MACE.entityEvent(c,packet.getEntity(c.level),packet.getEventId());
        if(packet.getEventId()==3&&c.level!=null&&dev.amethyst.finder.ChunkFinderClient.BOT!=null)
            dev.amethyst.finder.ChunkFinderClient.BOT.observedDeath(packet.getEntity(c.level));
    }
}
