package com.rhythm.audio.playback;

import com.rhythm.audio.*;
import com.rhythm.audio.analysis.AudioQualityAnalyzer;
import com.rhythm.audio.cache.AudioPcmCache;
import com.rhythm.audio.cache.RhythmAudioCache;
import com.rhythm.audio.io.AudioDecoder;
import com.rhythm.audio.io.SoundFileResolver;
import com.rhythm.audio.state.ClientSongManager;
import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.IOUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Manages a single audio playback session for a jukebox.
 * <p>
 * Handles audio decoding, seeking, volume control, and playback loop management.
 */
public class PlaybackSession {

    // ==================== Constants ====================

    private static final int BUFFER_MULTIPLIER = 4096;
    private static final int PAUSE_CHECK_INTERVAL_MS = 50;
    private static final int DISTANCE_UPDATE_INTERVAL_MS = 100;
    private static final long MS_PER_SECOND = 1000L;
    private static final float VOLUME_FADE_SPEED = 0.05f;
    private static final float VOLUME_CHANGE_THRESHOLD = 0.01f;
    private static final int VOLUME_LOG_INTERVAL = 100;

    // ==================== Session Configuration ====================

    private final BlockPos jukeboxPos;
    private final ResourceLocation soundEventId;
    private final String customUrl;
    private final long seekToMs;
    private final VolumeCalculator volumeCalculator;

    // ==================== Playback State ====================

    private volatile float baseVolume;
    private volatile float currentVolume;
    private volatile boolean isPlaying = false;
    private volatile boolean isStopRequested = false;
    private volatile boolean isGamePaused = false;
    private volatile SourceDataLine audioLine;
    private volatile long lastDistanceUpdate = 0;

    // ==================== Audio Processing ====================

    private final AudioProcessor audioProcessor = new AudioProcessor();
    private final AudioQualityAnalyzer qualityAnalyzer = new AudioQualityAnalyzer();

    /**
     * Creates a new playback session.
     *
     * @param jukeboxPos       the jukebox block position
     * @param soundEventId     the sound event ID for vanilla sounds
     * @param customUrl        optional custom URL for URL discs
     * @param seekToMs         position to seek to in milliseconds
     * @param volume           base volume (0.0 - 1.0)
     * @param volumeCalculator calculator for spatial audio volume
     */
    public PlaybackSession(BlockPos jukeboxPos, ResourceLocation soundEventId,
                           String customUrl, long seekToMs, float volume,
                           VolumeCalculator volumeCalculator) {
        this.jukeboxPos = jukeboxPos;
        this.soundEventId = soundEventId;
        this.customUrl = customUrl;
        this.seekToMs = seekToMs;
        this.baseVolume = volume;
        this.currentVolume = volume;
        this.volumeCalculator = volumeCalculator;
    }

    /**
     * Starts audio playback.
     *
     * @throws Exception if audio cannot be played
     */
    public void play() throws Exception {
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
                playPcmData(cachedPcm.data, cachedPcm.format);
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
     * Stops the playback session.
     */
    public void stop() {
        isStopRequested = true;
        closeAudioLine();
    }

    /**
     * Checks if the session is currently playing.
     *
     * @return true if playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Sets the base volume for this session.
     *
     * @param volume new base volume (0.0 - 1.0)
     */
    public void setBaseVolume(float volume) {
        this.baseVolume = volume;
    }

    /**
     * Sets whether the game is paused.
     *
     * @param paused true if game is paused
     */
    public void setGamePaused(boolean paused) {
        this.isGamePaused = paused;
        if (audioLine != null && audioLine.isOpen()) {
            if (paused) {
                audioLine.stop();
            } else {
                audioLine.start();
            }
        }
    }

    // ==================== Audio Analysis ====================

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

    // ==================== Cache Handling ====================

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

    // ==================== Stream Decoding ====================

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

    // ==================== PCM Playback ====================

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
        executePlaybackLoop(pcmData, startPosition, workBuffer, bufferSize, frameSize, channels);

        finishPlayback();
    }

