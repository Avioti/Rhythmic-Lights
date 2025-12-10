package com.rhythm.audio.state;

import net.minecraft.resources.ResourceLocation;

/**
 * Consolidated playback state for a jukebox position.
 * Using a record ensures atomic updates and consistency.
 *
 * @param state             current playback state
 * @param songId            unique ID to validate async operations
 * @param loadingProgress   FFT loading progress (0.0 to 1.0)
 * @param pausedAtTick      tick position where song was paused
 * @param playbackStartTime game time when playback started
 * @param soundEventId      sound event ID for vanilla sounds
 * @param customUrl         custom URL for URL discs
 * @param songTitle         display title for the song
 * @param loopEnabled       whether looping is enabled
 */
public record JukeboxPlaybackState(
    PlaybackState state,
    Long songId,
    Float loadingProgress,
    Long pausedAtTick,
    Long playbackStartTime,
    ResourceLocation soundEventId,
    String customUrl,
    String songTitle,
    boolean loopEnabled
) {
    /** Default empty state */
    public static final JukeboxPlaybackState EMPTY = new JukeboxPlaybackState(
        PlaybackState.EMPTY, null, null, null, null, null, null, "", false
    );

    /** Creates a copy with updated playback state */
    public JukeboxPlaybackState withState(PlaybackState newState) {
        return new JukeboxPlaybackState(newState, songId, loadingProgress, pausedAtTick,
            playbackStartTime, soundEventId, customUrl, songTitle, loopEnabled);
    }

    /** Creates a copy with updated song ID */
    public JukeboxPlaybackState withSongId(Long newSongId) {
        return new JukeboxPlaybackState(state, newSongId, loadingProgress, pausedAtTick,
            playbackStartTime, soundEventId, customUrl, songTitle, loopEnabled);
    }

    /** Creates a copy with updated loading progress */
    public JukeboxPlaybackState withLoadingProgress(Float progress) {
        return new JukeboxPlaybackState(state, songId, progress, pausedAtTick,
            playbackStartTime, soundEventId, customUrl, songTitle, loopEnabled);
    }

    /** Creates a copy with updated paused tick */
    public JukeboxPlaybackState withPausedAtTick(Long tick) {
        return new JukeboxPlaybackState(state, songId, loadingProgress, tick,
            playbackStartTime, soundEventId, customUrl, songTitle, loopEnabled);
    }

    /** Creates a copy with updated playback start time */
    public JukeboxPlaybackState withPlaybackStartTime(Long startTime) {
        return new JukeboxPlaybackState(state, songId, loadingProgress, pausedAtTick,
            startTime, soundEventId, customUrl, songTitle, loopEnabled);
    }

    /** Creates a copy with updated sound event ID */
    public JukeboxPlaybackState withSoundEventId(ResourceLocation id) {
        return new JukeboxPlaybackState(state, songId, loadingProgress, pausedAtTick,
            playbackStartTime, id, customUrl, songTitle, loopEnabled);
    }

    /** Creates a copy with updated custom URL */
    public JukeboxPlaybackState withCustomUrl(String url) {
        return new JukeboxPlaybackState(state, songId, loadingProgress, pausedAtTick,
            playbackStartTime, soundEventId, url, songTitle, loopEnabled);
    }

    /** Creates a copy with updated song title */
    public JukeboxPlaybackState withSongTitle(String title) {
        return new JukeboxPlaybackState(state, songId, loadingProgress, pausedAtTick,
            playbackStartTime, soundEventId, customUrl, title, loopEnabled);
    }

    /** Creates a copy with updated loop setting */
    public JukeboxPlaybackState withLoopEnabled(boolean loop) {
        return new JukeboxPlaybackState(state, songId, loadingProgress, pausedAtTick,
            playbackStartTime, soundEventId, customUrl, songTitle, loop);
    }

    /** Creates a copy with cleared pause/timing data (for reset) */
    public JukeboxPlaybackState withClearedPlaybackPosition() {
        return new JukeboxPlaybackState(state, songId, loadingProgress, null,
            null, soundEventId, customUrl, songTitle, loopEnabled);
    }
}

