package qa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.GuiGraphicsExtractor.class)
public abstract class UtilityLabelProbe {
    @Inject(method="text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",at=@At("HEAD"))
    private void label(net.minecraft.client.gui.Font font,String text,int x,int y,int color,boolean shadow,CallbackInfo ci){
        if(text.equals("Invisible")&&color==0xFFFF3B3B)qa.CombatUtilityQa.invisibleLabels++;
    }
}
