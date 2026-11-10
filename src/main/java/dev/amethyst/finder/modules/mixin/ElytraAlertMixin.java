package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.ui.ElytraHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Safety alert is extracted after screens and does not depend on the F1 HUD layer. */
@Mixin(Gui.class)
public abstract class ElytraAlertMixin {
 @Inject(method="extractRenderState",at=@At("TAIL"))
 private void tung$playerAlert(DeltaTracker delta,boolean renderHud,boolean renderScreen,CallbackInfo ci){
  var finder=ChunkFinderClient.ELYTRA;if(finder==null||!finder.enabled()||finder.players().isEmpty())return;
  var c=Minecraft.getInstance();var g=new GuiGraphicsExtractor(c,c.gameRenderer.gameRenderState().guiRenderState,0,0);g.nextStratum();ElytraHud.extractWarning(g);
 }
}
