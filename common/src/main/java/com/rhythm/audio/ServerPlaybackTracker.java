package com.rhythm.audio;

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

    // Playback state per jukebox position (dimension-aware via compound key or separate maps)
    private final Map<BlockPos, PlaybackState> states = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> startTimes = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> pausedAtTicks = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> songIds = new ConcurrentHashMap<>();
    // Cache the song holder when pausing so we can restore it on resume
    private final Map<BlockPos, Holder<JukeboxSong>> cachedSongHolders = new ConcurrentHashMap<>();
    // Track URL discs in unlinked jukeboxes
    private final Map<BlockPos, Boolean> urlDiscPositions = new ConcurrentHashMap<>();
    // Track positions that should auto-play when ready (URL discs in unlinked jukeboxes)
    private final Map<BlockPos, Boolean> autoplayOnReady = new ConcurrentHashMap<>();

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
        long songId = gameTime * 1000 + Math.abs(pos.hashCode() % 1000);
        states.put(pos, PlaybackState.LOADING);
        songIds.put(pos, songId);
        startTimes.remove(pos);
        pausedAtTicks.remove(pos);

        RhythmConstants.debugAudio("Server: Disc inserted at {} | SongID: {} | State: LOADING", pos, songId);
        return songId;
    }

    /**
     * Called when FFT loading is complete on a client and reported to server.
     * Sets state to READY.
     */
    public void onLoadingComplete(BlockPos pos) {
        if (states.get(pos) == PlaybackState.LOADING) {
            states.put(pos, PlaybackState.READY);
            RhythmConstants.debugAudio("Server: Loading complete at {} | State: READY", pos);
        }
    }

    /**
     * Called when the controller is clicked to start playback.
     *
     * @param pos Jukebox position
     * @param startTime Game time when playback started
     * @return true if state was changed to PLAYING
     */
    public boolean play(BlockPos pos, long startTime) {
        PlaybackState current = states.get(pos);
        if (current != null && current.canPlay()) {
            states.put(pos, PlaybackState.PLAYING);
            startTimes.put(pos, startTime);
            pausedAtTicks.remove(pos);
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
        if (states.get(pos) == PlaybackState.PLAYING) {
            states.put(pos, PlaybackState.STOPPED);
            pausedAtTicks.put(pos, pausedAt);
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
        states.remove(pos);
        startTimes.remove(pos);
        pausedAtTicks.remove(pos);
        songIds.remove(pos);
        cachedSongHolders.remove(pos);
        urlDiscPositions.remove(pos);
        autoplayOnReady.remove(pos);
        RhythmConstants.debugAudio("Server: Disc removed at {} | State: EMPTY", pos);
    }

    /**
     * Get the current playback state for a jukebox.
     */
    public PlaybackState getState(BlockPos pos) {
        return states.getOrDefault(pos, PlaybackState.EMPTY);
    }

    /**
     * Get the song ID for a jukebox (used for validation).
     */
    public Long getSongId(BlockPos pos) {
        return songIds.get(pos);
    }

    /**
     * Get the start time for a playing song.
     */
    public Long getStartTime(BlockPos pos) {
        return startTimes.get(pos);
    }

    /**
     * Get the paused tick position for a stopped song.
     */
    public Long getPausedAt(BlockPos pos) {
        return pausedAtTicks.get(pos);
    }

    /**
     * Cache the song holder when pausing playback.
     * This allows us to restore the song when resuming.
     */
    public void cacheSongHolder(BlockPos pos, Holder<JukeboxSong> songHolder) {
        if (songHolder != null) {
            cachedSongHolders.put(pos, songHolder);
        }
    }

    /**
     * Get the cached song holder for a paused jukebox.
     */
    public Holder<JukeboxSong> getCachedSongHolder(BlockPos pos) {
        return cachedSongHolders.get(pos);
    }

    /**
     * Validate that a song ID matches the current song.
     * Used to prevent stale operations after rapid disc changes.
     */
    public boolean validateSongId(BlockPos pos, long songId) {
        Long current = songIds.get(pos);
        return current != null && current == songId;
    }

    /**
     * Clear all tracking data (e.g., on server shutdown).
     */
    public void clearAll() {
        states.clear();
        startTimes.clear();
        pausedAtTicks.clear();
        songIds.clear();
        cachedSongHolders.clear();
        urlDiscPositions.clear();
        autoplayOnReady.clear();
    }

    // ==================== URL DISC SUPPORT ====================

    /**
     * Check if a position is being tracked (has state).
     */
    public boolean isTracking(BlockPos pos) {
        return states.containsKey(pos);
    }

    /**
     * Mark a position as having a URL disc (for unlinked jukebox handling).
     */
    public void markAsUrlDisc(BlockPos pos, boolean isUrlDisc) {
        if (isUrlDisc) {
            urlDiscPositions.put(pos, true);
        } else {
            urlDiscPositions.remove(pos);
        }
    }

    /**
     * Check if a position has a URL disc.
     */
    public boolean isUrlDisc(BlockPos pos) {
        return urlDiscPositions.getOrDefault(pos, false);
    }

    /**
     * Mark a position to auto-play when loading is complete.
     * Used for URL discs in unlinked jukeboxes.
     */
    public void markAsAutoplayOnReady(BlockPos pos, boolean autoplay) {
        if (autoplay) {
            autoplayOnReady.put(pos, true);
        } else {
            autoplayOnReady.remove(pos);
        }
    }

    /**
     * Check if a position should auto-play when ready.
     */
    public boolean shouldAutoplayOnReady(BlockPos pos) {
        return autoplayOnReady.getOrDefault(pos, false);
    }

    /**
     * Clear the autoplay flag after triggering autoplay.
     */
    public void clearAutoplayFlag(BlockPos pos) {
        autoplayOnReady.remove(pos);
    }
}

