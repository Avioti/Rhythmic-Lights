package com.rhythm.registry;

import com.rhythm.RhythmMod;
import com.rhythm.block.RhythmBulbBlockEntity;
import com.rhythm.block.RhythmControllerBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Block entity registry for RhythmMod.
 */
public final class RhythmModBlockEntities {

    private RhythmModBlockEntities() {}

    // ==================== Registry ====================

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    // ==================== Block Entities ====================

    public static final RegistrySupplier<BlockEntityType<RhythmControllerBlockEntity>> RHYTHM_CONTROLLER =
        BLOCK_ENTITIES.register("rhythm_controller", () ->
            BlockEntityType.Builder.of(
                RhythmControllerBlockEntity::new,
                RhythmModBlocks.RHYTHM_CONTROLLER.get()
            ).build(null));

    public static final RegistrySupplier<BlockEntityType<RhythmBulbBlockEntity>> RHYTHM_BULB =
        BLOCK_ENTITIES.register("rhythm_bulb", () ->
            BlockEntityType.Builder.of(
                RhythmBulbBlockEntity::new,
                RhythmModBlocks.RHYTHM_BULB.get(),
                RhythmModBlocks.SUB_BASS_LAMP.get(),
                RhythmModBlocks.DEEP_BASS_LAMP.get(),
                RhythmModBlocks.BASS_LAMP.get(),
                RhythmModBlocks.LOW_MIDS_LAMP.get(),
                RhythmModBlocks.MID_LOWS_LAMP.get(),
                RhythmModBlocks.MIDS_LAMP.get(),
                RhythmModBlocks.MID_HIGHS_LAMP.get(),
                RhythmModBlocks.HIGH_MIDS_LAMP.get(),
                RhythmModBlocks.HIGHS_LAMP.get(),
                RhythmModBlocks.VERY_HIGHS_LAMP.get(),
                RhythmModBlocks.ULTRA_LAMP.get(),
                RhythmModBlocks.TOP_LAMP.get()
            ).build(null));

    // ==================== Registration ====================

    public static void register() {
        BLOCK_ENTITIES.register();
    }
}

