package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Hud.class)
public abstract class CrosshairStyleMixin {
    @ModifyArg(method="extractCrosshair",at=@At(value="INVOKE",ordinal=0,
        target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),index=0)
    private RenderPipeline tuntung$translucentCrosshair(RenderPipeline vanilla) {
        return ChunkFinderClient.CONFIG != null && ChunkFinderClient.CONFIG.thinCrosshair ? RenderPipelines.GUI_TEXTURED : vanilla;
    }
}
