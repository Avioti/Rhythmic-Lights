package com.rhythm.registry;

import com.rhythm.RhythmMod;
import dev.architectury.registry.CreativeTabRegistry;

/**
 * Populates creative tabs with RhythmMod items
 */
public class RhythmModCreativeTabPopulator {

    public static void init() {
        // Add items to custom creative tab
        CreativeTabRegistry.append(RhythmModTabs.RHYTHM_TAB,
            RhythmMod.RHYTHM_CONTROLLER_ITEM,
            RhythmMod.RHYTHM_BULB_ITEM,
            RhythmMod.TUNING_WAND_ITEM,
            RhythmMod.RHYTHM_URL_DISC_ITEM,
            // Pre-tuned Rhythm Lamps (one per frequency channel)
            RhythmModItems.SUB_BASS_LAMP,
            RhythmModItems.DEEP_BASS_LAMP,
            RhythmModItems.BASS_LAMP,
            RhythmModItems.LOW_MIDS_LAMP,
            RhythmModItems.MID_LOWS_LAMP,
            RhythmModItems.MIDS_LAMP,
            RhythmModItems.MID_HIGHS_LAMP,
            RhythmModItems.HIGH_MIDS_LAMP,
            RhythmModItems.HIGHS_LAMP,
            RhythmModItems.VERY_HIGHS_LAMP,
            RhythmModItems.ULTRA_LAMP,
            RhythmModItems.TOP_LAMP
        );
    }
}

