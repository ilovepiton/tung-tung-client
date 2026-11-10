package dev.amethyst.finder.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

final class FinderSlider extends AbstractSliderButton {
    private final int min, max;
    private final String label, unit;
    private final IntConsumer setter;
    FinderSlider(int x, int y, int w, String label, String unit, int min, int max, int initial, IntConsumer setter) {
        super(x, y, w, 22, Component.empty(), (initial - min) / (double) (max - min));
        this.label = label; this.unit = unit; this.min = min; this.max = max; this.setter = setter;
        updateMessage();
    }
    private int number() { return (int) Math.round(min + value * (max - min)); }
    @Override protected void updateMessage() {
        if (label != null) setMessage(Component.literal(label + ": " + number() + unit));
    }
    @Override protected void applyValue() { if (setter != null) setter.accept(number()); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme.card(g, getX(), getY(), width, height, Theme.CARD);
        if (isHovered || isFocused()) g.outline(getX(), getY(), width, height, Theme.BORDER);
        int track = width - 16;
        g.fill(getX() + 8, getY() + height - 5, getX() + 8 + track, getY() + height - 3, Theme.BORDER);
        g.fill(getX() + 8, getY() + height - 5, getX() + 8 + (int) (track * value), getY() + height - 3, Theme.PURPLE);
        int thumb = getX() + 8 + (int) (track * value);
        g.fill(thumb - 2, getY() + height - 7, thumb + 2, getY() + height - 1, Theme.PURPLE);
        Theme.smallText(g, getMessage().getString(), getX() + 8, getY() + 4, width - 16, Theme.TEXT);
    }
}
