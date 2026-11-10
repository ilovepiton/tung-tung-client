package dev.amethyst.finder;

import net.fabricmc.loader.api.FabricLoader;

/** Runtime switches for the two separately packaged, MIT-licensed map add-ons. */
public final class MapArtModules {
    private static boolean warned;
    private MapArtModules() {}

    public static boolean available(String id) {
        return switch (id) {
            case "client_maps" -> FabricLoader.getInstance().isModLoaded("client_maps");
            case "map_in_slot" -> FabricLoader.getInstance().isModLoaded("map-in-slot");
            default -> false;
        };
    }

    public static void apply() {
        if (!available("client_maps")) return;
        try {
            Class<?> type=Class.forName("com.redstoned.client_maps.ClientMaps",false,MapArtModules.class.getClassLoader());
            type.getField("disabled").setBoolean(null,!ChunkFinderClient.CONFIG.clientMapsEnabled);
            warned=false;
        } catch (ReflectiveOperationException | LinkageError failure) {
            if(!warned)ChunkFinderClient.LOGGER.warn("Cannot apply the Client Maps runtime switch",failure);
            warned=true;
        }
    }

    public static String status(String id) {
        if(!available(id))return "Map add-on is not installed";
        return switch(id) {
            case "client_maps" -> ChunkFinderClient.CONFIG.clientMapsEnabled
                    ? "Saving and restoring received map art per server" : "Map storage paused";
            case "map_in_slot" -> ChunkFinderClient.CONFIG.mapInSlotEnabled
                    ? "Filled maps render inside inventory slots" : "Inventory map previews hidden";
            default -> "";
        };
    }
}
