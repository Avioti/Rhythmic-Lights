package com.rhythm.audio;

import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side singleton manager for active song frequency data.
 * Maps jukebox positions to their analyzed audio data and playback state.
 *
 * Enhanced with:
 * - PlaybackState tracking for the staged playback system
 * - SongId validation to prevent stale async FFT data
 * - Loading progress tracking for HUD display
 * - Paused position tracking for resume functionality
 * - Audio source info (soundEventId, customUrl) for seekable resume
 */
public class ClientSongManager {
    private static final ClientSongManager INSTANCE = new ClientSongManager();

    // Core frequency data storage
    private final Map<BlockPos, FrequencyData> activeSongs = new ConcurrentHashMap<>();

    // Playback state management
    private final Map<BlockPos, PlaybackState> playbackStates = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> currentSongIds = new ConcurrentHashMap<>();
    private final Map<BlockPos, Float> loadingProgress = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> pausedAtTicks = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> playbackStartTimes = new ConcurrentHashMap<>();

    // Audio source info for seekable resume
    private final Map<BlockPos, ResourceLocation> soundEventIds = new ConcurrentHashMap<>();
    private final Map<BlockPos, String> customUrls = new ConcurrentHashMap<>();
    private final Map<BlockPos, String> songTitles = new ConcurrentHashMap<>();

    // Loop setting for URL discs
    private final Map<BlockPos, Boolean> loopEnabled = new ConcurrentHashMap<>();

    private ClientSongManager() {}

    public static ClientSongManager getInstance() {
        return INSTANCE;
    }

    // ==================== FREQUENCY DATA ====================

    public void registerSong(BlockPos pos, FrequencyData data) {
        activeSongs.put(pos, data);
        RhythmConstants.debugAudio("Registered song at {} (Loading: {})", pos, data.isLoading());
    }

    /**
     * Register a song only if the songId matches the current expected songId.
     * This prevents stale async FFT results from being registered after a disc change.
     *
     * @return true if the song was registered, false if songId validation failed
     */
    public boolean registerSongIfValid(BlockPos pos, FrequencyData data, long songId) {
        if (!validateSongId(pos, songId)) {
            RhythmConstants.debugAudio("Discarding stale FFT data at {} - songId mismatch (expected: {}, got: {})",
                pos, currentSongIds.get(pos), songId);
            return false;
        }
        activeSongs.put(pos, data);
        RhythmConstants.debugAudio("Registered validated song at {}", pos);
        return true;
    }

    public FrequencyData getFrequencyData(BlockPos pos) {
        return activeSongs.get(pos);
    }

    public void removeSong(BlockPos pos) {
        activeSongs.remove(pos);
        RhythmConstants.debugAudio("Removed song at {}", pos);
    }

    public boolean hasSong(BlockPos pos) {
        return activeSongs.containsKey(pos);
    }

    // Legacy compatibility
    @Deprecated
    public SongData getSongData(BlockPos pos) {
        FrequencyData freqData = activeSongs.get(pos);
        if (freqData == null) return null;
        return new SongData(freqData.getBassMap(), freqData.getStartTime());
    }

    // ==================== PLAYBACK STATE ====================

    /**
     * Set the playback state for a jukebox position.
     */
    public void setState(BlockPos pos, PlaybackState state) {
        playbackStates.put(pos, state);
        RhythmConstants.debugAudio("State changed at {} -> {}", pos, state);
    }

    /**
     * Get the playback state for a jukebox position.
     * Returns EMPTY if no state is tracked.
     */
    public PlaybackState getState(BlockPos pos) {
        return playbackStates.getOrDefault(pos, PlaybackState.EMPTY);
    }

    /**
     * Check if a jukebox is in the PLAYING state.
     * This is the primary check for light sync systems.
     */
    public boolean isPlaying(BlockPos pos) {
        return getState(pos) == PlaybackState.PLAYING;
    }

    // ==================== SONG ID VALIDATION ====================

    /**
     * Set the current song ID for a jukebox position.
     * Called when a new disc is inserted.
     */
    public void setSongId(BlockPos pos, long songId) {
        currentSongIds.put(pos, songId);
        RhythmConstants.debugAudio("SongId set at {} -> {}", pos, songId);
    }

    /**
     * Get the current song ID for a jukebox position.
     */
    public Long getSongId(BlockPos pos) {
        return currentSongIds.get(pos);
    }

    /**
     * Clear the song ID for a jukebox position.
     * Called when a disc is removed.
     */
    public void clearSongId(BlockPos pos) {
        currentSongIds.remove(pos);
    }

    /**
     * Validate that a song ID matches the current expected song ID.
     * Returns false if no song ID is set or if they don't match.
     */
    public boolean validateSongId(BlockPos pos, long songId) {
        Long current = currentSongIds.get(pos);
        return current != null && current == songId;
    }

    // ==================== LOADING PROGRESS ====================

    /**
     * Set the loading progress for a jukebox (0.0 to 1.0).
     */
    public void setLoadingProgress(BlockPos pos, float progress) {
        loadingProgress.put(pos, Math.max(0f, Math.min(1f, progress)));
    }

    /**
     * Get the loading progress for a jukebox (0.0 to 1.0).
     */
    public float getLoadingProgress(BlockPos pos) {
        return loadingProgress.getOrDefault(pos, 0f);
    }

