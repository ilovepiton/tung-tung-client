package dev.amethyst.finder.modules;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Reveals received invisible players locally, without changing their actual entity flags. */
public final class InvisMode {
    public static final String LABEL="Invisible";
    public static final int LABEL_COLOR=0xFFFF3B3B;
    private InvisMode() {}
    public static boolean active() {
        var client=Minecraft.getInstance();
        return ChunkFinderClient.CONFIG!=null&&ChunkFinderClient.CONFIG.isModuleEnabled("invis_mode")
            &&client.player!=null&&client.level!=null;
    }
    public static boolean reveals(Entity entity) {
        return active()&&entity instanceof net.minecraft.world.entity.player.Player player
            &&entity!=Minecraft.getInstance().player&&player.isInvisible()&&player.isAlive()&&!player.isRemoved()&&!player.isSpectator();
    }
    public static String status() { return active()?"Invisible player avatars revealed · red Invisible labels":"Disabled"; }
}
