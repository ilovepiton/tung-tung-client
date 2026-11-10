package dev.amethyst.finder.modules;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

/** Full-bright local lighting without changing potion effects, gamma or world light. */
public final class NightVisionModule {
    private NightVisionModule() {}

    public static boolean active() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.player != null && ChunkFinderClient.CONFIG != null
                && ChunkFinderClient.CONFIG.isModuleEnabled("night_vision");
    }

    /** Shared with X-ray; vanilla extracts fresh state again after the override ends. */
    public static void brighten(LightmapRenderState state) {
        state.needsUpdate = true;
        state.blockFactor = state.skyFactor = state.brightness = state.nightVisionEffectIntensity = 1;
        state.blockLightTint = state.skyLightColor = state.ambientColor = state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
        state.darknessEffectScale = state.bossOverlayWorldDarkening = 0;
    }

    public static String status() {
        return active() ? "Full brightness · local lighting" : "Disabled";
    }
}
