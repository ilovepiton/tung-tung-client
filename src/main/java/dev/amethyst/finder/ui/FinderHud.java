package dev.amethyst.finder.ui;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.core.Finding;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FinderHud {
    private FinderHud() {}
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var client = Minecraft.getInstance();
        var config = ChunkFinderClient.CONFIG;
        var scanner = ChunkFinderClient.SCANNER;
        if (client.player == null || !config.hud || !config.enabled || client.gui.hud.isHidden() || client.gui.screen() != null) return;
        int size = 106, x = g.guiWidth() - size - 10, y = 10;
        Theme.card(g, x - 4, y - 4, size + 8, size + 43, Theme.PANEL);
        Theme.crystal(g, x + 3, y);
        g.text(client.font, "Tung Tung Client", x + 22, y + 3, Theme.PURPLE, false);
        Radar.draw(g, x, y + 20, size, true);
        Theme.smallText(g, scanner.findings().size() + config.text(" метки · ", " marks · ") + scanner.loadedCount()
                + config.text(" ч.", " ch"), x + 2, y + size + 25, size - 2, Theme.MUTED);
        Finding waypoint = scanner.waypoint;
        if (waypoint != null) {
            double dx = waypoint.x() - client.player.getX(), dz = waypoint.z() - client.player.getZ();
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            double relative = ((yaw - client.player.getYRot() + 540) % 360) - 180;
            String direction = Math.abs(relative) <= 25 ? config.text("Впереди", "Ahead")
                    : Math.abs(relative) >= 155 ? config.text("Сзади", "Behind")
                    : relative < 0 ? config.text("Слева", "Left") : config.text("Справа", "Right");
            String line = direction + " · " + Math.round(Math.hypot(dx, dz)) + "m · X " + waypoint.x() + " Z " + waypoint.z();
            int w = client.font.width(line) + 20;
            int px = (g.guiWidth() - w) / 2;
            Theme.card(g, px, 10, w, 23, Theme.PANEL);
            g.text(client.font, line, px + 10, 17, Theme.color(waypoint), false);
        }
    }
}