    private void executePlaybackLoop(byte[] pcmData, int startPosition,
                                     byte[] workBuffer, int bufferSize, int frameSize, int channels) {
        int loopIteration = 0;
        boolean shouldLoop;

        do {
            int currentPosition = (loopIteration == 0) ? startPosition : 0;

            if (loopIteration > 0) {
                handleLoopRestart(loopIteration);
            }

            processBufferLoop(pcmData, workBuffer, bufferSize, frameSize, channels, currentPosition);

            loopIteration++;
            shouldLoop = ClientSongManager.getInstance().isLoopEnabled(jukeboxPos);

        } while (shouldLoop && !isStopRequested && ClientSongManager.getInstance().isPlaying(jukeboxPos));

        logPlaybackCompletion(shouldLoop);
    }

    private void processBufferLoop(byte[] pcmData, byte[] workBuffer, int bufferSize,
                                   int frameSize, int channels, int startPosition) {
        int bufferIteration = 0;
        int currentPosition = startPosition;

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

            logBufferProgress(bufferIteration, currentPosition);
            bufferIteration++;

            processAndWriteAudio(pcmData, currentPosition, bytesToWrite, workBuffer, channels);
            currentPosition += bytesToWrite;
        }
    }

    private void logBufferProgress(int bufferIteration, int currentPosition) {
        if (bufferIteration % VOLUME_LOG_INTERVAL == 0 && RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Playback buffer #{}: currentVolume={}, position={}",
                    bufferIteration, currentVolume, currentPosition);
        }
    }

    private void handleLoopRestart(int loopIteration) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Restarting playback (loop #{}) at {}", loopIteration, jukeboxPos);
        }

        resetPlaybackStartTime();
    }

    private void resetPlaybackStartTime() {
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

    private void logPlaybackCompletion(boolean isLooping) {
        if (isLooping && isStopRequested) {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Loop playback stopped by user at {}", jukeboxPos);
            }
        } else if (!isLooping && RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Playback finished (no loop) at {}", jukeboxPos);
        }
    }

    // ==================== Seek Position Calculation ====================

    private int calculateSeekPosition(int dataLength, AudioFormat format) {
        float sampleRate = format.getSampleRate();
        int frameSize = format.getFrameSize();

        if (RhythmConstants.DEBUG_AUDIO) {
            double audioDurationMs = (dataLength / (double) (sampleRate * frameSize)) * MS_PER_SECOND;
            RhythmConstants.LOGGER.debug("Seek calculation: seekToMs={}, sampleRate={}, frameSize={}, dataLength={} bytes",
                    seekToMs, sampleRate, frameSize, dataLength);
            RhythmConstants.LOGGER.debug("Audio duration: {} ms ({} seconds)",
                    audioDurationMs, audioDurationMs / MS_PER_SECOND);
        }

        long bytesToSkip = (long) ((seekToMs / (double) MS_PER_SECOND) * sampleRate * frameSize);
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

    // ==================== Audio Line Management ====================

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

    private void closeAudioLine() {
        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
    }

    private void finishPlayback() {
        if (!isStopRequested && audioLine != null) {
            audioLine.drain();
        }
        closeAudioLine();
    }

    // ==================== Volume Management ====================

    private void updateDistanceBasedVolume() {
        long now = System.currentTimeMillis();
        if (now - lastDistanceUpdate < DISTANCE_UPDATE_INTERVAL_MS) {
            return;
        }
        lastDistanceUpdate = now;

        float newVolume = volumeCalculator.calculateMultiJukeboxVolume(jukeboxPos, baseVolume);

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
        float savedMasterVolume = settings.getMasterVolume();
        float effectiveVolume = calculateEffectiveVolume(savedMasterVolume);

        settings.setMasterVolume(effectiveVolume);
        audioProcessor.process(workBuffer, 0, length, channels);
        settings.setMasterVolume(savedMasterVolume);

        audioLine.write(workBuffer, 0, length);
    }

    private float calculateEffectiveVolume(float djScreenVolume) {
        float mcMasterVolume = volumeCalculator.getMinecraftMasterVolume();
        return mcMasterVolume * djScreenVolume * currentVolume;
    }

    // ==================== Utility Methods ====================

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

