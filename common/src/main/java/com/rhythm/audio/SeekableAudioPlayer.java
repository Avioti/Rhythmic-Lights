package com.rhythm.audio;

import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.IOUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
public class SeekableAudioPlayer {

    // ==================== Singleton ====================

    private static final SeekableAudioPlayer INSTANCE = new SeekableAudioPlayer();

    // ==================== Audio Buffer Configuration ====================

    private static final int BUFFER_MULTIPLIER = 4096;
    private static final int PAUSE_CHECK_INTERVAL_MS = 50;
    private static final int DISTANCE_UPDATE_INTERVAL_MS = 100;
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
    private static final float VOLUME_FADE_SPEED = 0.05f;
    private static final float VOLUME_CHANGE_THRESHOLD = 0.01f;

    // ==================== Timing Configuration ====================

    private static final long PLAYER_POSITION_CACHE_MS = 50L;
    private static final int STOP_WAIT_MAX_ITERATIONS = 50;
    private static final int STOP_WAIT_INTERVAL_MS = 10;
    private static final int VOLUME_LOG_INTERVAL = 100;

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

    private SeekableAudioPlayer() {}

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

        PlaybackSession session = new PlaybackSession(jukeboxPos, soundEventId, customUrl, seekToMs, volume);
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
            (referenceDistance + rolloffFactor * (float)(distance - referenceDistance));

        float fadeZone = maxDistance * FADE_ZONE_PERCENTAGE;
        if (distance > maxDistance - fadeZone) {
            float fadeProgress = (float)(distance - (maxDistance - fadeZone)) / fadeZone;
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
    float calculateMultiJukeboxVolume(BlockPos jukeboxPos, float baseVolume) {
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

        float priorityFactor = (float)(closestOtherDistance / distance);
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

    // ==================== Playback Session ====================

    /**
     * Internal playback session managing a single audio stream.
     */
    private class PlaybackSession {
        private final BlockPos jukeboxPos;
        private final ResourceLocation soundEventId;
        private final String customUrl;
        private final long seekToMs;

        private volatile float baseVolume;
        private volatile float currentVolume;
        private volatile boolean isPlaying = false;
        private volatile boolean isStopRequested = false;
        private volatile boolean isGamePaused = false;
        private volatile SourceDataLine audioLine;
        private volatile long lastDistanceUpdate = 0;

        private final AudioProcessor audioProcessor = new AudioProcessor();
        private final AudioQualityAnalyzer qualityAnalyzer = new AudioQualityAnalyzer();

        PlaybackSession(BlockPos jukeboxPos, ResourceLocation soundEventId,
                       String customUrl, long seekToMs, float volume) {
            this.jukeboxPos = jukeboxPos;
            this.soundEventId = soundEventId;
            this.customUrl = customUrl;
            this.seekToMs = seekToMs;
            this.baseVolume = volume;
            this.currentVolume = volume;
        }

        void play() throws Exception {
            isPlaying = true;

            try {
                String cacheKey = AudioPcmCache.getCacheKey(soundEventId, customUrl);
                logCacheKeyDetails(cacheKey);

                AudioPcmCache.CachedPcmData cachedPcm = AudioPcmCache.getInstance().get(soundEventId, customUrl);

                if (hasCachedPcmData(cachedPcm)) {
                    if (RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Using cached PCM data ({} bytes)", cachedPcm.data.length);
                    }
                    analyzeAndApplyAutoGain(cachedPcm.data, cachedPcm.format);
                    playFromCachedPcm(cachedPcm.data, cachedPcm.format);
                    return;
                }

                if (RhythmConstants.DEBUG_AUDIO) {
                    RhythmConstants.LOGGER.debug("No cached PCM data found, decoding audio...");
                    logCacheState();
                }

                playFromDecodedStream();

            } finally {
                isPlaying = false;
                audioProcessor.resetAutoGain();
                audioProcessor.resetAutoEQ();
            }
        }

        /**
         * Analyzes the audio and applies auto-gain and auto-EQ for optimal playback.
         */
        private void analyzeAndApplyAutoGain(byte[] pcmData, AudioFormat format) {
            qualityAnalyzer.analyze(pcmData, format);

            if (!qualityAnalyzer.isAnalyzed()) {
                return;
            }

            applyAutoGain();
            applyAutoEQ();
            logAudioAnalysis();
        }

        private void applyAutoGain() {
            float recommendedGain = qualityAnalyzer.getRecommendedGain();
            audioProcessor.setAutoGain(recommendedGain);
        }

        private void applyAutoEQ() {
            AudioSettings settings = AudioSettings.getInstance();
            if (!settings.isEnhancementsEnabled()) {
                return;
            }

            float[] recommendedEQ = qualityAnalyzer.getRecommendedEQ();
            audioProcessor.applyAutoEQ(recommendedEQ);
        }

        private void logAudioAnalysis() {
            if (!RhythmConstants.DEBUG_AUDIO) {
                return;
            }

            RhythmConstants.LOGGER.debug("Audio Quality: {}", qualityAnalyzer.getQualityInfo());
            RhythmConstants.LOGGER.debug("Audio Profile: {}", qualityAnalyzer.getAudioProfile().getDisplayName());
            RhythmConstants.LOGGER.debug("Applied auto-gain: {}x", qualityAnalyzer.getRecommendedGain());

            logAudioWarnings();
            logAutoEQAdjustments();
        }

        private void logAudioWarnings() {
            if (qualityAnalyzer.hasClipping()) {
                RhythmConstants.LOGGER.warn("Source audio has clipping - limiter will help");
            }
        }

        private void logAutoEQAdjustments() {
            AudioQualityAnalyzer.AudioProfile profile = qualityAnalyzer.getAudioProfile();
            if (profile == AudioQualityAnalyzer.AudioProfile.BALANCED) {
                RhythmConstants.LOGGER.debug("Balanced audio - no auto-EQ applied");
                return;
            }

            float[] eq = qualityAnalyzer.getRecommendedEQ();
            RhythmConstants.LOGGER.debug("Auto-EQ applied for {} track:", profile.getDisplayName());
            RhythmConstants.LOGGER.debug("  32Hz:{} 64Hz:{} 125Hz:{} 250Hz:{} 500Hz:{}",
                formatDb(eq[0]), formatDb(eq[1]), formatDb(eq[2]), formatDb(eq[3]), formatDb(eq[4]));
            RhythmConstants.LOGGER.debug("  1kHz:{} 2kHz:{} 4kHz:{} 8kHz:{} 16kHz:{}",
                formatDb(eq[5]), formatDb(eq[6]), formatDb(eq[7]), formatDb(eq[8]), formatDb(eq[9]));
        }

        private String formatDb(float db) {
            return String.format("%+.1fdB", db);
        }

        private void logCacheKeyDetails(String cacheKey) {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Looking for cached PCM with key: {}", cacheKey);
                RhythmConstants.LOGGER.debug("  soundEventId: {}, customUrl: {}, seekToMs: {}",
                    soundEventId, customUrl != null ? customUrl : "(null)", seekToMs);
            }
        }

        private boolean hasCachedPcmData(AudioPcmCache.CachedPcmData cachedPcm) {
            return cachedPcm != null && cachedPcm.data != null && cachedPcm.data.length > 0;
        }

        private void logCacheState() {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Cache size: {} entries, {} bytes",
                    AudioPcmCache.getInstance().getEntryCount(),
                    AudioPcmCache.getInstance().getCacheSize());
                AudioPcmCache.getInstance().debugPrintCacheKeys();
            }
        }

