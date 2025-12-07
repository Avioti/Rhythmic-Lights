package com.rhythm.fabric.client;

import com.rhythm.client.gui.RGBText;

/**
 * Utility class to track tooltip state for RhythmMod items.
 * Separated from mixin to avoid issues with non-private static methods.
 */
public final class TooltipState {

    private static final int BORDER_ALPHA = 0xCC000000;
    private static final int RGB_MASK = 0x00FFFFFF;

    private static boolean isRenderingRhythmTooltip = false;

    private TooltipState() {
        // Utility class - no instantiation
    }

    /**
     * Sets whether we're currently rendering a RhythmMod tooltip.
     *
     * @param isRendering true if rendering a RhythmMod item tooltip
     */
    public static void setRhythmModTooltip(boolean isRendering) {
        isRenderingRhythmTooltip = isRendering;
    }

    /**
     * Checks if we're currently rendering a RhythmMod tooltip.
     *
     * @return true if currently rendering a RhythmMod item tooltip
     */
    public static boolean isRhythmModTooltip() {
        return isRenderingRhythmTooltip;
    }

    /**
     * Gets animated RGB border color based on current hue.
     * All border parts use the same color for unified cycling effect.
     *
     * @param isTopOrLeft unused, kept for API compatibility
     * @return ARGB color with 80% alpha
     */
    public static int getAnimatedBorderColor(boolean isTopOrLeft) {
        return getUnifiedBorderColor();
    }

    /**
     * Gets a single unified border color.
     *
     * @return ARGB color with 80% alpha, cycling through RGB spectrum
     */
    public static int getUnifiedBorderColor() {
        float hue = RGBText.getGlobalHue();
        int rgb = RGBText.hueToRGB(hue);
        return (rgb & RGB_MASK) | BORDER_ALPHA;
    }
}

