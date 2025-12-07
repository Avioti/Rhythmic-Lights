package com.rhythm.registry;

import com.rhythm.RhythmMod;
import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Recipe registry for RhythmMod.
 */
public final class RhythmModRecipes {

    private RhythmModRecipes() {}

    // ==================== Registries ====================

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.RECIPE_TYPE);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.RECIPE_SERIALIZER);

    // ==================== Registration ====================

    public static void register() {
        RECIPE_TYPES.register();
        RECIPE_SERIALIZERS.register();
    }
}

