package com.rhythm.registry;

import com.rhythm.RhythmMod;
import com.rhythm.item.ComposerStationBlockItem;
import com.rhythm.item.RhythmBulbBlockItem;
import com.rhythm.item.RhythmLampBlockItem;
import com.rhythm.item.RhythmURLDisc;
import com.rhythm.item.TuningWandItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Item registry for RhythmMod.
 */
public final class RhythmModItems {

    private RhythmModItems() {}

    // ==================== Registry ====================

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.ITEM);

    // ==================== Translation Key Prefix ====================

    private static final String BLOCK_TRANSLATION_PREFIX = "block.rhythmmod.";

    // ==================== Core Items ====================

    public static final RegistrySupplier<Item> RHYTHM_CONTROLLER =
        ITEMS.register("rhythm_controller", () ->
            new ComposerStationBlockItem(RhythmModBlocks.RHYTHM_CONTROLLER.get(), defaultProperties()));

    public static final RegistrySupplier<Item> RHYTHM_BULB =
        ITEMS.register("rhythm_bulb", () ->
            new RhythmBulbBlockItem(RhythmModBlocks.RHYTHM_BULB.get(), defaultProperties()));

    public static final RegistrySupplier<Item> TUNING_WAND =
        ITEMS.register("tuning_wand", () ->
            new TuningWandItem(defaultProperties().stacksTo(1)));

    public static final RegistrySupplier<Item> RHYTHM_URL_DISC =
        ITEMS.register("rhythm_url_disc", RhythmURLDisc::new);

    // ==================== Pre-Tuned Lamp Items ====================

    public static final RegistrySupplier<Item> SUB_BASS_LAMP =
        registerLampItem("sub_bass_lamp", RhythmModBlocks.SUB_BASS_LAMP);

    public static final RegistrySupplier<Item> DEEP_BASS_LAMP =
        registerLampItem("deep_bass_lamp", RhythmModBlocks.DEEP_BASS_LAMP);

    public static final RegistrySupplier<Item> BASS_LAMP =
        registerLampItem("bass_lamp", RhythmModBlocks.BASS_LAMP);

    public static final RegistrySupplier<Item> LOW_MIDS_LAMP =
        registerLampItem("low_mids_lamp", RhythmModBlocks.LOW_MIDS_LAMP);

    public static final RegistrySupplier<Item> MID_LOWS_LAMP =
        registerLampItem("mid_lows_lamp", RhythmModBlocks.MID_LOWS_LAMP);

    public static final RegistrySupplier<Item> MIDS_LAMP =
        registerLampItem("mids_lamp", RhythmModBlocks.MIDS_LAMP);

    public static final RegistrySupplier<Item> MID_HIGHS_LAMP =
        registerLampItem("mid_highs_lamp", RhythmModBlocks.MID_HIGHS_LAMP);

    public static final RegistrySupplier<Item> HIGH_MIDS_LAMP =
        registerLampItem("high_mids_lamp", RhythmModBlocks.HIGH_MIDS_LAMP);

    public static final RegistrySupplier<Item> HIGHS_LAMP =
        registerLampItem("highs_lamp", RhythmModBlocks.HIGHS_LAMP);

    public static final RegistrySupplier<Item> VERY_HIGHS_LAMP =
        registerLampItem("very_highs_lamp", RhythmModBlocks.VERY_HIGHS_LAMP);

    public static final RegistrySupplier<Item> ULTRA_LAMP =
        registerLampItem("ultra_lamp", RhythmModBlocks.ULTRA_LAMP);

    public static final RegistrySupplier<Item> TOP_LAMP =
        registerLampItem("top_lamp", RhythmModBlocks.TOP_LAMP);

    // ==================== Helper Methods ====================

    private static Item.Properties defaultProperties() {
        return new Item.Properties();
    }

    private static RegistrySupplier<Item> registerLampItem(String name, RegistrySupplier<Block> block) {
        String translationKey = BLOCK_TRANSLATION_PREFIX + name;
        return ITEMS.register(name, () ->
            new RhythmLampBlockItem(block.get(), defaultProperties(), translationKey));
    }

    // ==================== Registration ====================

    public static void register() {
        ITEMS.register();
    }
}