    /**
     * Clear loading progress when done.
     */
    public void clearLoadingProgress(BlockPos pos) {
        loadingProgress.remove(pos);
    }

    // ==================== PAUSE/RESUME ====================

    /**
     * Set the tick position where the song was paused.
     * Used for resume functionality.
     */
    public void setPausedAt(BlockPos pos, long tick) {
        pausedAtTicks.put(pos, tick);
    }

    /**
     * Get the tick position where the song was paused.
     */
    public Long getPausedAt(BlockPos pos) {
        return pausedAtTicks.get(pos);
    }

    /**
     * Clear the paused position.
     */
    public void clearPausedAt(BlockPos pos) {
        pausedAtTicks.remove(pos);
    }

    /**
     * Reset the playback position to the beginning.
     * Called when STOP button is pressed (not pause).
     * Next play will start from the beginning of the song.
     */
    public void resetPlaybackPosition(BlockPos pos) {
        pausedAtTicks.remove(pos);
        playbackStartTimes.remove(pos);
        RhythmConstants.debugAudio("Reset playback position for {} - next play will start from beginning", pos);
    }

    // ==================== PLAYBACK TIMING ====================

    /**
     * Set the game time when playback started.
     * Used by renderers to calculate tick offset into song.
     */
    public void setPlaybackStartTime(BlockPos pos, long startTime) {
        playbackStartTimes.put(pos, startTime);
        RhythmConstants.debugAudio("Playback start time set at {} -> {}", pos, startTime);
    }

    /**
     * Get the game time when playback started.
     * Returns null if not currently playing.
     */
    public Long getPlaybackStartTime(BlockPos pos) {
        return playbackStartTimes.get(pos);
    }

    /**
     * Clear the playback start time.
     */
    public void clearPlaybackStartTime(BlockPos pos) {
        playbackStartTimes.remove(pos);
    }

    // ==================== AUDIO SOURCE INFO (for seekable resume) ====================

    /**
     * Set the sound event ID for a jukebox position.
     * Used when resuming from pause with seekable audio.
     */
    public void setSoundEventId(BlockPos pos, ResourceLocation soundEventId) {
        soundEventIds.put(pos, soundEventId);
    }

    /**
     * Get the sound event ID for a jukebox position.
     */
    public ResourceLocation getSoundEventId(BlockPos pos) {
        return soundEventIds.get(pos);
    }

    /**
     * Set the custom URL for a jukebox position (custom URL disc support).
     */
    public void setCustomUrl(BlockPos pos, String customUrl) {
        if (customUrl != null && !customUrl.isEmpty()) {
            customUrls.put(pos, customUrl);
        }
    }

    /**
     * Get the custom URL for a jukebox position.
     */
    public String getCustomUrl(BlockPos pos) {
        return customUrls.get(pos);
    }

    /**
     * Set the song title for a jukebox position.
     */
    public void setSongTitle(BlockPos pos, String title) {
        if (title != null && !title.isEmpty()) {
            songTitles.put(pos, title);
        }
    }

    /**
     * Get the song title for a jukebox position.
     *
     * @return the song title, or empty string if not set
     */
    public String getSongTitle(BlockPos pos) {
        return songTitles.getOrDefault(pos, "");
    }

    /**
     * Check if a song title is available for a jukebox position.
     */
    public boolean hasSongTitle(BlockPos pos) {
        String title = songTitles.get(pos);
        return title != null && !title.isEmpty();
    }

    // ==================== LOOP SETTING ====================

    /**
     * Set whether loop is enabled for a jukebox position.
     */
    public void setLoopEnabled(BlockPos pos, boolean loop) {
        loopEnabled.put(pos, loop);
        RhythmConstants.debugAudio("Loop set at {} -> {}", pos, loop);
    }

    /**
     * Check if loop is enabled for a jukebox position.
     */
    public boolean isLoopEnabled(BlockPos pos) {
        return loopEnabled.getOrDefault(pos, false);
    }

    // ==================== CLEANUP ====================

    /**
     * Clear all data for a specific jukebox position.
     * Called when a disc is removed.
     */
    public void clearPosition(BlockPos pos) {
        activeSongs.remove(pos);
        playbackStates.remove(pos);
        currentSongIds.remove(pos);
        loadingProgress.remove(pos);
        pausedAtTicks.remove(pos);
        playbackStartTimes.remove(pos);
        soundEventIds.remove(pos);
        customUrls.remove(pos);
        songTitles.remove(pos);
        loopEnabled.remove(pos);
        // Also stop any custom audio playback
        SeekableAudioPlayer.getInstance().stop(pos);
        RhythmConstants.debugAudio("Cleared all data for {}", pos);
    }

    /**
     * Clear all tracked data (e.g., on world exit).
     */
    public void clearAll() {
        activeSongs.clear();
        playbackStates.clear();
        currentSongIds.clear();
        loadingProgress.clear();
        pausedAtTicks.clear();
        playbackStartTimes.clear();
        soundEventIds.clear();
        customUrls.clear();
        loopEnabled.clear();
        // Stop all custom audio playback
        SeekableAudioPlayer.getInstance().stopAll();
        RhythmConstants.debugAudio("Cleared all data");
    }
}