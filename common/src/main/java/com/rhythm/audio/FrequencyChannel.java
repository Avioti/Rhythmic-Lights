package com.rhythm.audio;

import net.minecraft.util.StringRepresentable;

/**
 * Enum representing different frequency channels for bulb control
 * ULTRA-EXPANDED to 12 channels for MAXIMUM beat accuracy and guitar/instrument detail
 * Optimized frequency ranges for drums, bass, guitars, and all instruments
 */
public enum FrequencyChannel implements StringRepresentable {
    SUB_BASS(0, "sub_bass", "Sub Bass", 0xFF0000),           // Red - 20-40Hz (sub kick, floor toms)
    DEEP_BASS(1, "deep_bass", "Deep Bass", 0x441707),        // Dark Orange - 40-80Hz (kick drums, bass fundamentals)
    BASS(2, "bass", "Bass", 0x00FF00),                       // Green - 80-150Hz (bass guitar, low toms)
    LOW_MIDS(3, "low_mids", "Low Mids", 0x0000FF),           // Blue - 150-300Hz (guitar low strings E/A/D, snare body)
    MID_LOWS(4, "mid_lows", "Mid Lows", 0xFFFFFF),           // White - 300-500Hz (rhythm guitar fundamentals, toms)
    MIDS(5, "mids", "Mids", 0x8B00FF),                       // Purple - 500-800Hz (lead guitar, vocals, snare snap)
    MID_HIGHS(6, "mid_highs", "Mid Highs", 0xFF69B4),        // Hot Pink - 800-1200Hz (guitar harmonics, hi-hats)
    HIGH_MIDS(7, "high_mids", "High Mids", 0xFFFF00),        // Yellow - 1200-2000Hz (cymbals, guitar brightness)
    HIGHS(8, "highs", "Highs", 0x00FFFF),                    // Cyan - 2000-4000Hz (hi-hats, cymbals, guitar attack)
    VERY_HIGHS(9, "very_highs", "Very Highs", 0xFF00FF),     // Magenta - 4000-8000Hz (cymbal shimmer, presence)
    ULTRA(10, "ultra", "Ultra", 0xFFD700),                   // Gold - 8000-12000Hz (air, brilliance, pick attack)
    TOP(11, "top", "Top", 0xC0C0C0),                         // Silver - 12000-20000Hz (ultra-high harmonics, sparkle)
    ALL(12, "all", "All", 0xFFFFFF);                         // White - All frequencies combined

    // Legacy mapping for backward compatibility
    @Deprecated
    public static final FrequencyChannel LOW_BASS = LOW_MIDS; // Maps to new LOW_MIDS (150-300Hz)

    private final int id;
    private final String serializedName;
    private final String displayName;
    private final int defaultColor;

    FrequencyChannel(int id, String serializedName, String displayName, int defaultColor) {
        this.id = id;
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.defaultColor = defaultColor;
    }

    public int getId() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public static FrequencyChannel fromId(int id) {
        for (FrequencyChannel channel : values()) {
            if (channel.id == id) {
                return channel;
            }
        }
        return ALL;
    }

    public FrequencyChannel cycle() {
        return values()[(ordinal() + 1) % values().length];
    }
}

