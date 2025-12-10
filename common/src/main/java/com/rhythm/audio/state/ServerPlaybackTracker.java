package com.rhythm.audio.state;

import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side tracker for jukebox playback states.
 * This singleton manages the state machine for all jukeboxes being controlled by RhythmMod.
 *
 * The server tracks states to:
 * 1. Handle controller clicks properly (know current state to toggle)
 * 2. Coordinate multi-player scenarios
 * 3. Persist state across chunk loads
 */
public class ServerPlaybackTracker {
    private static final ServerPlaybackTracker INSTANCE = new ServerPlaybackTracker();

    /** Consolidated playback state per jukebox position */
    private final Map<BlockPos, ServerJukeboxState> jukeboxStates = new ConcurrentHashMap<>();

    private ServerPlaybackTracker() {}

    public static ServerPlaybackTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Called when a disc is inserted into a jukebox.
     * Generates a unique song ID and sets state to LOADING.
     *
     * @param pos Jukebox position
     * @param gameTime Current game time (used to generate unique song ID)
     * @return The generated song ID
     */
    public long onDiscInserted(BlockPos pos, long gameTime) {
        long songId = RhythmConstants.generateSongId(gameTime, pos.hashCode());
        jukeboxStates.put(pos, ServerJukeboxState.EMPTY
            .withState(PlaybackState.LOADING)
            .withSongId(songId));

        RhythmConstants.debugAudio("Server: Disc inserted at {} | SongID: {} | State: LOADING", pos, songId);
        return songId;
    }

    /**
     * Called when FFT loading is complete on a client and reported to server.
     * Sets state to READY.
     */
    public void onLoadingComplete(BlockPos pos) {
        jukeboxStates.computeIfPresent(pos, (k, state) -> {
            if (state.state() == PlaybackState.LOADING) {
                RhythmConstants.debugAudio("Server: Loading complete at {} | State: READY", pos);
                return state.withState(PlaybackState.READY);
            }
            return state;
        });
    }

    /**
     * Called when the controller is clicked to start playback.
     *
     * @param pos Jukebox position
     * @param startTime Game time when playback started
     * @return true if state was changed to PLAYING
     */
    public boolean play(BlockPos pos, long startTime) {
        ServerJukeboxState current = jukeboxStates.get(pos);
        if (current != null && current.state().canPlay()) {
            jukeboxStates.put(pos, current.forPlayback(startTime));
            RhythmConstants.debugAudio("Server: Playing at {} | StartTime: {}", pos, startTime);
            return true;
        }
        return false;
    }

    /**
     * Called when the controller is clicked to stop/pause playback.
     *
     * @param pos Jukebox position
     * @param pausedAt Tick position where the song was paused
     * @return true if state was changed to STOPPED
     */
    public boolean stop(BlockPos pos, long pausedAt) {
        ServerJukeboxState current = jukeboxStates.get(pos);
        if (current != null && current.state() == PlaybackState.PLAYING) {
            jukeboxStates.put(pos, current.forStop(pausedAt));
            RhythmConstants.debugAudio("Server: Stopped at {} | PausedAt: {}", pos, pausedAt);
            return true;
        }
        return false;
    }

    /**
     * Called when a disc is removed from the jukebox.
     * Clears all state for this position.
     */
    public void onDiscRemoved(BlockPos pos) {
        jukeboxStates.remove(pos);
        RhythmConstants.debugAudio("Server: Disc removed at {} | State: EMPTY", pos);
    }

    /**
     * Get the current playback state for a jukebox.
     */
    public PlaybackState getState(BlockPos pos) {
        ServerJukeboxState state = jukeboxStates.get(pos);
        return state != null ? state.state() : PlaybackState.EMPTY;
    }

    /**
     * Get the full jukebox state record for a position.
     */
    public ServerJukeboxState getJukeboxState(BlockPos pos) {
        return jukeboxStates.getOrDefault(pos, ServerJukeboxState.EMPTY);
    }

    /**
     * Get the song ID for a jukebox (used for validation).
     */
    public Long getSongId(BlockPos pos) {
        return getJukeboxState(pos).songId();
    }

    /**
     * Get the start time for a playing song.
     */
    public Long getStartTime(BlockPos pos) {
        return getJukeboxState(pos).startTime();
    }

    /**
     * Get the paused tick position for a stopped song.
     */
    public Long getPausedAt(BlockPos pos) {
        return getJukeboxState(pos).pausedAtTick();
    }

    /**
     * Cache the song holder when pausing playback.
     * This allows us to restore the song when resuming.
     */
    public void cacheSongHolder(BlockPos pos, Holder<JukeboxSong> songHolder) {
        if (songHolder == null) {
            return;
        }
        jukeboxStates.computeIfPresent(pos, (k, state) -> state.withCachedSongHolder(songHolder));
    }

    /**
     * Get the cached song holder for a paused jukebox.
     */
    public Holder<JukeboxSong> getCachedSongHolder(BlockPos pos) {
        return getJukeboxState(pos).cachedSongHolder();
    }

    /**
     * Validate that a song ID matches the current song.
     * Used to prevent stale operations after rapid disc changes.
     */
    public boolean validateSongId(BlockPos pos, long songId) {
        Long current = getSongId(pos);
        return current != null && current == songId;
    }

    /**
     * Clear all tracking data (e.g., on server shutdown).
     */
    public void clearAll() {
        jukeboxStates.clear();
    }

    // ==================== URL DISC SUPPORT ====================

    /**
     * Check if a position is being tracked (has state).
     */
    public boolean isTracking(BlockPos pos) {
        return jukeboxStates.containsKey(pos);
    }

    /**
     * Mark a position as having a URL disc (for unlinked jukebox handling).
     */
    public void markAsUrlDisc(BlockPos pos, boolean isUrlDisc) {
        jukeboxStates.computeIfPresent(pos, (k, state) -> state.withUrlDisc(isUrlDisc));
    }

    /**
     * Check if a position has a URL disc.
     */
    public boolean isUrlDisc(BlockPos pos) {
        return getJukeboxState(pos).isUrlDisc();
    }

    /**
     * Mark a position to auto-play when loading is complete.
     * Used for URL discs in unlinked jukeboxes.
     */
    public void markAsAutoplayOnReady(BlockPos pos, boolean autoplay) {
        jukeboxStates.computeIfPresent(pos, (k, state) -> state.withAutoplayOnReady(autoplay));
    }

    /**
     * Check if a position should auto-play when ready.
     */
    public boolean shouldAutoplayOnReady(BlockPos pos) {
        return getJukeboxState(pos).autoplayOnReady();
    }

    /**
     * Clear the autoplay flag after triggering autoplay.
     */
    public void clearAutoplayFlag(BlockPos pos) {
        jukeboxStates.computeIfPresent(pos, (k, state) -> state.withAutoplayOnReady(false));
    }
}

