package dev.amethyst.finder;

import net.minecraft.client.Minecraft;

/** Both the desktop switch and an in-game key use the same module lifecycle. */
public final class ModuleControls {
    private ModuleControls() {}
    public static void setEnabled(Minecraft client,String id,boolean enabled) {
        if(ModuleCatalog.find(id)==null||!ModuleCatalog.available(id))throw new IllegalArgumentException("Module is not available: "+id);
        if(enabled&&ChunkFinderClient.CONFIG.streamerMode&&!StreamerMode.allowsModule(id))
            throw new IllegalStateException("Streamer Mode keeps gameplay modules off. Turn Streamer Mode off first.");
        if("elytra_finder".equals(id))ChunkFinderClient.ELYTRA.setEnabled(client,enabled);
        else if("auto_vault".equals(id))ChunkFinderClient.VAULTS.setEnabled(client,enabled);
        else if(!ChunkFinderClient.MOVEMENT.setEnabled(id,enabled)&&!ChunkFinderClient.PVP.setEnabled(id,enabled)&&!ChunkFinderClient.MODULES.setEnabled(id,enabled))
            ChunkFinderClient.CONFIG.setModuleEnabled(id,enabled);
        if("client_maps".equals(id)||"map_in_slot".equals(id))MapArtModules.apply();
        if(java.util.Set.of("chunk_finder","storage_esp","amethyst_esp","spawner_chunks").contains(id))ChunkFinderClient.SCANNER.requestRescan();
        ChunkFinderClient.CONFIG.save();
        ChunkFinderClient.SCANNER.refresh(client);
        if(enabled)StreamerMode.play(client,1.15f);
    }
}
