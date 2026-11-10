package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;
import java.util.Map;

/** A local stream-safe profile: cosmetics and vanilla diagnostics stay available, gameplay modules stay off. */
public final class StreamerMode {
    private static boolean chartVisible;
    private StreamerMode() {}

    public static boolean allowsModule(String id) { return "client_maps".equals(id)||"map_in_slot".equals(id); }

    public static void set(Minecraft client,boolean enabled) {
        FinderConfig config=ChunkFinderClient.CONFIG;
        if(enabled) {
            // Run normal lifecycles first so cameras, movement and held inputs are restored cleanly.
            for(var entry:ModuleCatalog.ENTRIES)if(config.isModuleEnabled(entry.id())&&ModuleCatalog.available(entry.id())&&!allowsModule(entry.id()))
                ModuleControls.setEnabled(client,entry.id(),false);
        }
        config.streamerMode=enabled;
        config.save();
        play(client,enabled?1.35f:.75f);
        tick(client);
    }

    public static void play(Minecraft client,float pitch) {
        if(!ChunkFinderClient.CONFIG.moduleSounds||client.player==null)return;
        client.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME,.55f,pitch);
        client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.30f,pitch+0.35f);
    }

    public static void tick(Minecraft client) {
        boolean actual=client.getDebugOverlay().showProfilerChart();
        if(ChunkFinderClient.CONFIG.streamerMode) {
            boolean desired=ChunkFinderClient.CONFIG.streamerPieChart;
            if(actual!=desired)client.getDebugOverlay().toggleProfilerChart();
            chartVisible=desired;
        } else if(chartVisible) {
            if(actual)client.getDebugOverlay().toggleProfilerChart();
            chartVisible=false;
        }
    }

    public static Map<String,Object> state() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("active",ChunkFinderClient.CONFIG.streamerMode);
        result.put("pieChart",chartVisible);
        result.put("policy","Cosmetics + vanilla diagnostics only");
        return result;
    }
}
