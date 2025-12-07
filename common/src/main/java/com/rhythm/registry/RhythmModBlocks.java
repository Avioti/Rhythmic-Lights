package com.rhythm.registry;

import com.rhythm.RhythmMod;
import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.RhythmBulbBlock;
import com.rhythm.block.RhythmControllerBlock;
import com.rhythm.block.RhythmLampBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Block registry for RhythmMod.
 */
public final class RhythmModBlocks {

    private RhythmModBlocks() {}

    // ==================== Registry ====================

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.BLOCK);

    // ==================== Block Properties ====================

    private static final float CONTROLLER_STRENGTH = 3.0f;
    private static final float LAMP_STRENGTH = 0.3f;
    private static final int MAX_LIGHT_LEVEL = 15;

    private static BlockBehaviour.Properties metalBlockProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(CONTROLLER_STRENGTH)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties lampProperties(MapColor color) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .strength(LAMP_STRENGTH)
            .sound(SoundType.GLASS)
            .noOcclusion()
            .lightLevel(state -> state.getValue(RhythmBulbBlock.LIT) ? MAX_LIGHT_LEVEL : 0);
    }

    // ==================== Core Blocks ====================

    public static final RegistrySupplier<Block> RHYTHM_CONTROLLER =
        BLOCKS.register("rhythm_controller", () -> new RhythmControllerBlock(metalBlockProperties()));

    public static final RegistrySupplier<Block> RHYTHM_BULB =
        BLOCKS.register("rhythm_bulb", () -> new RhythmBulbBlock(lampProperties(MapColor.GOLD)));

    // ==================== Pre-Tuned Frequency Lamps ====================

    public static final RegistrySupplier<Block> SUB_BASS_LAMP =
        registerLamp("sub_bass_lamp", MapColor.COLOR_RED, FrequencyChannel.SUB_BASS);

    public static final RegistrySupplier<Block> DEEP_BASS_LAMP =
        registerLamp("deep_bass_lamp", MapColor.COLOR_ORANGE, FrequencyChannel.DEEP_BASS);

    public static final RegistrySupplier<Block> BASS_LAMP =
        registerLamp("bass_lamp", MapColor.COLOR_GREEN, FrequencyChannel.BASS);

    public static final RegistrySupplier<Block> LOW_MIDS_LAMP =
        registerLamp("low_mids_lamp", MapColor.COLOR_BLUE, FrequencyChannel.LOW_MIDS);

    public static final RegistrySupplier<Block> MID_LOWS_LAMP =
        registerLamp("mid_lows_lamp", MapColor.SNOW, FrequencyChannel.MID_LOWS);

    public static final RegistrySupplier<Block> MIDS_LAMP =
        registerLamp("mids_lamp", MapColor.COLOR_PURPLE, FrequencyChannel.MIDS);

    public static final RegistrySupplier<Block> MID_HIGHS_LAMP =
        registerLamp("mid_highs_lamp", MapColor.COLOR_PINK, FrequencyChannel.MID_HIGHS);

    public static final RegistrySupplier<Block> HIGH_MIDS_LAMP =
        registerLamp("high_mids_lamp", MapColor.COLOR_CYAN, FrequencyChannel.HIGH_MIDS);

    public static final RegistrySupplier<Block> HIGHS_LAMP =
        registerLamp("highs_lamp", MapColor.COLOR_YELLOW, FrequencyChannel.HIGHS);

    public static final RegistrySupplier<Block> VERY_HIGHS_LAMP =
        registerLamp("very_highs_lamp", MapColor.COLOR_LIGHT_BLUE, FrequencyChannel.VERY_HIGHS);

    public static final RegistrySupplier<Block> ULTRA_LAMP =
        registerLamp("ultra_lamp", MapColor.COLOR_MAGENTA, FrequencyChannel.ULTRA);

    public static final RegistrySupplier<Block> TOP_LAMP =
        registerLamp("top_lamp", MapColor.COLOR_LIGHT_GREEN, FrequencyChannel.TOP);

    // ==================== Helper Methods ====================

    private static RegistrySupplier<Block> registerLamp(String name, MapColor color, FrequencyChannel channel) {
        return BLOCKS.register(name, () -> new RhythmLampBlock(lampProperties(color), channel));
    }

    // ==================== Registration ====================

    public static void register() {
        BLOCKS.register();
    }
}

