package com.rhythm.audio.state;

import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;

/**
 * Consolidated server-side playback state for a jukebox position.
 * Using a record ensures atomic updates and consistency across all state fields.
 *
 * @param state            current playback state
 * @param songId           unique ID to validate async operations
 * @param startTime        game time when playback started
 * @param pausedAtTick     tick position where song was paused
 * @param cachedSongHolder cached song holder for resume after pause
 * @param isUrlDisc        whether this is a URL disc
 * @param autoplayOnReady  whether to auto-play when loading completes
 */
public record ServerJukeboxState(
    PlaybackState state,
    Long songId,
    Long startTime,
    Long pausedAtTick,
    Holder<JukeboxSong> cachedSongHolder,
    boolean isUrlDisc,
    boolean autoplayOnReady
) {
    /** Default empty state */
    public static final ServerJukeboxState EMPTY = new ServerJukeboxState(
        PlaybackState.EMPTY, null, null, null, null, false, false
    );

    /** Creates a copy with updated playback state */
    public ServerJukeboxState withState(PlaybackState newState) {
        return new ServerJukeboxState(newState, songId, startTime, pausedAtTick,
            cachedSongHolder, isUrlDisc, autoplayOnReady);
    }

    /** Creates a copy with updated song ID */
    public ServerJukeboxState withSongId(Long newSongId) {
        return new ServerJukeboxState(state, newSongId, startTime, pausedAtTick,
            cachedSongHolder, isUrlDisc, autoplayOnReady);
    }

    /** Creates a copy with updated start time */
    public ServerJukeboxState withStartTime(Long newStartTime) {
        return new ServerJukeboxState(state, songId, newStartTime, pausedAtTick,
            cachedSongHolder, isUrlDisc, autoplayOnReady);
    }

    /** Creates a copy with updated paused tick */
    public ServerJukeboxState withPausedAtTick(Long tick) {
        return new ServerJukeboxState(state, songId, startTime, tick,
            cachedSongHolder, isUrlDisc, autoplayOnReady);
    }

    /** Creates a copy with updated cached song holder */
    public ServerJukeboxState withCachedSongHolder(Holder<JukeboxSong> holder) {
        return new ServerJukeboxState(state, songId, startTime, pausedAtTick,
            holder, isUrlDisc, autoplayOnReady);
    }

    /** Creates a copy with updated URL disc flag */
    public ServerJukeboxState withUrlDisc(boolean urlDisc) {
        return new ServerJukeboxState(state, songId, startTime, pausedAtTick,
            cachedSongHolder, urlDisc, autoplayOnReady);
    }

    /** Creates a copy with updated autoplay flag */
    public ServerJukeboxState withAutoplayOnReady(boolean autoplay) {
        return new ServerJukeboxState(state, songId, startTime, pausedAtTick,
            cachedSongHolder, isUrlDisc, autoplay);
    }

    /** Creates a copy for starting playback (sets state, start time, clears paused tick) */
    public ServerJukeboxState forPlayback(long newStartTime) {
        return new ServerJukeboxState(PlaybackState.PLAYING, songId, newStartTime, null,
            cachedSongHolder, isUrlDisc, autoplayOnReady);
    }

    /** Creates a copy for stopping playback (sets state, paused tick) */
    public ServerJukeboxState forStop(long pausedAt) {
        return new ServerJukeboxState(PlaybackState.STOPPED, songId, startTime, pausedAt,
            cachedSongHolder, isUrlDisc, autoplayOnReady);
    }
}

