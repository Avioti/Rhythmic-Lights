package com.rhythm.client.gui.widget;

import com.rhythm.RhythmMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom image-based button that renders only the PNG texture.
 * <p>
 * No background or border rendering - just the image to avoid blur effects
 * when used with custom GUI systems.
 */
public class ImageButton extends AbstractWidget {

    // ==================== Constants ====================

    private static final int DEFAULT_TEXTURE_SIZE = 16;
    private static final float HOVER_BRIGHTNESS = 1.2f;
    private static final float NORMAL_BRIGHTNESS = 1.0f;
    private static final float FULL_ALPHA = 1.0f;

    private static final String TEXTURE_PATH_PREFIX = "textures/gui/";
    private static final String TEXTURE_EXTENSION = ".png";

    // ==================== Fields ====================

    private final ResourceLocation texture;
    private final ResourceLocation hoverTexture;
    private final Runnable onClick;
    private final int textureSize;

    // ==================== Constructors ====================

    public ImageButton(int x, int y, int size, ResourceLocation texture, Runnable onClick) {
        this(x, y, size, size, texture, texture, onClick, DEFAULT_TEXTURE_SIZE);
    }

    public ImageButton(int x, int y, int width, int height, ResourceLocation texture, Runnable onClick) {
        this(x, y, width, height, texture, texture, onClick, DEFAULT_TEXTURE_SIZE);
    }

    public ImageButton(int x, int y, int width, int height,
                       ResourceLocation texture, ResourceLocation hoverTexture, Runnable onClick) {
        this(x, y, width, height, texture, hoverTexture, onClick, DEFAULT_TEXTURE_SIZE);
    }

    public ImageButton(int x, int y, int width, int height,
                       ResourceLocation texture, ResourceLocation hoverTexture,
                       Runnable onClick, int textureSize) {
        super(x, y, width, height, Component.empty());
        this.texture = texture;
        this.hoverTexture = hoverTexture;
        this.onClick = onClick;
        this.textureSize = textureSize;
    }

    // ==================== Rendering ====================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex = isHovered ? hoverTexture : texture;
        int iconX = getX() + (width - textureSize) / 2;
        int iconY = getY() + (height - textureSize) / 2;

        if (isHovered) {
            graphics.setColor(HOVER_BRIGHTNESS, HOVER_BRIGHTNESS, HOVER_BRIGHTNESS, FULL_ALPHA);
        }

        graphics.blit(tex, iconX, iconY, 0, 0, textureSize, textureSize, textureSize, textureSize);
        graphics.setColor(NORMAL_BRIGHTNESS, NORMAL_BRIGHTNESS, NORMAL_BRIGHTNESS, FULL_ALPHA);
    }

    // ==================== Interaction ====================

    @Override
    public void onClick(double mouseX, double mouseY) {
        onClick.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ==================== Factory Methods ====================

    public static ImageButton playButton(int x, int y, int size, Runnable onClick) {
        return new ImageButton(x, y, size, createGuiTexture("play"), onClick);
    }

    public static ImageButton pauseButton(int x, int y, int size, Runnable onClick) {
        return new ImageButton(x, y, size, createGuiTexture("pause"), onClick);
    }

    public static ImageButton stopButton(int x, int y, int size, Runnable onClick) {
        return new ImageButton(x, y, size, createGuiTexture("stop"), onClick);
    }

    public static ImageButton fetchButton(int x, int y, int size, Runnable onClick) {
        return new ImageButton(x, y, size, createGuiTexture("fetch"), onClick);
    }

    // ==================== Helpers ====================

    private static ResourceLocation createGuiTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, TEXTURE_PATH_PREFIX + name + TEXTURE_EXTENSION);
    }
}

