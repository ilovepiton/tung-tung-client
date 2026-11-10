package dev.amethyst.finder.ui;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.core.Finding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Radar {
    private Radar() {}
    public static void draw(GuiGraphicsExtractor g, int x, int y, int size, boolean compact) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        var scanner = ChunkFinderClient.SCANNER;
        int radius = compact ? Math.min(ChunkFinderClient.CONFIG.radiusChunks, 8) : ChunkFinderClient.CONFIG.radiusChunks;
        int cells = radius * 2 + 1;
        double step = (size - 12.0) / cells;
        int cx = client.player.chunkPosition().x(), cz = client.player.chunkPosition().z();
        Theme.card(g, x, y, size, size, 0xFF100D17);
        g.outline(x, y, size, size, Theme.BORDER);
        for (int i = 0; i <= cells; i++) {
            int p = (int) (6 + i * step);
            g.fill(x + p, y + 6, x + p + 1, y + size - 6, 0xFF292035);
            g.fill(x + 6, y + p, x + size - 6, y + p + 1, 0xFF292035);
        }
        g.enableScissor(x + 5, y + 5, x + size - 5, y + size - 5);
        for (Finding f : scanner.findings()) {
            int dx = f.evidence().chunkX - cx, dz = f.evidence().chunkZ - cz;
            if (Math.abs(dx) > radius || Math.abs(dz) > radius) continue;
            int px = x + 6 + (int) ((dx + radius) * step), py = y + 6 + (int) ((dz + radius) * step);
            int side = Math.max(2, (int) step - 1);
            int color = scanner.isLoaded(f) ? Theme.color(f) : (Theme.color(f) & 0xffffff) | 0x66000000;
            g.fill(px + 1, py + 1, px + side, py + side, color);
            if (scanner.waypoint != null && scanner.waypoint.key() == f.key()) g.outline(px, py, side + 1, side + 1, Theme.TEXT);
        }
        int centerX = x + size / 2, centerY = y + size / 2;
        double angle = Math.toRadians(client.player.getYRot());
        for (int i = 2; i <= 8; i++) {
            int px = centerX - (int) (Math.sin(angle) * i), py = centerY + (int) (Math.cos(angle) * i);
            g.fill(px, py, px + 2, py + 2, Theme.TEXT);
        }
        g.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, Theme.TEXT);
        g.disableScissor();
        g.text(client.font, "N", x + size / 2 - 3, y + 2, Theme.MUTED, false);
    }
}
