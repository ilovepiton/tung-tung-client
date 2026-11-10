package dev.amethyst.finder.modules.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Submit the captured model surfaces through the normal GUI render-state lifecycle. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
    @Accessor("guiRenderState")
    GuiRenderState tuntung$guiRenderState();
}
