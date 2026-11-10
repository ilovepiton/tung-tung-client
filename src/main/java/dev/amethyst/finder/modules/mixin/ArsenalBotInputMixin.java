package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Per-tick input only: vanilla physical keys are never overwritten or left held. */
@Mixin(KeyboardInput.class)
public abstract class ArsenalBotInputMixin extends ClientInput {
    @Inject(method="tick",at=@At("TAIL"))
    private void tuntung$botMovement(CallbackInfo ci) {
        if(ChunkFinderClient.BOT==null)return;
        var next=ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.enabled()?ChunkFinderClient.MACE.movement(Minecraft.getInstance(),keyPresses):ChunkFinderClient.BOT.movement(Minecraft.getInstance(),keyPresses);
        if(next.equals(keyPresses))return;
        keyPresses=next;
        moveVector=new Vec2((next.left()?1:0)-(next.right()?1:0),(next.forward()?1:0)-(next.backward()?1:0)).normalized();
    }
}
