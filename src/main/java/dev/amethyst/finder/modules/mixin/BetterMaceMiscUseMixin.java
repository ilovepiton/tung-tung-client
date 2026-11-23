package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A shield-break gesture owns RMB; the same press must not also throw a wind charge. */
@Pseudo
@Mixin(targets="com.ritesh.feature.Misc",remap=false)
public abstract class BetterMaceMiscUseMixin {
 @Inject(method="tick",at=@At("HEAD"),cancellable=true,remap=false)
 private static void tung$ownedShieldClick(CallbackInfo ci){
  if(ChunkFinderClient.PVP!=null&&ChunkFinderClient.PVP.interceptShieldUse())ci.cancel();
 }
}
