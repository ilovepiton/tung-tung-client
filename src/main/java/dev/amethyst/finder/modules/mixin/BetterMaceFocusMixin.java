package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Keep the unmodified mod idle while the desktop/menu or detached camera owns input. */
@Pseudo
@Mixin(targets="com.ritesh.feature.Coordinator",remap=false)
public abstract class BetterMaceFocusMixin {
 @Inject(method={"tick","onRender","onHit","onAttack","onBlockHit","onPearlThrown","onSplashPotThrown"},at=@At("HEAD"),cancellable=true,remap=false,require=0)
 private static void tung$pauseWithoutGameplayFocus(CallbackInfo ci){
  Minecraft client=Minecraft.getInstance();
  if(client.player==null||client.level==null||!client.isWindowActive()||client.gui.screen()!=null||client.gui.overlay()!=null||!client.mouseHandler.isMouseGrabbed()
   ||ChunkFinderClient.CONFIG!=null&&ChunkFinderClient.CONFIG.streamerMode
   ||ChunkFinderClient.MODULES!=null&&ChunkFinderClient.MODULES.freecamActive())ci.cancel();
 }
}
