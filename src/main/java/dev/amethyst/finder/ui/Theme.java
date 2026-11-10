package dev.amethyst.finder.ui;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.core.Finding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Theme {
    public static final int PANEL = 0xFF15111F, CARD = 0xFF20192E, BORDER = 0xFF3A2A50;
    public static final int TEXT = 0xFFF2EDF9, MUTED = 0xFFA99ABF, PURPLE = 0xFFD09AFF, MINT = 0xFF8FE9C3, GOLD = 0xFFFFCA81;
    private Theme() {}
    public static int color(Finding f) {
        return f.kind() == Finding.Kind.GEODE ? PURPLE : f.score() >= 60 ? MINT : GOLD;
    }
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }
    public static void crystal(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 4, y, x + 8, y + 2, 0xFFE5C8FF);
        g.fill(x + 2, y + 2, x + 10, y + 4, PURPLE);
        g.fill(x, y + 4, x + 12, y + 10, 0xFFAD6BF0);
        g.fill(x + 2, y + 10, x + 10, y + 12, 0xFF844BC5);
        g.fill(x + 4, y + 12, x + 8, y + 14, 0xFF623396);
        g.fill(x + 2, y + 4, x + 5, y + 9, 0xFFD8ACFF);
    }
    public static String reasons(Finding f) {
        return String.join(" · ", f.reasons().stream().map(Theme::reason).toList());
    }
    private static String reason(String key) {
        var c = ChunkFinderClient.CONFIG;
        return switch (key) {
            case "storage" -> c.text("хранилища", "storage");
            case "chest" -> "chests";
            case "barrel" -> "barrels";
            case "shulker" -> c.text("шалкеры", "shulkers");
            case "ender" -> c.text("эндер-сундук", "ender chest");
            case "hopper" -> c.text("хопперы", "hoppers");
            case "furnace" -> c.text("печи", "furnaces");
            case "beacon" -> c.text("маяк", "beacon");
            case "enchanting" -> c.text("зачарование", "enchanting");
            case "anvil" -> c.text("наковальни", "anvils");
            case "crafting" -> c.text("верстаки", "crafting");
            case "redstone" -> c.text("редстоун", "redstone");
            case "bed" -> c.text("кровати", "beds");
            case "portal" -> c.text("портал", "portal");
            case "geode" -> c.text("аметист", "amethyst");
            case "mature_clusters" -> "mature clusters · history unknown";
            case "observed_growth" -> "observed bud growth";
            case "cluster_change" -> c.text("исчезли кристаллы", "clusters disappeared");
            case "spawner" -> "spawners";
            case "armor_stand" -> "armor stands";
            default -> key;
        };
    }
    public static void smallText(GuiGraphicsExtractor g, String s, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        g.text(font, font.plainSubstrByWidth(s, Math.max(1, width)), x, y, color, false);
    }
}
