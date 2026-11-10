package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import java.util.Map;

/** Remove frame caps on request without touching the world's visual settings. */
public final class SmoothProfile {
    public static final int UNLIMITED_FPS = 260;
    private SmoothProfile() {}
    public static Map<String,Object> state(Minecraft c) {
        var o=c.options;
        return Map.of("renderDistance",o.renderDistance().get(),"simulationDistance",o.simulationDistance().get(),
            "fpsLimit",o.framerateLimit().get(),"vsync",o.enableVsync().get(),
            "active",o.framerateLimit().get()==UNLIMITED_FPS&&!o.enableVsync().get(),
            "restoreAvailable",ChunkFinderClient.CONFIG.performanceProfileActive);
    }
    public static void apply(Minecraft c) {
        var o=c.options;
        var cfg=ChunkFinderClient.CONFIG;
        if(!cfg.performanceProfileActive){
            cfg.performancePreviousFps=o.framerateLimit().get();
            cfg.performancePreviousVsync=o.enableVsync().get();
            cfg.performanceProfileActive=true;cfg.save();
        }
        o.framerateLimit().set(UNLIMITED_FPS);o.enableVsync().set(false);
        o.save();
    }
    public static void restore(Minecraft c) {
        var cfg=ChunkFinderClient.CONFIG;
        if(!cfg.performanceProfileActive)return;
        c.options.framerateLimit().set(cfg.performancePreviousFps);
        c.options.enableVsync().set(cfg.performancePreviousVsync);
        c.options.save();cfg.performanceProfileActive=false;cfg.save();
    }
}
