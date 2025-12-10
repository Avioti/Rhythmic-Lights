package com.rhythm.audio.analysis;

import com.rhythm.audio.io.AudioDecoder;
import com.rhythm.audio.cache.AudioPcmCache;
import com.rhythm.audio.io.SoundFileResolver;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.IOUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-computes frequency analysis (FFT) data for audio files.
 * Supports both Minecraft sound events and URL-based audio (YouTube, etc).
 * Caches results for instant playback on subsequent uses.
 * <p>
 * Delegates to:
 * <ul>
 *   <li>{@link FFTAnalyzer} - FFT calculations and frequency band extraction</li>
 *   <li>{@link OnsetDetector} - Beat and onset detection</li>
 * </ul>
 */
public class AudioPreComputer {

    // ==================== Cache Validation ====================

    private static final int CACHE_VALIDATION_SAMPLE_COUNT = 100;
    private static final float CACHE_VALIDATION_MIN_SUM = 0.001f;
    private static final float DURATION_MISMATCH_THRESHOLD = 0.9f;

    // ==================== Audio Processing ====================

    private static final int BITS_PER_BYTE = 8;
    private static final double SAMPLE_NORMALIZATION_FACTOR = 32768.0;
    private static final int TICKS_PER_SECOND = 20;

    // ==================== State ====================

    private static final Map<String, FrequencyData> urlCache = new ConcurrentHashMap<>();
    private static boolean hasLoggedFormats = false;

    // ==================== Cache Management ====================

    /**
     * Clears all cached FFT data.
     * Useful for troubleshooting or when cached data becomes corrupt.
     */
    public static void clearCache() {
        int size = urlCache.size();
        urlCache.clear();
        RhythmConstants.LOGGER.info("Cleared FFT cache ({} entries removed)", size);
    }

    /**
     * Returns the number of cached FFT analyses.
     */
    public static int getCacheSize() {
        return urlCache.size();
    }

    // ==================== URL Analysis ====================

    /**
     * Analyzes audio from a web URL with intelligent caching.
     *
     * @param urlString   the URL to download audio from
     * @param startTime   game time when song started
     * @param jukeboxPos  position of the jukebox (unused, kept for API compatibility)
     * @return CompletableFuture containing the frequency analysis data
     */
    public static CompletableFuture<FrequencyData> preComputeFrequenciesFromUrl(
            String urlString, long startTime, BlockPos jukeboxPos) {
        return CompletableFuture.supplyAsync(() -> analyzeUrlAudio(urlString, startTime));
    }

    private static FrequencyData analyzeUrlAudio(String urlString, long startTime) {
        FrequencyData cachedData = getValidCachedData(urlString);
        if (cachedData != null) {
            return createWithNewStartTime(cachedData, startTime);
        }

        RhythmConstants.LOGGER.info("Starting FFT analysis from URL: {}", urlString);

        try {
            return downloadAndProcessUrl(urlString, startTime);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error processing audio from URL: {}", urlString, e);
            return null;
        }
    }

    private static FrequencyData downloadAndProcessUrl(String urlString, long startTime) throws Exception {
        URL url = new URL(urlString);
        try (InputStream rawUrlStream = url.openStream()) {
            byte[] audioData = downloadAudioData(rawUrlStream);
            return processDownloadedAudio(audioData, urlString, startTime);
        }
    }

