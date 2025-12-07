package com.rhythm.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Utility class for creating animated RGB text effects in tooltips.
 * Provides rainbow, gradient, and pulse text coloring with smooth animation.
 */
public final class RGBText {

    private static final float HUE_INCREMENT = 0.002f;
    private static final float DEFAULT_HUE_STEP = 0.05f;
    private static final int FULL_ALPHA = 0xFF000000;
    private static final int MAX_COLOR_VALUE = 255;
    private static final float HUE_SEGMENTS = 6.0f;

    // Preset hue ranges for themed gradients
    private static final float AQUA_MIN_HUE = 0.45f;
    private static final float AQUA_MAX_HUE = 0.55f;
    private static final float PURPLE_MIN_HUE = 0.75f;
    private static final float PURPLE_MAX_HUE = 0.85f;
    private static final float GOLD_MIN_HUE = 0.08f;
    private static final float GOLD_MAX_HUE = 0.15f;

    private static float globalHue = 0f;

    private RGBText() {
        // Utility class - no instantiation
    }

    /**
     * Updates the global hue based on delta time.
     * Should be called each client tick.
     *
     * @param deltaTime time since last update
     */
    public static void tick(float deltaTime) {
        globalHue += HUE_INCREMENT * deltaTime;
        if (globalHue > 1.0f) {
            globalHue -= 1.0f;
        }
    }

    /**
     * Gets the current global hue for external use.
     *
     * @return current hue value (0.0 - 1.0)
     */
    public static float getGlobalHue() {
        return globalHue;
    }

    /**
     * Creates rainbow text where each letter has a different color.
     *
     * @param text the text to colorize
     * @return component with rainbow-colored text
     */
    public static Component rainbow(String text) {
        return rainbow(text, DEFAULT_HUE_STEP);
    }

    /**
     * Creates rainbow text with custom spacing between letter hues.
     *
     * @param text the text to colorize
     * @param hueStep how much hue changes between each letter (0.01 - 0.1 recommended)
     * @return component with rainbow-colored text
     */
    public static Component rainbow(String text, float hueStep) {
        MutableComponent component = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            String letter = text.substring(i, i + 1);
            float hue = (i * hueStep + globalHue) % 1.0f;
            component.append(Component.literal(letter).withColor(hueToRGB(hue)));
        }
        return component;
    }

    /**
     * Creates gradient text that transitions between two hue values.
     *
     * @param text the text to colorize
     * @param minHue starting hue (0.0 - 1.0)
     * @param maxHue ending hue (0.0 - 1.0)
     * @return component with gradient-colored text
     */
    public static Component gradient(String text, float minHue, float maxHue) {
        MutableComponent component = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            String letter = text.substring(i, i + 1);
            float t = text.length() > 1 ? (float) i / (text.length() - 1) : 0;
            float hue = mapHuePingPong(t + globalHue, minHue, maxHue);
            component.append(Component.literal(letter).withColor(hueToRGB(hue)));
        }
        return component;
    }

    /**
     * Creates text that pulses through colors uniformly.
     *
     * @param text the text to colorize
     * @return component with pulsing color
     */
    public static Component pulse(String text) {
        return Component.literal(text).withColor(hueToRGB(globalHue));
    }

    /**
     * Creates text with a specific static hue.
     *
     * @param text the text to colorize
     * @param hue the hue value (0.0 - 1.0)
     * @return component with specified color
     */
    public static Component withHue(String text, float hue) {
        return Component.literal(text).withColor(hueToRGB(hue));
    }

    /**
     * Creates cyan/aqua themed gradient text.
     */
    public static Component aqua(String text) {
        return gradient(text, AQUA_MIN_HUE, AQUA_MAX_HUE);
    }

    /**
     * Creates purple themed gradient text.
     */
    public static Component purple(String text) {
        return gradient(text, PURPLE_MIN_HUE, PURPLE_MAX_HUE);
    }

    /**
     * Creates gold/orange themed gradient text.
     */
    public static Component gold(String text) {
        return gradient(text, GOLD_MIN_HUE, GOLD_MAX_HUE);
    }

    /**
     * Maps hue using ping-pong for smooth back-and-forth transitions.
     */
    private static float mapHuePingPong(float h, float min, float max) {
        float t = Math.abs(2f * (h % 1f) - 1f);
        return min + t * (max - min);
    }

    /**
     * Converts hue (0.0 - 1.0) to RGB int color with full saturation and brightness.
     *
     * @param hue the hue value (0.0 - 1.0)
     * @return ARGB color integer
     */
    public static int hueToRGB(float hue) {
        float normalizedHue = hue - (float) Math.floor(hue);
        float h = normalizedHue * HUE_SEGMENTS;
        float fraction = h - (float) Math.floor(h);
        float inverseFraction = 1.0f - fraction;

        int r, g, b;
        int segment = (int) h;

        switch (segment) {
            case 0 -> { r = MAX_COLOR_VALUE; g = (int) (fraction * MAX_COLOR_VALUE); b = 0; }
            case 1 -> { r = (int) (inverseFraction * MAX_COLOR_VALUE); g = MAX_COLOR_VALUE; b = 0; }
            case 2 -> { r = 0; g = MAX_COLOR_VALUE; b = (int) (fraction * MAX_COLOR_VALUE); }
            case 3 -> { r = 0; g = (int) (inverseFraction * MAX_COLOR_VALUE); b = MAX_COLOR_VALUE; }
            case 4 -> { r = (int) (fraction * MAX_COLOR_VALUE); g = 0; b = MAX_COLOR_VALUE; }
            default -> { r = MAX_COLOR_VALUE; g = 0; b = (int) (inverseFraction * MAX_COLOR_VALUE); }
        }

        return FULL_ALPHA | (r << 16) | (g << 8) | b;
    }
}

