package com.rhythm.registry;

import com.rhythm.RhythmMod;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Creative Mode Tab registration for RhythmMod
 */
public class RhythmModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> RHYTHM_TAB = TABS.register("rhythmmod_tab",
        () -> CreativeTabRegistry.create(
            Component.translatable("itemGroup.rhythmmod.main"),
            () -> new ItemStack(RhythmMod.RHYTHM_CONTROLLER_ITEM.get())
        )
    );

    public static void init() {
        TABS.register();
    }
}

