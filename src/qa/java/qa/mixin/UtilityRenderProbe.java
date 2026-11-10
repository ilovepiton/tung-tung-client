package qa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
public abstract class UtilityRenderProbe {
    @Inject(method="submitHandsWithItems",at=@At("TAIL"))
    private void hands(CallbackInfo ci){qa.CombatUtilityQa.handsSubmitted++;}
}
