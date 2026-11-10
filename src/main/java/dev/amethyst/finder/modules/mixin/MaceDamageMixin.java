package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe damage applied by vanilla; a swing is not proof of a successful smash. */
@Mixin(ClientPacketListener.class)
public abstract class MaceDamageMixin {
 @Inject(method="handleDamageEvent",at=@At("TAIL"))
 private void tung$maceDamage(ClientboundDamageEventPacket packet,CallbackInfo ci){
  if(ChunkFinderClient.MACE!=null)ChunkFinderClient.MACE.damage(packet.entityId(),packet.sourceCauseId());
 }
}
