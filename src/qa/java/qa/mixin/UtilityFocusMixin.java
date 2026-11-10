package qa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Logical focus in this disposable QA JVM, without operating the user's cursor. */
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class UtilityFocusMixin {
    @Inject(method="isWindowActive",at=@At("HEAD"),cancellable=true)
    private void focus(CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(true);}
}
