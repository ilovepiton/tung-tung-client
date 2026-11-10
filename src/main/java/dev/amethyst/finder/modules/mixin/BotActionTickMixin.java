package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Interact after vanilla publishes movement and before the client-tick end packet. */
@Mixin(LocalPlayer.class)
public abstract class BotActionTickMixin {
 @Inject(method="sendPosition",at=@At("TAIL"))
 private void tuntung$combatWithinTick(CallbackInfo ci){if(ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled())ChunkFinderClient.MACE.afterMovement(Minecraft.getInstance());else if(ChunkFinderClient.BOT!=null)ChunkFinderClient.BOT.afterMovement(Minecraft.getInstance());}
}
