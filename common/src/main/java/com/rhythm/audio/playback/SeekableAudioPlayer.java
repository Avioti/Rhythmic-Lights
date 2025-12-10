package com.rhythm.audio.playback;

import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side audio player with seek capability and spatial audio features.
 * <p>
 * Features:
 * <ul>
 *   <li>Seek to any position in the audio</li>
 *   <li>Distance-based volume attenuation</li>
 *   <li>Automatic background music fade-out</li>
 *   <li>Multi-jukebox support with proximity-based mixing</li>
 *   <li>Game pause detection</li>
 *   <li>Real-time audio effects (EQ, bass boost, surround)</li>
 * </ul>
 */
public class SeekableAudioPlayer implements VolumeCalculator {

    // ==================== Singleton ====================

    private static final SeekableAudioPlayer INSTANCE = new SeekableAudioPlayer();

    // ==================== Timing Configuration ====================

    private static final long TICKS_PER_SECOND = 20L;
    private static final long MS_PER_SECOND = 1000L;

    // ==================== Distance-Based Audio ====================

    private static final float DEFAULT_MAX_DISTANCE = 24.0f;
    private static final float DEFAULT_REFERENCE_DISTANCE = 4.0f;
    private static final float DEFAULT_ROLLOFF_FACTOR = 1.0f;
    private static final float MIN_DISTANCE_CLAMP = 1.0f;
    private static final float MIN_REFERENCE_DISTANCE = 0.5f;
    private static final float FADE_ZONE_PERCENTAGE = 0.2f;

    // ==================== Volume Configuration ====================

    private static final float BACKGROUND_FADE_TARGET = 0.15f;

    // ==================== Session Management ====================

    private static final long PLAYER_POSITION_CACHE_MS = 50L;
    private static final int STOP_WAIT_MAX_ITERATIONS = 50;
    private static final int STOP_WAIT_INTERVAL_MS = 10;

    // ==================== Spatial Audio State ====================

    private float maxDistance = DEFAULT_MAX_DISTANCE;
    private float referenceDistance = DEFAULT_REFERENCE_DISTANCE;
    private float rolloffFactor = DEFAULT_ROLLOFF_FACTOR;

    // ==================== Playback State ====================

    private final Map<BlockPos, PlaybackSession> activeSessions = new ConcurrentHashMap<>();
    private final ExecutorService playbackExecutor = Executors.newCachedThreadPool(this::createPlaybackThread);

    private volatile boolean isGamePaused = false;
    private volatile float originalMusicVolume = -1f;
    private volatile boolean isBackgroundMusicFaded = false;

    // ==================== Thread-Safe Player Position ====================

    private volatile Vec3 cachedPlayerPosition = null;
    private volatile long lastPlayerPositionUpdate = 0;

    // ==================== Constructor ====================

    private SeekableAudioPlayer() {
    }

    /**
     * Gets the singleton instance of the audio player.
     */
    public static SeekableAudioPlayer getInstance() {
        return INSTANCE;
    }

