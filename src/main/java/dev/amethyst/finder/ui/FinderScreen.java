package dev.amethyst.finder.ui;

import dev.amethyst.finder.*;
import dev.amethyst.finder.core.Finding;
import dev.amethyst.finder.core.Signal;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

public final class FinderScreen extends Screen {
    private final FinderConfig c = ChunkFinderClient.CONFIG;
    private final ChunkScanner scanner = ChunkFinderClient.SCANNER;
    private int tab, page, px, py, pw, ph, top, contentHeight, rows;
    private long selected = Long.MIN_VALUE;
    private int refreshTick;
    private String notice = "";

    public FinderScreen() { super(Component.literal("Tung Tung Client")); }
    public FinderScreen(int tab) { this(); this.tab = tab; }
    private String t(String ru, String en) { return c.text(ru, en); }
    private void changed() { c.save(); scanner.refresh(minecraft); rebuildWidgets(); }

    @Override protected void init() {
        pw = Math.min(680, width - 20); ph = Math.min(380, height - 20);
        px = (width - pw) / 2; py = (height - ph) / 2;
        top = py + 67; contentHeight = ph - 96;
        button(px + pw - 63, py + 9, 30, 20, c.language.toUpperCase(), () -> {
            c.language = "ru".equals(c.language) ? "en" : "ru"; changed();
        }, false);
        button(px + pw - 28, py + 9, 20, 20, "x", this::onClose, false);
        int tabWidth = (pw - 28) / 3;
        String[] tabs = {t("Поиск", "Scan"), t("Методы", "Methods"), t("Находки", "Findings")};
        for (int i = 0; i < 3; i++) {
            int index = i;
            button(px + 12 + i * (tabWidth + 2), py + 36, tabWidth, 22, tabs[i], () -> {
                tab = index; notice = ""; rebuildWidgets();
            }, tab == i);
        }
        if (tab == 0) initScan();
        else if (tab == 1) initMethods();
        else initFindings();
    }