        private void playFromDecodedStream() throws Exception {
            InputStream audioStream = getAudioStream();
            if (audioStream == null) {
                RhythmConstants.LOGGER.error("Could not get audio stream for {}", soundEventId);
                return;
            }

            byte[] audioData = loadAudioData(audioStream);
            if (audioData == null) {
                return;
            }

            ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
            byteStream.mark(audioData.length);

            AudioInputStream audioInputStream = AudioDecoder.getAudioInputStream(byteStream);
            if (audioInputStream == null) {
                RhythmConstants.LOGGER.error("Could not create audio input stream");
                return;
            }

            AudioFormat sourceFormat = audioInputStream.getFormat();
            logAudioFormat("Source", sourceFormat);

            AudioFormat pcmFormat = AudioDecoder.buildPcmFormat(
                sourceFormat.getSampleRate(),
                sourceFormat.getChannels()
            );

            AudioInputStream pcmStream = convertToPcmIfNeeded(audioInputStream, sourceFormat, pcmFormat);
            if (pcmStream == null) {
                return;
            }

            byte[] pcmData = IOUtils.toByteArray(pcmStream);
            pcmStream.close();

            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Converted to {} bytes of PCM data", pcmData.length);
            }

            // Analyze and apply auto-gain for decoded audio
            analyzeAndApplyAutoGain(pcmData, pcmFormat);

