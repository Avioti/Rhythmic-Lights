package com.rhythm.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Utility class for creating smooth, seamless animated RGB color effects.
 * <p>
 * The colors rotate around borders creating a flowing rainbow effect.
 */
public final class RGBAnimator {

    // ==================== Constants ====================

    private static final int FULL_ALPHA = 0xFF000000;
    private static final int MAX_COLOR_VALUE = 255;
    private static final float HUE_SEGMENTS = 6.0f;
    private static final double MILLIS_TO_SECONDS = 1000.0;
    private static final int PIXEL_SIZE = 1;

    // ==================== Constructor ====================

    private RGBAnimator() {
        // Utility class - no instantiation
    }

    // ==================== Color Generation ====================

    /**
     * Converts HSV hue to RGB color with full saturation and value.
     *
     * @param hue hue value from 0.0 to 1.0 (wraps around)
     * @return ARGB color integer
     */
    public static int hsvToRgb(float hue) {
        float normalizedHue = hue - (float) Math.floor(hue);
        float h = normalizedHue * HUE_SEGMENTS;
        int sector = (int) h;
        float fraction = h - sector;

        float r, g, b;
        switch (sector % 6) {
            case 0 -> { r = 1.0f; g = fraction; b = 0.0f; }
            case 1 -> { r = 1.0f - fraction; g = 1.0f; b = 0.0f; }
            case 2 -> { r = 0.0f; g = 1.0f; b = fraction; }
            case 3 -> { r = 0.0f; g = 1.0f - fraction; b = 1.0f; }
            case 4 -> { r = fraction; g = 0.0f; b = 1.0f; }
            default -> { r = 1.0f; g = 0.0f; b = 1.0f - fraction; }
        }

        int ri = (int) (r * MAX_COLOR_VALUE);
        int gi = (int) (g * MAX_COLOR_VALUE);
        int bi = (int) (b * MAX_COLOR_VALUE);

        return FULL_ALPHA | (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Gets an animated RGB color based on current time.
     *
     * @param speed animation speed (cycles per second)
     * @return current RGB color
     */
    public static int getRGBColor(float speed) {
        return getRGBColor(speed, 0);
    }

    /**
     * Gets an animated RGB color with offset.
     *
     * @param speed animation speed (cycles per second)
     * @param offset phase offset (0-1)
     * @return current RGB color
     */
    public static int getRGBColor(float speed, float offset) {
        float hue = getAnimatedHue(speed, offset);
        return hsvToRgb(hue);
    }

    // ==================== Border Rendering ====================

    /**
     * Renders a seamless rotating RGB border around a rectangle.
     *
     * @param graphics the graphics context
     * @param x left position
     * @param y top position
     * @param width rectangle width
     * @param height rectangle height
     * @param thickness border thickness in pixels
     * @param speed animation speed (cycles per second)
     */
    public static void renderRotatingRGBBorder(GuiGraphics graphics,
                                                int x, int y, int width, int height,
                                                int thickness, float speed) {
        int perimeter = 2 * (width + height);
        float timeOffset = getAnimatedHue(speed, 0);

        renderTopEdge(graphics, x, y, width, thickness, perimeter, timeOffset);
        renderRightEdge(graphics, x, y, width, height, thickness, perimeter, timeOffset);
        renderBottomEdge(graphics, x, y, width, height, thickness, perimeter, timeOffset);
        renderLeftEdge(graphics, x, y, width, height, thickness, perimeter, timeOffset);
    }

    /**
     * Renders a simple RGB border (delegates to rotating version).
     */
    public static void renderSimpleRGBBorder(GuiGraphics graphics,
                                              int x, int y, int width, int height,
                                              int thickness, float speed) {
        renderRotatingRGBBorder(graphics, x, y, width, height, thickness, speed);
    }

    // ==================== Animation Helpers ====================

    private static float getAnimatedHue(float speed, float offset) {
        double seconds = System.currentTimeMillis() / MILLIS_TO_SECONDS;
        double hue = (seconds * speed) + offset;
        return (float) (hue - Math.floor(hue));
    }

    // ==================== Edge Rendering ====================

    private static void renderTopEdge(GuiGraphics graphics, int x, int y, int width,
                                       int thickness, int perimeter, float timeOffset) {
        for (int i = 0; i < width; i++) {
            float progress = (float) i / perimeter;
            int color = hsvToRgb(progress + timeOffset);
            graphics.fill(x + i, y, x + i + PIXEL_SIZE, y + thickness, color);
        }
    }

    private static void renderRightEdge(GuiGraphics graphics, int x, int y, int width, int height,
                                         int thickness, int perimeter, float timeOffset) {
        for (int i = 0; i < height; i++) {
            float progress = (float) (width + i) / perimeter;
            int color = hsvToRgb(progress + timeOffset);
            graphics.fill(x + width - thickness, y + i, x + width, y + i + PIXEL_SIZE, color);
        }
    }

    private static void renderBottomEdge(GuiGraphics graphics, int x, int y, int width, int height,
                                          int thickness, int perimeter, float timeOffset) {
        for (int i = 0; i < width; i++) {
            float progress = (float) (width + height + i) / perimeter;
            int color = hsvToRgb(progress + timeOffset);
            graphics.fill(x + width - i - PIXEL_SIZE, y + height - thickness, x + width - i, y + height, color);
        }
    }

    private static void renderLeftEdge(GuiGraphics graphics, int x, int y, int width, int height,
                                        int thickness, int perimeter, float timeOffset) {
        for (int i = 0; i < height; i++) {
            float progress = (float) (2 * width + height + i) / perimeter;
            int color = hsvToRgb(progress + timeOffset);
            graphics.fill(x, y + height - i - PIXEL_SIZE, x + thickness, y + height - i, color);
        }
    }
}

