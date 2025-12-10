package com.rhythm.audio.playback;

import net.minecraft.core.BlockPos;

/**
 * Interface for calculating spatial audio volume.
 * Decouples playback sessions from the audio player's volume logic.
 */
public interface VolumeCalculator {

    /**
     * Calculates the effective volume for a jukebox considering distance and other active jukeboxes.
     *
     * @param jukeboxPos the jukebox position to calculate volume for
     * @param baseVolume the base volume (0.0 - 1.0)
     * @return effective volume, or -1 if player position is not available
     */
    float calculateMultiJukeboxVolume(BlockPos jukeboxPos, float baseVolume);

    /**
     * Gets Minecraft's master volume setting.
     *
     * @return master volume (0.0 - 1.0)
     */
    float getMinecraftMasterVolume();
}