            playPcmData(pcmData, pcmFormat);
        }

        private byte[] loadAudioData(InputStream audioStream) throws Exception {
            byte[] audioData;
            if (audioStream instanceof ByteArrayInputStream) {
                audioData = audioStream.readAllBytes();
            } else {
                audioData = IOUtils.toByteArray(audioStream);
                audioStream.close();
            }
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Loaded {} bytes of audio data", audioData.length);
            }
            return audioData;
        }

        private AudioInputStream convertToPcmIfNeeded(AudioInputStream source,
                                                       AudioFormat sourceFormat,
                                                       AudioFormat pcmFormat) {
            if (sourceFormat.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                if (RhythmConstants.DEBUG_AUDIO) {
                    RhythmConstants.LOGGER.debug("Already PCM format, no conversion needed");
                }
                return source;
            }

            AudioInputStream converted = AudioDecoder.convertToFormat(source, pcmFormat);
            if (converted == null) {
                RhythmConstants.LOGGER.error("Format conversion failed!");
            }
            return converted;
        }

        private void playFromCachedPcm(byte[] pcmData, AudioFormat pcmFormat) throws Exception {
            playPcmData(pcmData, pcmFormat);
        }

        private void playPcmData(byte[] pcmData, AudioFormat pcmFormat) throws Exception {
            audioProcessor.setSampleRate(pcmFormat.getSampleRate());

            int frameSize = pcmFormat.getFrameSize();
            int channels = pcmFormat.getChannels();
            int startPosition = calculateSeekPosition(pcmData.length, pcmFormat);

            if (isStopRequested) {
                return;
            }

            openAudioLine(pcmFormat);
            audioLine.start();

            int bufferSize = BUFFER_MULTIPLIER * frameSize;

            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Starting playback (channels: {}, seek: {} bytes)", channels, startPosition);
                RhythmConstants.LOGGER.debug("Volume info: baseVolume={}, currentVolume={}", baseVolume, currentVolume);
            }

            byte[] workBuffer = new byte[bufferSize];
            executePlaybackLoop(pcmData, pcmFormat, startPosition, workBuffer, bufferSize, frameSize, channels);

            finishPlayback();
        }

        private void executePlaybackLoop(byte[] pcmData, AudioFormat pcmFormat, int startPosition,
                                          byte[] workBuffer, int bufferSize, int frameSize, int channels) {
            int playbackLoopCount = 0;
            boolean shouldLoop;

            do {
                int currentPosition = (playbackLoopCount == 0) ? startPosition : 0;
                int bufferLoopCount = 0;

                shouldLoop = ClientSongManager.getInstance().isLoopEnabled(jukeboxPos);

                if (playbackLoopCount > 0) {
                    handleLoopRestart(playbackLoopCount);
                }

                while (!isStopRequested && currentPosition < pcmData.length) {
                    if (isGamePaused && !waitWhilePaused()) {
                        break;
                    }

                    updateDistanceBasedVolume();

                    int bytesToWrite = Math.min(bufferSize, pcmData.length - currentPosition);
                    bytesToWrite = alignToFrameBoundary(bytesToWrite, frameSize);

                    if (bytesToWrite <= 0) {
                        break;
                    }

                    if (bufferLoopCount % VOLUME_LOG_INTERVAL == 0 && RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Playback buffer #{}: currentVolume={}, position={}",
                            bufferLoopCount, currentVolume, currentPosition);
                    }
                    bufferLoopCount++;

                    processAndWriteAudio(pcmData, currentPosition, bytesToWrite, workBuffer, channels);
                    currentPosition += bytesToWrite;
                }

                playbackLoopCount++;
                shouldLoop = ClientSongManager.getInstance().isLoopEnabled(jukeboxPos);

            } while (shouldLoop && !isStopRequested && ClientSongManager.getInstance().isPlaying(jukeboxPos));

            logPlaybackCompletion(shouldLoop);
        }

        private void handleLoopRestart(int loopCount) {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Restarting playback (loop #{}) at {}", loopCount, jukeboxPos);
            }

            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    long newStartTime = mc.level.getGameTime();
                    ClientSongManager.getInstance().setPlaybackStartTime(jukeboxPos, newStartTime);
                    if (RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Reset playback start time to {}", newStartTime);
                    }
                }
            } catch (Exception e) {
                RhythmConstants.LOGGER.warn("Could not reset playback start time: {}", e.getMessage());
            }
        }

        private void logPlaybackCompletion(boolean wasLooping) {
            if (wasLooping && isStopRequested) {
                if (RhythmConstants.DEBUG_AUDIO) {
                    RhythmConstants.LOGGER.debug("Loop playback stopped by user at {}", jukeboxPos);
                }
            } else if (!wasLooping && RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Playback finished (no loop) at {}", jukeboxPos);
            }
        }

        private int calculateSeekPosition(int dataLength, AudioFormat format) {
            float sampleRate = format.getSampleRate();
            int frameSize = format.getFrameSize();

            if (RhythmConstants.DEBUG_AUDIO) {
                double audioDurationMs = (dataLength / (double)(sampleRate * frameSize)) * MS_PER_SECOND;
                RhythmConstants.LOGGER.debug("Seek calculation: seekToMs={}, sampleRate={}, frameSize={}, dataLength={} bytes",
                    seekToMs, sampleRate, frameSize, dataLength);
                RhythmConstants.LOGGER.debug("Audio duration: {} ms ({} seconds)",
                    audioDurationMs, audioDurationMs / MS_PER_SECOND);
            }

            long bytesToSkip = (long) ((seekToMs / (double)MS_PER_SECOND) * sampleRate * frameSize);
            bytesToSkip = alignToFrameBoundary(bytesToSkip, frameSize);

            if (bytesToSkip >= dataLength) {
                RhythmConstants.LOGGER.warn("Seek position {} exceeds audio length {}, starting from beginning",
                    bytesToSkip, dataLength);
                bytesToSkip = 0;
            }

            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Final seek position: {} bytes", bytesToSkip);
            }

            return (int) bytesToSkip;
        }

        private void openAudioLine(AudioFormat format) throws Exception {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                RhythmConstants.LOGGER.error("Audio line not supported for format: {}", format);
                throw new Exception("Audio line not supported");
            }

            try {
                audioLine = (SourceDataLine) AudioSystem.getLine(info);
                audioLine.open(format);
                if (RhythmConstants.DEBUG_AUDIO) {
                    RhythmConstants.LOGGER.debug("Audio line opened successfully");
                }
            } catch (Exception e) {
                RhythmConstants.LOGGER.error("Failed to open audio line: {}", e.getMessage());
                throw e;
            }
        }

        private void updateDistanceBasedVolume() {
            long now = System.currentTimeMillis();
            if (now - lastDistanceUpdate < DISTANCE_UPDATE_INTERVAL_MS) {
                return;
            }
            lastDistanceUpdate = now;

            float newVolume = calculateMultiJukeboxVolume(jukeboxPos, baseVolume);

            if (newVolume < 0) {
                newVolume = baseVolume;
            }

            if (Math.abs(newVolume - currentVolume) > VOLUME_CHANGE_THRESHOLD) {
                currentVolume = currentVolume + (newVolume - currentVolume) * VOLUME_FADE_SPEED;
            }
        }

        private void processAndWriteAudio(byte[] pcmData, int position, int length,
                                          byte[] workBuffer, int channels) {
            System.arraycopy(pcmData, position, workBuffer, 0, length);

            AudioSettings settings = AudioSettings.getInstance();
            float userMasterVolume = settings.getMasterVolume();
            float effectiveVolume = currentVolume * userMasterVolume;

            float originalMasterVolume = settings.getMasterVolume();
            settings.setMasterVolume(effectiveVolume);

            audioProcessor.process(workBuffer, 0, length, channels);

            settings.setMasterVolume(originalMasterVolume);

            audioLine.write(workBuffer, 0, length);
        }

        private void finishPlayback() {
            if (!isStopRequested && audioLine != null) {
                audioLine.drain();
            }
            closeAudioLine();
        }

        private void closeAudioLine() {
            if (audioLine != null) {
                audioLine.stop();
                audioLine.close();
            }
        }

        private long alignToFrameBoundary(long bytes, int frameSize) {
            return (bytes / frameSize) * frameSize;
        }

        private int alignToFrameBoundary(int bytes, int frameSize) {
            return (bytes / frameSize) * frameSize;
        }

        private InputStream getAudioStream() {
            try {
                if (customUrl != null && !customUrl.isEmpty()) {
                    if (RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Getting audio stream from custom URL");
                    }
                    InputStream stream = RhythmAudioCache.getAudioStream(customUrl);
                    if (stream == null) {
                        RhythmConstants.LOGGER.error("RhythmAudioCache returned null for URL: {}", customUrl);
                    }
                    return stream;
                }

                if (RhythmConstants.DEBUG_AUDIO) {
                    RhythmConstants.LOGGER.debug("Getting audio stream from sound event: {}", soundEventId);
                }
                InputStream stream = SoundFileResolver.getStreamForSound(soundEventId);
                if (stream == null) {
                    RhythmConstants.LOGGER.error("SoundFileResolver returned null for: {}", soundEventId);
                }
                return stream;
            } catch (Exception e) {
                RhythmConstants.LOGGER.error("Error getting audio stream: {}", e.getMessage());
                return null;
            }
        }

        private void logAudioFormat(String label, AudioFormat format) {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("{} format: {}, {}Hz, {} channels",
                    label, format.getEncoding(), format.getSampleRate(), format.getChannels());
            }
        }

        void stop() {
            isStopRequested = true;
            closeAudioLine();
        }

        boolean isPlaying() {
            return isPlaying;
        }

        void setBaseVolume(float volume) {
            this.baseVolume = volume;
        }

        void setGamePaused(boolean paused) {
            this.isGamePaused = paused;
            if (audioLine != null && audioLine.isOpen()) {
                if (paused) {
                    audioLine.stop();
                } else {
                    audioLine.start();
                }
            }
        }

        private boolean waitWhilePaused() {
            while (isGamePaused && !isStopRequested) {
                try {
                    Thread.sleep(PAUSE_CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return !isStopRequested;
        }
    }
}

