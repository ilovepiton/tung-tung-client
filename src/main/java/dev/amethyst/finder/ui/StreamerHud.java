package dev.amethyst.finder.ui;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small local watermark for stream recordings; contains no coordinates or hidden world data. */
public final class StreamerHud {
    private StreamerHud() {}
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var client=Minecraft.getInstance();var c=ChunkFinderClient.CONFIG;
        if(!c.streamerMode||!c.streamerWatermark||client.player==null||client.gui.hud.isHidden())return;
        String title="TUNG TUNG · STREAM SAFE";
        int width=client.font.width(title)+18;
        Theme.card(g,8,8,width,24,0xD9100B0D);
        g.fill(12,14,15,26,0xFFFF3364);
        g.text(client.font,title,20,16,0xFFFFD7E1,false);
    }
}
