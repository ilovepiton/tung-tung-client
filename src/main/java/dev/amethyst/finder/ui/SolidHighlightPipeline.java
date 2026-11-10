package dev.amethyst.finder.ui;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** Solid model colors with the original material alpha mask and vanilla GUI render state. */
public final class SolidHighlightPipeline {
    public static final RenderPipeline PIPELINE = create();
    private SolidHighlightPipeline() {}

    private static RenderPipeline create() {
        RenderPipeline original = RenderPipelines.GUI_TEXTURED;
        // The vanilla GUI snippets are private in 26.3. Snapshot the public pipeline state instead.
        RenderPipeline.Snippet gui = new RenderPipeline.Snippet(
                original.getShaders(), Optional.of(original.getShaderDefines()),
                Optional.of(original.getBindGroupLayouts()),
                original.getColorTargetStates().toArray(ColorTargetState[]::new),
                original.getColorTargetStates().size(),
                Optional.ofNullable(original.getDepthStencilState()),
                Optional.of(original.getPolygonMode()), Optional.of(original.isCull()),
                original.getVertexFormatBindings().toArray(VertexFormat[]::new),
                Optional.of(original.getPrimitiveTopology()), original.pushConstantSize());
        RenderPipeline.Builder builder = RenderPipeline.builder(gui)
                .withLocation(Identifier.fromNamespaceAndPath("amethyst_chunk_finder", "pipeline/solid_model_highlight"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("amethyst_chunk_finder", "core/solid_model_highlight"))
                // An empty optional explicitly keeps the GUI's disabled depth test.
                .withDepthStencilState(Optional.ofNullable(original.getDepthStencilState()));
        ((FabricRenderPipeline.Builder)builder).withUsePipelineDrawModeForGui(
                ((FabricRenderPipeline)original).usePipelineDrawModeForGui());
        return builder.build();
    }
}
