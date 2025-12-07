package com.rhythm.client.gui.widget;

import com.rhythm.RhythmMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Custom toggle button that renders PNG texture + text label.
 * <p>
 * No background or border rendering - just the image and label to avoid
 * blur effects when used with custom GUI systems.
 */
public class ToggleButton extends AbstractWidget {

    // ==================== Textures ====================

    private static final ResourceLocation ON_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "textures/gui/on.png");
    private static final ResourceLocation OFF_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "textures/gui/off.png");
    private static final ResourceLocation LOOP_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "textures/gui/loop.png");
    private static final ResourceLocation LOCK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "textures/gui/lock.png");

    // ==================== Constants ====================

    private static final int ICON_SIZE = 16;
    private static final int ICON_PADDING_LEFT = 2;
    private static final int TEXT_PADDING = 4;
    private static final int FONT_HEIGHT = 8;

    private static final float FULL_BRIGHTNESS = 1.0f;
    private static final float DIM_BRIGHTNESS = 0.5f;
    private static final float FULL_ALPHA = 1.0f;

    private static final int TOGGLED_ON_TEXT_COLOR = 0xFF00FF88;
    private static final int TOGGLED_OFF_TEXT_COLOR = 0xFF888888;

    // ==================== Types ====================

    public enum ToggleType {
        ON_OFF,
        LOOP,
        LOCK
    }

    // ==================== Fields ====================

    private final Consumer<Boolean> onToggle;
    private final ToggleType type;
    private final String label;
    private boolean isToggled;

    // ==================== Constructor ====================

    public ToggleButton(int x, int y, int width, int height, String label, boolean initialState,
                        ToggleType type, Consumer<Boolean> onToggle) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.isToggled = initialState;
        this.type = type;
        this.onToggle = onToggle;
    }

    // ==================== Rendering ====================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int iconX = getX() + ICON_PADDING_LEFT;
        int iconY = getY() + (height - ICON_SIZE) / 2;

        renderIcon(graphics, iconX, iconY);
        renderLabel(graphics, iconX);
    }

    private void renderIcon(GuiGraphics graphics, int iconX, int iconY) {
        float brightness = isToggled ? FULL_BRIGHTNESS : DIM_BRIGHTNESS;
        graphics.setColor(brightness, brightness, brightness, FULL_ALPHA);

        ResourceLocation iconTex = getIconTexture();
        graphics.blit(iconTex, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        graphics.setColor(FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_ALPHA);
    }

    private void renderLabel(GuiGraphics graphics, int iconX) {
        int textX = iconX + ICON_SIZE + TEXT_PADDING;
        int textY = getY() + (height - FONT_HEIGHT) / 2;
        int textColor = isToggled ? TOGGLED_ON_TEXT_COLOR : TOGGLED_OFF_TEXT_COLOR;

        graphics.drawString(Minecraft.getInstance().font, label, textX, textY, textColor, false);
    }

    private ResourceLocation getIconTexture() {
        return switch (type) {
            case LOOP -> LOOP_TEXTURE;
            case LOCK -> LOCK_TEXTURE;
            case ON_OFF -> isToggled ? ON_TEXTURE : OFF_TEXTURE;
        };
    }

    // ==================== Interaction ====================

    @Override
    public void onClick(double mouseX, double mouseY) {
        isToggled = !isToggled;
        onToggle.accept(isToggled);
    }

    // ==================== Getters & Setters ====================

    public boolean isToggled() {
        return isToggled;
    }

    public void setToggled(boolean value) {
        this.isToggled = value;
    }

    // ==================== Narration ====================

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