    private static byte[] downloadAudioData(InputStream stream) throws IOException {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Downloading audio from URL...");
        }
        byte[] audioData = IOUtils.toByteArray(stream);
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Downloaded {} bytes", audioData.length);
        }
        return audioData;
    }

    private static FrequencyData processDownloadedAudio(byte[] audioData, String cacheKey, long startTime) {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
        byteStream.mark(audioData.length);

        String pcmCacheKey = AudioPcmCache.getCacheKey(null, cacheKey);
        FrequencyData result = processAudioStream(byteStream, startTime, pcmCacheKey);

        cacheIfValid(cacheKey, result);
        return result;
    }

    /**
     * Analyzes audio from a file URL using a custom cache key.
     * Useful for cached files where we want to cache by original URL, not file path.
     *
     * @param fileUrlString the file:// URL to the actual audio file
     * @param cacheKey      the key to use for caching (e.g., original YouTube URL)
     * @param startTime     game time when song started
     * @param jukeboxPos    position of the jukebox (unused, kept for API compatibility)
     * @return CompletableFuture containing the frequency analysis data
     */
    public static CompletableFuture<FrequencyData> preComputeFrequenciesFromUrlWithCacheKey(
            String fileUrlString, String cacheKey, long startTime, BlockPos jukeboxPos) {
        return CompletableFuture.supplyAsync(() -> analyzeFileAudio(fileUrlString, cacheKey, startTime));
    }

    private static FrequencyData analyzeFileAudio(String fileUrlString, String cacheKey, long startTime) {
        FrequencyData cachedData = getValidCachedData(cacheKey);
        if (cachedData != null) {
            return createWithNewStartTime(cachedData, startTime);
        }

        logFileAnalysisStart(fileUrlString, cacheKey);

        try {
            return readAndProcessFile(fileUrlString, cacheKey, startTime);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error processing audio from file: {}", fileUrlString, e);
            return null;
        }
    }

    private static void logFileAnalysisStart(String fileUrlString, String cacheKey) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Starting FFT analysis from file: {}", fileUrlString);
            RhythmConstants.LOGGER.debug("Will cache with key: {}", cacheKey);
        }
    }

    private static FrequencyData readAndProcessFile(String fileUrlString, String cacheKey, long startTime)
            throws Exception {
        InputStream rawStream = openFileStream(fileUrlString);
        if (rawStream == null) {
            return null;
        }

        try (InputStream inputStream = rawStream) {
            byte[] audioData = readFileData(inputStream);
            return processDownloadedAudio(audioData, cacheKey, startTime);
        }
    }

    private static byte[] readFileData(InputStream inputStream) throws IOException {
        byte[] audioData = IOUtils.toByteArray(inputStream);
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Read {} bytes", audioData.length);
        }
        return audioData;
    }

    /**
     * Opens an input stream for a file URL, handling both file:// and http:// URLs.
     */
    private static InputStream openFileStream(String fileUrlString) throws IOException {
        if (fileUrlString.startsWith("file:///") || fileUrlString.startsWith("file:/")) {
            return openLocalFileStream(fileUrlString);
        }
        return new URL(fileUrlString).openStream();
    }

    /**
     * Opens a local file stream from a file:// URL.
     */
    private static InputStream openLocalFileStream(String fileUrlString) throws IOException {
        String filePath = fileUrlString.replace("file:///", "").replace("file:/", "");
        File file = new File(filePath);

        if (!file.exists()) {
            // Try alternative parsing for Windows paths
            filePath = fileUrlString.substring(fileUrlString.indexOf(":/") - 1);
            file = new File(filePath);
        }

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Reading from local file: {}", file.getAbsolutePath());
            RhythmConstants.LOGGER.debug("File exists: {}, Size: {} bytes", file.exists(), file.length());
        }

        return new FileInputStream(file);
    }

    // ==================== Sound Event Analysis ====================

    /**
     * Analyzes audio from any SoundEvent ID (vanilla or modded).
     * Uses SoundFileResolver to get the actual file from Minecraft's sound system.
     *
     * @param soundEventId the sound event ID from the jukebox
     * @param startTime    game time when song started
     * @return CompletableFuture containing the frequency analysis data
     */
    public static CompletableFuture<FrequencyData> preComputeFrequencies(
            ResourceLocation soundEventId, long startTime) {
        return CompletableFuture.supplyAsync(() -> {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Starting FFT analysis for sound event: {}", soundEventId);
            }

            try {
                InputStream bufferedStream = SoundFileResolver.getStreamForSound(soundEventId);
                if (bufferedStream == null) {
                    RhythmConstants.LOGGER.error("Could not resolve sound event to file: {}", soundEventId);
                    return null;
                }

                String cacheKey = AudioPcmCache.getCacheKey(soundEventId, null);
                return processAudioStream(bufferedStream, startTime, cacheKey);
            } catch (Exception e) {
                RhythmConstants.LOGGER.error("Error processing audio for sound event: {}", soundEventId, e);
                return null;
            }
        });
    }

    // ==================== Cache Helpers ====================

    /**
     * Retrieves and validates cached FFT data for the given key.
     * Returns null if not cached or if cached data is invalid/corrupt.
     */
    private static FrequencyData getValidCachedData(String cacheKey) {
        FrequencyData cachedData = urlCache.get(cacheKey);
        if (cachedData == null) {
            return null;
        }

        if (!isDataStructureValid(cachedData)) {
            RhythmConstants.LOGGER.warn("Cached FFT data is invalid! Removing from cache: {}", cacheKey);
            urlCache.remove(cacheKey);
            return null;
        }

        if (!hasNonZeroContent(cachedData.getBassMap())) {
            RhythmConstants.LOGGER.warn("Cached FFT data is corrupt (all zeros)! Removing from cache: {}", cacheKey);
            urlCache.remove(cacheKey);
            return null;
        }

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Using cached FFT data for: {}", cacheKey);
        }
        return cachedData;
    }

    /**
     * Checks if the frequency data has valid structure (non-null, has duration, has data).
     */
    private static boolean isDataStructureValid(FrequencyData data) {
        return data != null
            && data.getDurationTicks() > 0
            && data.getBassMap().length > 0;
    }

    /**
     * Samples the data array to check if it contains non-zero values.
     */
    private static boolean hasNonZeroContent(float[] data) {
        int sampleCount = Math.min(CACHE_VALIDATION_SAMPLE_COUNT, data.length);
        float sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            sum += Math.abs(data[i]);
        }
        return sum > CACHE_VALIDATION_MIN_SUM;
    }

    /**
     * Creates a new FrequencyData with the same content but updated start time.
     */
    private static FrequencyData createWithNewStartTime(FrequencyData source, long startTime) {
        return new FrequencyData(
            source.getSubBassMap(), source.getDeepBassMap(), source.getBassMap(),
            source.getLowMidMap(), source.getMidLowMap(), source.getMidMap(),
            source.getMidHighMap(), source.getHighMidMap(), source.getHighMap(),
            source.getVeryHighMap(), source.getUltraMap(), source.getTopMap(),
            startTime
        );
    }

    /**
     * Caches the result if it's valid and contains real audio data.
     */
    private static void cacheIfValid(String cacheKey, FrequencyData result) {
        if (result == null || result.getDurationTicks() <= 0) {
            RhythmConstants.LOGGER.warn("FFT processing returned null or invalid data, not caching");
            return;
        }

        float[] bassMap = result.getBassMap();
        if (bassMap.length == 0) {
            RhythmConstants.LOGGER.warn("Refusing to cache FFT data - bass map is empty!");
            return;
        }

        if (!hasNonZeroContent(bassMap)) {
            RhythmConstants.LOGGER.warn("Refusing to cache FFT data - all values are zero!");
            return;
        }

        urlCache.put(cacheKey, result);
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Cached FFT data for future use: {}", cacheKey);
        }
    }

    // ==================== Core Audio Processing ====================

    /**
     * Core audio processing logic - works with any InputStream.
     * Converts audio to PCM, performs FFT analysis, and generates frequency data.
     *
     * @param inputStream the audio input stream (must support mark/reset)
     * @param startTime   the game time when the song started
     * @param pcmCacheKey optional cache key for storing decoded PCM data
     * @return the frequency analysis data, or null on error
     */
    private static FrequencyData processAudioStream(InputStream inputStream, long startTime, String pcmCacheKey) {
        try {
            logMarkSupportStatus(inputStream);
            logAvailableAudioFormats();

            byte[] pcmBytes = decodeToPcm(inputStream, pcmCacheKey);
            if (pcmBytes == null) {
                return null;
            }

            return analyzeAndConvert(pcmBytes, startTime);
        } catch (UnsupportedAudioFileException e) {
            logUnsupportedFormatError(e);
            return null;
        } catch (IOException e) {
            RhythmConstants.LOGGER.error("IO error processing audio stream", e);
            return null;
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Unexpected error processing audio stream", e);
            return null;
        }
    }

    private static byte[] decodeToPcm(InputStream inputStream, String pcmCacheKey)
            throws UnsupportedAudioFileException, IOException {
        AudioInputStream audioInputStream = AudioDecoder.getAudioInputStream(inputStream);
        if (audioInputStream == null) {
            throw new UnsupportedAudioFileException("Could not decode audio stream with any available reader");
        }

        AudioFormat format = audioInputStream.getFormat();
        logAudioFormat(format, audioInputStream.getFrameLength());

        AudioFormat decodedFormat = AudioDecoder.buildPcmFormat(format.getSampleRate(), format.getChannels());
        AudioInputStream pcmStream = AudioDecoder.convertToFormat(audioInputStream, decodedFormat);
        if (pcmStream == null) {
            RhythmConstants.LOGGER.error("Failed to convert audio to PCM format");
            return null;
        }

        byte[] pcmBytes = IOUtils.toByteArray(pcmStream);
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Loaded {} bytes of audio data", pcmBytes.length);
        }

        cachePcmDataIfNeeded(pcmCacheKey, pcmBytes, decodedFormat);
        return pcmBytes;
    }

    private static FrequencyData analyzeAndConvert(byte[] pcmBytes, long startTime) {
        AudioFormat decodedFormat = AudioDecoder.buildPcmFormat(44100, 2);
        double[] audioSamples = convertBytesToMonoSamples(pcmBytes, decodedFormat);
        int expectedTicks = calculateExpectedTicks(audioSamples.length, decodedFormat.getSampleRate());

        logSampleStatistics(audioSamples, decodedFormat.getSampleRate());

        List<double[]> rawBands = FFTAnalyzer.calculateRawBands(audioSamples, decodedFormat.getSampleRate());
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("FFT analysis produced {} frequency frames", rawBands.size());
        }

        FrequencyData result = convertToTickData(rawBands, decodedFormat.getSampleRate(), startTime);
        validateDuration(result, expectedTicks);

        return result;
    }

    private static void logMarkSupportStatus(InputStream inputStream) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Processing audio stream (markSupported: {})", inputStream.markSupported());
        }
    }

    private static void logAudioFormat(AudioFormat format, long frameLength) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Detected audio format: {}, {}Hz, {} channels",
                format.getEncoding(), format.getSampleRate(), format.getChannels());

            if (frameLength != AudioSystem.NOT_SPECIFIED) {
                double durationSeconds = frameLength / format.getFrameRate();
                RhythmConstants.LOGGER.debug("Decoder reports: {} frames, {} seconds", frameLength,
                    String.format("%.2f", durationSeconds));
            }
        }
    }

    private static void cachePcmDataIfNeeded(String pcmCacheKey, byte[] pcmBytes, AudioFormat format) {
        if (pcmCacheKey != null && pcmBytes.length > 0) {
            AudioPcmCache.getInstance().put(pcmCacheKey, pcmBytes, format);
        }
    }

    private static int calculateExpectedTicks(int sampleCount, float sampleRate) {
        double durationSeconds = sampleCount / (double) sampleRate;
        return (int) (durationSeconds * TICKS_PER_SECOND);
    }

    private static void logSampleStatistics(double[] samples, float sampleRate) {
        if (!RhythmConstants.DEBUG_AUDIO) {
            return;
        }

        double durationSeconds = samples.length / (double) sampleRate;
        RhythmConstants.LOGGER.debug("Audio samples: {}, Sample rate: {} Hz", samples.length, sampleRate);
        RhythmConstants.LOGGER.debug("Calculated duration: {} seconds ({} ticks)",
            String.format("%.2f", durationSeconds), String.format("%.1f", durationSeconds * TICKS_PER_SECOND));

        logSampleRange(samples);
    }

    private static void logSampleRange(double[] samples) {
        double minSample = Double.MAX_VALUE;
        double maxSample = Double.MIN_VALUE;
        for (double sample : samples) {
            if (sample < minSample) minSample = sample;
            if (sample > maxSample) maxSample = sample;
        }
        RhythmConstants.LOGGER.debug("Mono sample range: [{}, {}]",
            String.format("%.6f", minSample), String.format("%.6f", maxSample));
    }

    private static void validateDuration(FrequencyData result, int expectedTicks) {
        if (result == null) {
            return;
        }

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Final result: {} ticks ({} seconds)",
                result.getDurationTicks(), result.getDurationTicks() / 20.0);
        }

        if (result.getDurationTicks() < expectedTicks * DURATION_MISMATCH_THRESHOLD) {
            RhythmConstants.LOGGER.warn("Significant duration mismatch! Expected ~{} ticks, got {} ticks",
                expectedTicks, result.getDurationTicks());
            RhythmConstants.LOGGER.warn("Audio might be truncated or corrupt!");
        }
    }

    private static void logUnsupportedFormatError(UnsupportedAudioFileException e) {
        RhythmConstants.LOGGER.error("Unsupported audio format - make sure OGG/MP3 libraries are installed", e);
        RhythmConstants.LOGGER.error("Possible causes:");
        RhythmConstants.LOGGER.error("  1. The sound file is not OGG Vorbis (Minecraft's standard format)");
        RhythmConstants.LOGGER.error("  2. The sound file is corrupted");
        RhythmConstants.LOGGER.error("  3. Required audio libraries (vorbisspi, mp3spi) are missing");
    }

    // ==================== Audio Conversion ====================

    /**
     * Converts raw PCM bytes to mono audio samples normalized to [-1.0, 1.0].
     */
    private static double[] convertBytesToMonoSamples(byte[] bytes, AudioFormat format) {
        int bytesPerSample = format.getSampleSizeInBits() / BITS_PER_BYTE;
        int channels = format.getChannels();
        int frameSize = bytesPerSample * channels;
        int frameCount = bytes.length / frameSize;

        double[] monoSamples = new double[frameCount];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < frameCount; i++) {
            double sampleSum = 0;
            for (int c = 0; c < channels; c++) {
                sampleSum += buffer.getShort() / SAMPLE_NORMALIZATION_FACTOR;
            }
            monoSamples[i] = sampleSum / channels;
        }
        return monoSamples;
    }


    // ==================== Tick Data Conversion ====================

    /**
     * Converts raw FFT band data to per-tick frequency data with onset detection.
     */
    private static FrequencyData convertToTickData(List<double[]> rawBands, float sampleRate, long startTime) {
        if (rawBands.isEmpty()) {
            return new FrequencyData(startTime);
        }

        double framesPerSecond = sampleRate / FFTAnalyzer.HOP_SIZE;
        double framesPerTick = framesPerSecond / TICKS_PER_SECOND;
        int numResultingTicks = (int) Math.ceil(rawBands.size() / framesPerTick);

        float[][] tickArrays = createTickArrays(numResultingTicks);
        averageBandsToTicks(rawBands, tickArrays, framesPerTick);

        float[][] onsetArrays = detectAllOnsets(tickArrays);
        normalizeAllArrays(onsetArrays);

        FrequencyData frequencyData = new FrequencyData(
            onsetArrays[0], onsetArrays[1], onsetArrays[2], onsetArrays[3],
            onsetArrays[4], onsetArrays[5], onsetArrays[6], onsetArrays[7],
            onsetArrays[8], onsetArrays[9], onsetArrays[10], onsetArrays[11],
            startTime
        );

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("FFT complete: {} ticks (12-band ultra-precision onset detection)",
                numResultingTicks);
            logSampleTick(onsetArrays, numResultingTicks);
        }

        return frequencyData;
    }

    private static float[][] createTickArrays(int size) {
        float[][] arrays = new float[FFTAnalyzer.FREQUENCY_BAND_COUNT][];
        for (int i = 0; i < FFTAnalyzer.FREQUENCY_BAND_COUNT; i++) {
            arrays[i] = new float[size];
        }
        return arrays;
    }

    private static void averageBandsToTicks(List<double[]> rawBands, float[][] tickArrays, double framesPerTick) {
        int numTicks = tickArrays[0].length;

        for (int i = 0; i < numTicks; i++) {
            int startFrame = (int) (i * framesPerTick);
            int endFrame = (int) ((i + 1) * framesPerTick);
            averageFrameRange(rawBands, tickArrays, i, startFrame, endFrame);
        }
    }

    private static void averageFrameRange(List<double[]> rawBands, float[][] tickArrays,
                                          int tickIndex, int startFrame, int endFrame) {
        double[] sums = new double[FFTAnalyzer.FREQUENCY_BAND_COUNT];
        int frameCount = 0;

        for (int j = startFrame; j < endFrame && j < rawBands.size(); j++) {
            double[] bands = rawBands.get(j);
            for (int b = 0; b < FFTAnalyzer.FREQUENCY_BAND_COUNT; b++) {
                sums[b] += bands[b];
            }
            frameCount++;
        }

        if (frameCount > 0) {
            for (int b = 0; b < FFTAnalyzer.FREQUENCY_BAND_COUNT; b++) {
                tickArrays[b][tickIndex] = (float) (sums[b] / frameCount);
            }
        }
    }

    private static float[][] detectAllOnsets(float[][] tickArrays) {
        float[][] onsetArrays = new float[FFTAnalyzer.FREQUENCY_BAND_COUNT][];
        for (int i = 0; i < FFTAnalyzer.FREQUENCY_BAND_COUNT; i++) {
            onsetArrays[i] = OnsetDetector.detectOnsets(tickArrays[i]);
        }
        return onsetArrays;
    }

    private static void normalizeAllArrays(float[][] arrays) {
        for (float[] array : arrays) {
            normalizeArray(array);
        }
    }

    private static void logSampleTick(float[][] onsetArrays, int numTicks) {
        int sampleTick = Math.min(numTicks / 2, numTicks - 1);
        if (sampleTick < 0) {
            return;
        }

        RhythmConstants.LOGGER.debug("Tick {} - SubBass: {}, DeepBass: {}, Bass: {}, LowMid: {}, MidLow: {}, Mid: {}",
            sampleTick,
            String.format("%.4f", onsetArrays[0][sampleTick]),
            String.format("%.4f", onsetArrays[1][sampleTick]),
            String.format("%.4f", onsetArrays[2][sampleTick]),
            String.format("%.4f", onsetArrays[3][sampleTick]),
            String.format("%.4f", onsetArrays[4][sampleTick]),
            String.format("%.4f", onsetArrays[5][sampleTick]));
    }


    // ==================== Utility Methods ====================

    /**
     * Normalizes an array to 0-1 range.
     */
    private static void normalizeArray(float[] data) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;

        for (float v : data) {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        float range = max - min;
        if (range > 0) {
            for (int i = 0; i < data.length; i++) {
                data[i] = (data[i] - min) / range;
            }
        }
    }

    /**
     * Logs available audio formats for diagnostics (once per session).
     */
    private static void logAvailableAudioFormats() {
        if (hasLoggedFormats || !RhythmConstants.DEBUG_AUDIO) {
            return;
        }
        hasLoggedFormats = true;

        try {
            logAudioFormatDiagnostics();
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to check audio formats", e);
        }
    }

    private static void logAudioFormatDiagnostics() {
        RhythmConstants.LOGGER.debug("========== AUDIO FORMAT DIAGNOSTICS ==========");

        AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
        RhythmConstants.LOGGER.debug("Available audio file types: {}", types.length);

        boolean hasOgg = false;
        boolean hasMp3 = false;

        for (AudioFileFormat.Type type : types) {
            RhythmConstants.LOGGER.debug("  - {}", type);
            String typeName = type.toString().toLowerCase();
            hasOgg = hasOgg || typeName.contains("ogg") || typeName.contains("vorbis");
            hasMp3 = hasMp3 || typeName.contains("mp3") || typeName.contains("mpeg");
        }

        logFormatSupportStatus(hasOgg, hasMp3);
        RhythmConstants.LOGGER.debug("==============================================");
    }

    private static void logFormatSupportStatus(boolean hasOgg, boolean hasMp3) {
        RhythmConstants.LOGGER.debug("OGG Vorbis support: {}", hasOgg ? "YES" : "NO");
        RhythmConstants.LOGGER.debug("MP3 support: {}", hasMp3 ? "YES" : "NO");

        if (!hasOgg) {
            RhythmConstants.LOGGER.warn("OGG support not detected - vorbisspi may not be loaded");
        }
    }
}