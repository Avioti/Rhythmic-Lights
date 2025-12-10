package com.rhythm.audio.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Stores information about a jukebox with a disc inserted.
 * Used by {@link JukeboxStateManager} to track active jukeboxes on the server.
 *
 * @param pos          the jukebox block position
 * @param soundEventId the sound event ID for the disc
 * @param customUrl    custom URL for URL discs (null for vanilla discs)
 * @param songId       unique song ID for validation
 * @param loop         whether looping is enabled
 */
public record JukeboxDiscInfo(
    BlockPos pos,
    ResourceLocation soundEventId,
    String customUrl,
    long songId,
    boolean loop
) {}

