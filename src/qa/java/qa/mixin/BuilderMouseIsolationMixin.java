package qa.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** This QA JVM never grabs or moves the user's mouse. Logical focus allows test-player inputs. */
@Mixin(MouseHandler.class)
public abstract class BuilderMouseIsolationMixin {
    @Inject(method={"turnPlayer","grabMouse","releaseMouse"},at=@At("HEAD"),cancellable=true)
    private void isolate(CallbackInfo ci){ci.cancel();}
    @Inject(method="isMouseGrabbed",at=@At("HEAD"),cancellable=true)
    private void grabbed(CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(true);}
}
