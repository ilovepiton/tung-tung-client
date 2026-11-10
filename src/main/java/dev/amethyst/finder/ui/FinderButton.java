package dev.amethyst.finder.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

final class FinderButton extends Button {
    private final BooleanSupplier selected;
    private final boolean warm;
    FinderButton(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier selected) {
        this(x,y,w,h,label,action,selected,false);
    }
    FinderButton(int x,int y,int w,int h,String label,Runnable action,BooleanSupplier selected,boolean warm) {
        super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);
        this.warm=warm;
        this.selected = selected;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean on = selected.getAsBoolean();
        int accent=warm?0xFFC89155:Theme.PURPLE;
        int fill = warm?(on?0xFF2A1D11:isHovered||isFocused()?0xFF23180F:0xFF171009):on ? 0xFF3D255B : isHovered || isFocused() ? 0xFF32243E : Theme.CARD;
        Theme.card(g, getX(), getY(), width, height, fill);
        if (isHovered || isFocused()) g.outline(getX(), getY(), width, height, accent);
        if (on) g.fill(getX() + 2, getY() + 5, getX() + 4, getY() + height - 5, accent);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), width - 14);
        g.text(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 9) / 2,
                active ? on ? accent : warm?0xFFF2EBE1:Theme.TEXT : warm?0xFF93897D:Theme.MUTED, false);
    }
}