    private FinderButton button(int x, int y, int w, int h, String label, Runnable action, boolean selected) {
        return addRenderableWidget(new FinderButton(x, y, w, h, label, action, () -> selected));
    }
    private FinderButton toggle(int x, int y, int w, String label, boolean value, Runnable action) {
        return button(x, y, w, 22, label + "  " + (value ? "ON" : "OFF"), () -> { action.run(); changed(); }, value);
    }
    private int controlWidth() { return Math.min(245, (pw - 42) / 2); }
    private void initScan() {
        int x = px + 14, w = controlWidth(), gap = Math.clamp((contentHeight - 4) / 5, 22, 31);
        toggle(x, top, w, t("Chunk Finder", "Chunk Finder"), c.enabled, () -> c.enabled = !c.enabled);
        var sensitivity = addRenderableWidget(new FinderSlider(x, top + gap, w, t("Чувствительность", "Sensitivity"), "", 10, 90, c.sensitivity,
                v -> { c.sensitivity = v; scanner.refresh(minecraft); }));
        sensitivity.setTooltip(Tooltip.create(Component.literal(t("Выше — больше слабых сигналов. Оценка не является вероятностью.", "Higher values include weaker signals. Scores are not probabilities."))));
        var radius = addRenderableWidget(new FinderSlider(x, top + gap * 2, w, t("Радиус", "Radius"), t(" чанков", " chunks"), 2, 32, c.radiusChunks,
                v -> c.radiusChunks = v));
        radius.setTooltip(Tooltip.create(Component.literal(t("Ограничен чанками, полученными от сервера. Не меняет simulation distance.", "Limited to chunks received from the server. Does not change simulation distance."))));
        addRenderableWidget(new FinderSlider(x, top + gap * 3, w, t("Бюджет скана", "Scan budget"), " ms", 1, 6, c.budgetMs,
                v -> c.budgetMs = v));
        toggle(x, top + gap * 4, w, t("Радар в игре", "In-game radar"), c.hud, () -> c.hud = !c.hud);
    }
    private void initMethods() {
        int x = px + 14, w = Math.min(185, (pw - 40) / 2);
        var storage = toggle(x, top, w, "Chest / Storage", c.storage, () -> c.storage = !c.storage);
        storage.setTooltip(Tooltip.create(Component.literal(t("Сундуки, бочки, шалкеры, эндер-сундуки, хопперы и печи. Сундуки также бывают в природных структурах.",
                "Chests, barrels, shulkers, ender chests, hoppers and furnaces. Natural structures can contain storage."))));
        var amethyst = toggle(x, top + 28, w, "Amethyst", c.amethyst, () -> c.amethyst = !c.amethyst);
        amethyst.setTooltip(Tooltip.create(Component.literal(t("Жеоды, зрелые кристаллы и исчезновение кристаллов между наблюдениями. Аметист сам по себе не доказывает наличие базы.",
                "Geodes, mature clusters and clusters disappearing between observations. Amethyst alone does not prove a base exists."))));
        var builds = toggle(x, top + 56, w, t("Постройки", "Build signals"), c.builds, () -> c.builds = !c.builds);
        builds.setTooltip(Tooltip.create(Component.literal(t("Маяки, зачарование, наковальни, верстаки, редстоун, кровати, порталы и факелы.",
                "Beacons, enchanting, anvils, crafting, redstone, beds, portals and torches."))));
        toggle(x, top + 86, w, t("Уведомления", "Notifications"), c.notifications, () -> c.notifications = !c.notifications);
        button(x + w + 10, top + 86, pw - w - 38, 22, t("Очистить историю", "Clear history"), () -> {
            scanner.clear(); selected = Long.MIN_VALUE; notice = t("История очищена", "History cleared"); rebuildWidgets();
        }, false);
    }
    private Finding selection() {
        return scanner.findings().stream().filter(f -> f.key() == selected).findFirst().orElse(null);
    }
    private void initFindings() {
        List<Finding> list = scanner.findings();
        rows = Math.max(1, (contentHeight - 82) / 25);
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        button(px + pw - 62, top - 1, 22, 20, "<", () -> { page--; rebuildWidgets(); }, false).active = page > 0;
        button(px + pw - 36, top - 1, 22, 20, ">", () -> { page++; rebuildWidgets(); }, false).active = page + 1 < pages;
        for (int i = 0; i < rows; i++) {
            int index = page * rows + i;
            if (index >= list.size()) break;
            Finding f = list.get(index);
            String kind = f.kind() == Finding.Kind.GEODE ? t("Жеода", "Geode") : f.score() + "/100";
            long distance = minecraft.player == null ? 0 : Math.round(f.distance(minecraft.player.getX(), minecraft.player.getZ()));
            String label = kind + "  ·  X " + f.x() + " Z " + f.z() + "  ·  " + distance + "m  ·  "
                    + (scanner.isLoaded(f) ? t("загружен", "loaded") : t("из памяти", "cached"));
            FinderButton row = button(px + 14, top + 24 + i * 25, pw - 28, 22, label, () -> {
                selected = f.key(); rebuildWidgets();
            }, selected == f.key());
            row.setTooltip(Tooltip.create(Component.literal(Theme.reasons(f))));
        }
        int w = (pw - 36) / 3, y = top + contentHeight - 22;
        button(px + 14, y, w, 22, t("Метка", "Waypoint"), () -> {
            Finding f = selection();
            if (f != null) scanner.waypoint = scanner.waypoint != null && scanner.waypoint.key() == f.key() ? null : f;
        }, scanner.waypoint != null).active = selection() != null;
        button(px + 18 + w, y, w, 22, t("Копировать XYZ", "Copy XYZ"), () -> {
            Finding f = selection();
            if (f != null) { minecraft.keyboardHandler.setClipboard(f.x() + " " + f.y() + " " + f.z()); notice = t("Координаты скопированы", "Coordinates copied"); }
        }, false).active = selection() != null;
        button(px + 22 + w * 2, y, w, 22, t("Экспорт CSV", "Export CSV"), () -> {
            try {
                var file = FindingExporter.export(scanner.findings(), minecraft.level == null ? "" : minecraft.level.dimension().toString());
                notice = t("Сохранено: amethyst-exports/", "Saved: amethyst-exports/") + file.getFileName();
            } catch (IOException ex) { notice = t("Ошибка экспорта: ", "Export failed: ") + ex.getMessage(); }
        }, false).active = !list.isEmpty();
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xB5090710);
        Theme.card(g, px - 2, py - 2, pw + 4, ph + 4, Theme.BORDER);
        Theme.card(g, px, py, pw, ph, Theme.PANEL);
        g.fill(px + 10, py + 1, px + pw - 10, py + 2, Theme.PURPLE);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Theme.crystal(g, px + 14, py + 12);
        Theme.smallText(g, "TUNG TUNG CLIENT", px + 35, py + 14, pw - 105, Theme.TEXT);
        if (tab == 0) drawScan(g);
        else if (tab == 1) drawMethods(g);
        else drawFindings(g);
        g.fill(px + 12, py + ph - 28, px + pw - 12, py + ph - 27, Theme.BORDER);
        String footer = !c.saveError.isEmpty() ? t("Не удалось сохранить настройки", "Could not save settings")
                : !notice.isEmpty() ? notice : t("Right Shift · меню   K · поиск   Только полученные чанки", "Right Shift · menu   K · scan   Received chunks only");
        Theme.smallText(g, footer, px + 14, py + ph - 17, pw - 28, Theme.MUTED);
        super.extractRenderState(g, mx, my, delta);
    }
    private void drawScan(GuiGraphicsExtractor g) {
        int rx = px + controlWidth() + 28, rw = pw - controlWidth() - 42;
        Theme.smallText(g, t("Загружено: ", "Loaded: ") + scanner.loadedCount() + t(" · Найдено: ", " · Found: ") + scanner.findings().size(), rx, top + 2, rw, Theme.MUTED);
        int size = Math.min(rw, contentHeight - 30);
        Radar.draw(g, rx + (rw - size) / 2, top + 16, size, false);
        Theme.smallText(g, t("Сканы: ", "Scans: ") + scanner.completedScans + " · "
                + String.format(java.util.Locale.ROOT, "%.1f ms", scanner.lastWorkMs), rx, top + contentHeight - 9, rw, Theme.MUTED);
    }
    private void drawMethods(GuiGraphicsExtractor g) {
        int x = px + Math.min(185, (pw - 40) / 2) + 24, w = px + pw - 14 - x;
        Theme.smallText(g, t("Хранилища и механизмы", "Storage and machinery"), x, top + 7, w, Theme.TEXT);
        Theme.smallText(g, t("Жеоды, рост и изменения", "Geodes, growth and changes"), x, top + 35, w, Theme.PURPLE);
        Theme.smallText(g, t("Блоки и комбинации", "Blocks and combinations"), x, top + 63, w, Theme.TEXT);
        if (contentHeight > 150) {
            g.textWithWordWrap(font, Component.literal(t("Оценка 0–100 — сила сигнала. Природные структуры тоже могут содержать сундуки и редстоун. Жеода сама по себе не считается базой.",
                    "Scores from 0 to 100 describe signal strength. Natural structures may contain chests and redstone. A geode alone is not classified as a base.")),
                    px + 15, top + 124, pw - 30, Theme.MUTED, false);
            g.textWithWordWrap(font, Component.literal(t("Радиус не меняет дальность симуляции сервера. Повторное сравнение работает, пока чанк остаётся загруженным.",
                    "Radius does not change server simulation distance. Repeated comparisons require a continuously loaded chunk.")),
                    px + 15, top + 172, pw - 30, Theme.MUTED, false);
        }
    }
    private void drawFindings(GuiGraphicsExtractor g) {
        var list = scanner.findings();
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        Theme.smallText(g, t("Найдено: ", "Found: ") + list.size() + " · " + (page + 1) + "/" + pages, px + 14, top + 5, pw - 84, Theme.MUTED);
        if (list.isEmpty()) {
            Theme.smallText(g, c.enabled ? t("Пока нет сигналов. Исследуй загруженные чанки.", "No signals yet. Explore loaded chunks.")
                    : t("Включи Chunk Finder на вкладке «Поиск».", "Enable Chunk Finder in the Scan tab."), px + 14, top + 30, pw - 28, Theme.MUTED);
        }
        Finding f = selection();
        int y = top + contentHeight - 52;
        if (f == null) {
            Theme.smallText(g, t("Выбери строку для метки и деталей", "Select a row for details and a waypoint"), px + 14, y, pw - 28, Theme.MUTED);
        } else {
            Theme.smallText(g, Theme.reasons(f), px + 14, y, pw - 28, Theme.color(f));
            String detail = "Chest " + f.evidence().count(Signal.CHEST) + " · Hopper " + f.evidence().count(Signal.HOPPER)
                    + " · Cluster " + f.evidence().count(Signal.CLUSTER) + " · " + t("Y≈", "Y≈") + f.y() + t(" · центр чанка", " · chunk center");
            Theme.smallText(g, detail, px + 14, y + 13, pw - 28, Theme.MUTED);
        }
    }
    @Override public void tick() {
        if (tab == 2 && ++refreshTick % 20 == 0) rebuildWidgets();
    }
    @Override public void removed() { c.save(); }
    @Override public void onClose() { c.save(); super.onClose(); }
    @Override public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
