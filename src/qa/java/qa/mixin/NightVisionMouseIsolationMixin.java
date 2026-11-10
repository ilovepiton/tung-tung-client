package qa.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Isolate only the disposable QA JVM from the user's mouse and cursor. */
@Mixin(MouseHandler.class)
public abstract class NightVisionMouseIsolationMixin {
    @Inject(method={"turnPlayer","grabMouse","releaseMouse"},at=@At("HEAD"),cancellable=true)
    private void leaveUserCursorAlone(CallbackInfo callback) { callback.cancel(); }
    @Inject(method="isMouseGrabbed",at=@At("HEAD"),cancellable=true)
    private void logicalFocus(CallbackInfoReturnable<Boolean> callback) { callback.setReturnValue(true); }
}
