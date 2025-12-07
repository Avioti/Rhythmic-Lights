package com.rhythm.fabric;

import com.rhythm.util.RhythmConstants;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import com.rhythm.RhythmMod;

public final class RhythmModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register placeholder sound for URL discs
        Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            RhythmConstants.PLACEHOLDER_SOUND_ID,
            SoundEvent.createVariableRangeEvent(RhythmConstants.PLACEHOLDER_SOUND_ID)
        );

        // Register network packets (Fabric 1.21.1)
        RhythmModNetworking.registerPackets();

        // Run our common setup.
        RhythmMod.init();
    }
}