    private Thread createPlaybackThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "RhythmMod-AudioPlayback");
        thread.setDaemon(true);
        return thread;
    }

    // ==================== Configuration ====================

    /**
     * Sets the maximum distance at which audio can be heard.
     *
     * @param distance maximum distance in blocks (default: 24)
     */
    public void setMaxDistance(float distance) {
        this.maxDistance = Math.max(MIN_DISTANCE_CLAMP, distance);
    }

    /**
     * Gets the maximum audible distance.
     */
    public float getMaxDistance() {
        return maxDistance;
    }

    /**
     * Sets the reference distance for volume attenuation.
     * At this distance, volume is at 100%.
     *
     * @param distance reference distance in blocks (default: 4)
     */
    public void setReferenceDistance(float distance) {
        this.referenceDistance = Math.max(MIN_REFERENCE_DISTANCE, distance);
    }

    /**
     * Sets the rolloff factor for distance attenuation.
     * Higher values = faster volume drop-off.
     *
     * @param factor rolloff factor (default: 1.0)
     */
    public void setRolloffFactor(float factor) {
        this.rolloffFactor = Math.max(0.0f, factor);
    }

    // ==================== Playback Control ====================

    /**
     * Starts playing audio from a specific position.
     *
     * @param jukeboxPos   the jukebox position
     * @param soundEventId the sound event ID (for vanilla sounds)
     * @param customUrl    optional custom URL (for URL discs)
     * @param seekToTicks  the tick position to seek to
     * @param volume       base volume (0.0 - 1.0)
     */
    public void playFromPosition(BlockPos jukeboxPos, ResourceLocation soundEventId,
                                 String customUrl, long seekToTicks, float volume) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("playFromPosition: pos={}, seekTicks={}, volume={}",
                    jukeboxPos, seekToTicks, volume);
        }

        stop(jukeboxPos);

        long seekToMs = ticksToMilliseconds(seekToTicks);
        fadeOutBackgroundMusic();

        PlaybackSession session = new PlaybackSession(jukeboxPos, soundEventId, customUrl, seekToMs, volume, this);
        activeSessions.put(jukeboxPos, session);

        playbackExecutor.submit(() -> executePlaybackSession(session, jukeboxPos));
    }

    private void executePlaybackSession(PlaybackSession session, BlockPos jukeboxPos) {
        try {
            session.play();
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Playback error at {}: {}", jukeboxPos, e.getMessage());
        } finally {
            activeSessions.remove(jukeboxPos, session);
            if (activeSessions.isEmpty()) {
                restoreBackgroundMusic();
            }
        }
    }

    /**
     * Stops playback at a specific jukebox position.
     *
     * @param jukeboxPos the jukebox position
     */
    public void stop(BlockPos jukeboxPos) {
        PlaybackSession session = activeSessions.remove(jukeboxPos);
        if (session == null) {
            return;
        }

        session.stop();
        waitForSessionToStop(session, jukeboxPos);

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Stopped playback at {}", jukeboxPos);
        }

        if (activeSessions.isEmpty()) {
            restoreBackgroundMusic();
        }
    }

    private void waitForSessionToStop(PlaybackSession session, BlockPos pos) {
        int waitCount = 0;
        while (session.isPlaying() && waitCount < STOP_WAIT_MAX_ITERATIONS) {
            try {
                Thread.sleep(STOP_WAIT_INTERVAL_MS);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (session.isPlaying()) {
            RhythmConstants.LOGGER.warn("Session at {} did not stop in time", pos);
        }
    }

    /**
     * Checks if audio is currently playing at a position.
     *
     * @param jukeboxPos the jukebox position
     * @return true if playing
     */
    public boolean isPlaying(BlockPos jukeboxPos) {
        PlaybackSession session = activeSessions.get(jukeboxPos);
        return session != null && session.isPlaying();
    }

    /**
     * Checks if any jukebox is currently playing.
     *
     * @return true if any session is active
     */
    public boolean hasActivePlayback() {
        return !activeSessions.isEmpty();
    }

    /**
     * Updates volume for all active sessions.
     *
     * @param volume new base volume
     */
    public void updateVolume(float volume) {
        for (PlaybackSession session : activeSessions.values()) {
            session.setBaseVolume(volume);
        }
    }

    /**
     * Stops all active playback sessions.
     */
    public void stopAll() {
        for (BlockPos pos : activeSessions.keySet()) {
            stop(pos);
        }
    }

    // ==================== Game Pause Handling ====================

    /**
     * Sets whether the game is paused.
     *
     * @param paused true if game is paused
     */
    public void setGamePaused(boolean paused) {
        if (this.isGamePaused == paused) {
            return;
        }

        this.isGamePaused = paused;
        for (PlaybackSession session : activeSessions.values()) {
            session.setGamePaused(paused);
        }

        if (RhythmConstants.DEBUG_AUDIO) {
            String state = paused ? "paused" : "resumed";
            RhythmConstants.LOGGER.debug("Game {} - audio {}", state, state);
        }
    }

    /**
     * Checks if the game is currently paused.
     */
    public boolean isGamePaused() {
        return isGamePaused;
    }

    /**
     * Updates the cached player position.
     * Should be called from the render thread for thread-safe access.
     */
    public void updatePlayerPosition() {
        long now = System.currentTimeMillis();
        if (now - lastPlayerPositionUpdate < PLAYER_POSITION_CACHE_MS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null) {
            cachedPlayerPosition = player.position();
            lastPlayerPositionUpdate = now;
        }
    }

    /**
     * Gets the cached player position (thread-safe).
     */
    public Vec3 getCachedPlayerPosition() {
        return cachedPlayerPosition;
    }

    // ==================== Background Music Control ====================

    private void fadeOutBackgroundMusic() {
        if (isBackgroundMusicFaded) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        originalMusicVolume = mc.options.getSoundSourceVolume(SoundSource.MUSIC);
        isBackgroundMusicFaded = true;

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Jukebox audio active - background music suppressed");
        }
    }

    private void restoreBackgroundMusic() {
        if (!isBackgroundMusicFaded) {
            return;
        }

        isBackgroundMusicFaded = false;

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Jukebox audio stopped - background music restored");
        }
    }

    /**
     * Checks if background music should be suppressed.
     * Can be used by mixins to reduce background music volume.
     */
    public boolean shouldSuppressBackgroundMusic() {
        return isBackgroundMusicFaded;
    }

    /**
     * Gets the target volume multiplier for background music when jukeboxes are active.
     */
    public float getBackgroundMusicVolumeMultiplier() {
        return isBackgroundMusicFaded ? BACKGROUND_FADE_TARGET : 1.0f;
    }

    // ==================== Spatial Audio ====================

    /**
     * Calculates distance-based volume attenuation using inverse distance model.
     *
     * @param distance   distance from listener to source
     * @param baseVolume the base volume (0.0 - 1.0)
     * @return attenuated volume
     */
    float calculateDistanceVolume(double distance, float baseVolume) {
        if (distance <= referenceDistance) {
            return baseVolume;
        }
        if (distance >= maxDistance) {
            return 0.0f;
        }

        float attenuation = referenceDistance /
                (referenceDistance + rolloffFactor * (float) (distance - referenceDistance));

        float fadeZone = maxDistance * FADE_ZONE_PERCENTAGE;
        if (distance > maxDistance - fadeZone) {
            float fadeProgress = (float) (distance - (maxDistance - fadeZone)) / fadeZone;
            attenuation *= (1.0f - fadeProgress);
        }

        return Math.max(0.0f, Math.min(1.0f, baseVolume * attenuation));
    }

    /**
     * Gets the distance from the player to a block position.
     * Uses cached player position for thread safety.
     *
     * @param blockPos the block position
     * @return distance in blocks, or -1 if player position not available
     */
    double getPlayerDistance(BlockPos blockPos) {
        Vec3 playerPos = cachedPlayerPosition;
        if (playerPos == null) {
            return -1;
        }

        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        return playerPos.distanceTo(blockCenter);
    }

    /**
     * Calculates the effective volume for a jukebox considering other active jukeboxes.
     * Closer jukeboxes take priority, reducing volume of farther ones.
     *
     * @param jukeboxPos the jukebox position to calculate volume for
     * @param baseVolume the base volume
     * @return effective volume considering proximity to other jukeboxes
     */
    @Override
    public float calculateMultiJukeboxVolume(BlockPos jukeboxPos, float baseVolume) {
        double distance = getPlayerDistance(jukeboxPos);
        if (distance < 0) {
            return -1;
        }

        float distanceVolume = calculateDistanceVolume(distance, baseVolume);

        if (activeSessions.size() <= 1) {
            return distanceVolume;
        }

        double closestOtherDistance = findClosestOtherJukeboxDistance(jukeboxPos);

        if (distance <= closestOtherDistance) {
            return distanceVolume;
        }

        float priorityFactor = (float) (closestOtherDistance / distance);
        priorityFactor = priorityFactor * priorityFactor;

        return distanceVolume * priorityFactor;
    }

    private double findClosestOtherJukeboxDistance(BlockPos excludePos) {
        double closest = Double.MAX_VALUE;
        for (BlockPos otherPos : activeSessions.keySet()) {
            if (!otherPos.equals(excludePos)) {
                double otherDistance = getPlayerDistance(otherPos);
                if (otherDistance >= 0 && otherDistance < closest) {
                    closest = otherDistance;
                }
            }
        }
        return closest;
    }

    // ==================== Utility Methods ====================

    private static long ticksToMilliseconds(long ticks) {
        return (ticks * MS_PER_SECOND) / TICKS_PER_SECOND;
    }

    /**
     * Gets Minecraft's master volume setting.
     *
     * @return Minecraft's master volume (0.0 to 1.0)
     */
    @Override
    public float getMinecraftMasterVolume() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getSoundSourceVolume(SoundSource.MASTER);
    }
}
