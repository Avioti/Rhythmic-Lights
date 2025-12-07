package com.rhythm.mixin.accessor;

import com.google.common.collect.Multimap;
import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Accessor mixin for SoundEngine to expose internal state.
 * <p>
 * Used for:
 * <ul>
 *   <li>Fading background music when jukebox is active</li>
 *   <li>Pausing/resuming music tracks</li>
 *   <li>Adjusting volumes based on user settings</li>
 * </ul>
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {

    /**
     * Checks if the sound engine is loaded and ready.
     */
    @Accessor("loaded")
    boolean rhythmmod$isLoaded();

    /**
     * Gets the map of sound instances to their channel handles.
     */
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> rhythmmod$getInstanceToChannel();

    /**
     * Gets the multimap of sound sources to their instances.
     */
    @Accessor("instanceBySource")
    Multimap<SoundSource, SoundInstance> rhythmmod$getInstanceBySource();

    /**
     * Gets the audio listener (player's ears).
     */
    @Accessor("listener")
    Listener rhythmmod$getListener();

    /**
     * Calculates the adjusted volume based on user's category settings.
     *
     * @param volume   base volume (0.0 to 1.0)
     * @param category the sound category
     * @return adjusted volume
     */
    @Invoker("calculateVolume")
    float rhythmmod$calculateVolume(float volume, SoundSource category);
}

