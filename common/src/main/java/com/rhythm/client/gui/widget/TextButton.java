package com.rhythm.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Custom styled text button that renders only text.
 * <p>
 * No background or border rendering - just text to avoid blur effects
 * when used with custom GUI systems.
 */
public class TextButton extends AbstractWidget {

    // ==================== Constants ====================

    private static final int HOVER_TEXT_COLOR = 0xFFFFFFFF;
    private static final int NORMAL_TEXT_COLOR = 0xFFCCCCCC;
    private static final int FONT_HEIGHT = 8;

    // ==================== Fields ====================

    private final Runnable onClick;

    // ==================== Constructor ====================

    public TextButton(int x, int y, int width, int height, String text, Runnable onClick) {
        super(x, y, width, height, Component.literal(text));
        this.onClick = onClick;
    }

    // ==================== Rendering ====================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int textColor = isHovered ? HOVER_TEXT_COLOR : NORMAL_TEXT_COLOR;

        String text = getMessage().getString();
        int textX = getX() + (width - font.width(text)) / 2;
        int textY = getY() + (height - FONT_HEIGHT) / 2;

        graphics.drawString(font, text, textX, textY, textColor, true);
    }

    // ==================== Interaction ====================

    @Override
    public void onClick(double mouseX, double mouseY) {
        onClick.run();
    }

    // ==================== Narration ====================

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

