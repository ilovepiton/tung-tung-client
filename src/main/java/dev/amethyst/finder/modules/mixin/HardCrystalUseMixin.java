package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop vanilla held-use repetition before block, entity or air-use packets are created. */
@Mixin(Minecraft.class)
public abstract class HardCrystalUseMixin {
 @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
 private void tung$oneGesture(CallbackInfo ci) {
  var hard=ChunkFinderClient.HARD_CRYSTAL;var client=(Minecraft)(Object)this;
  if(hard!=null&&hard.interceptsUse(client)&&!hard.beginGesture(client))ci.cancel();
 }
 @Inject(method="startUseItem",at=@At("RETURN"))
 private void tung$endGesture(CallbackInfo ci) {
  if(ChunkFinderClient.HARD_CRYSTAL!=null)ChunkFinderClient.HARD_CRYSTAL.endGesture();
 }
}
