package com.rhythm.audio;

import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.jtransforms.fft.DoubleFFT_1D;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-computes frequency analysis (FFT) data for audio files.
 * Supports both Minecraft sound events and URL-based audio (YouTube, etc).
 * Caches results for instant playback on subsequent uses.
 */
public class AudioPreComputer {

    // ==================== FFT Configuration ====================

    /** FFT window size - larger = better frequency resolution, worse time resolution */
    private static final int FFT_SIZE = 2048;

    /** Hop size between FFT frames (~2.5ms at 44.1kHz for maximum temporal precision) */
    private static final int HOP_SIZE = 110;

    /** Number of frequency bands for analysis */
    private static final int FREQUENCY_BAND_COUNT = 12;

    // ==================== Frequency Band Cutoffs (Hz) ====================

    private static final double FREQ_SUB_BASS = 40;      // Sub-kick, floor toms
    private static final double FREQ_DEEP_BASS = 80;     // Kick drums, bass fundamentals
    private static final double FREQ_BASS = 150;         // Bass guitar, low toms
    private static final double FREQ_LOW_MID = 300;      // Guitar low strings, snare body
    private static final double FREQ_MID_LOW = 500;      // Rhythm guitar, toms
    private static final double FREQ_MID = 800;          // Lead guitar, vocals, snare snap
    private static final double FREQ_MID_HIGH = 1200;    // Guitar harmonics, hi-hats
    private static final double FREQ_HIGH_MID = 2000;    // Cymbals, guitar brightness
    private static final double FREQ_HIGH = 4000;        // Hi-hats, cymbals, guitar attack
    private static final double FREQ_VERY_HIGH = 8000;   // Cymbal shimmer, presence
    private static final double FREQ_ULTRA = 12000;      // Air, brilliance, pick attack
    // Above FREQ_ULTRA = Top (12-20kHz) - Ultra-high harmonics, sparkle

    // ==================== Onset Detection Parameters ====================

    private static final int[] ONSET_WINDOW_SIZES = {1, 2, 3, 5, 8};
    private static final float[] ONSET_WINDOW_WEIGHTS = {0.35f, 0.30f, 0.20f, 0.10f, 0.05f};
    private static final float ONSET_ADAPTIVE_THRESHOLD_MIN = 0.2f;
    private static final float ONSET_DYNAMIC_RANGE_FACTOR = 0.02f;
    private static final float ONSET_STD_DEV_FACTOR = 0.15f;
    private static final float ONSET_RELATIVE_THRESHOLD = 0.03f;
    private static final float ONSET_PEAK_RELATIVE_THRESHOLD = 0.02f;
    private static final float ONSET_PEAK_BONUS = 1.2f;
    private static final float ONSET_FLUX_WEIGHT = 0.25f;
    private static final float ONSET_ACCELERATION_WEIGHT = 0.15f;
    private static final float ONSET_PHASE_WEIGHT = 0.1f;

    // ==================== Cache Validation ====================

    private static final int CACHE_VALIDATION_SAMPLE_COUNT = 100;
    private static final float CACHE_VALIDATION_MIN_SUM = 0.001f;
    private static final double DB_EPSILON = 1e-10;
    private static final float DURATION_MISMATCH_THRESHOLD = 0.9f;

    // ==================== Audio Processing ====================

    private static final int BITS_PER_BYTE = 8;
    private static final double SAMPLE_NORMALIZATION_FACTOR = 32768.0;
    private static final int TICKS_PER_SECOND = 20;
    private static final float SILENCE_THRESHOLD_DB = -80f;

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
     * @param jukeboxPos  position of the jukebox
     * @return CompletableFuture containing the frequency analysis data
     */
    public static CompletableFuture<FrequencyData> preComputeFrequenciesFromUrl(
            String urlString, long startTime, BlockPos jukeboxPos) {
        return CompletableFuture.supplyAsync(() -> {
            FrequencyData cachedData = getValidCachedData(urlString);
            if (cachedData != null) {
                return createWithNewStartTime(cachedData, startTime);
            }

            RhythmConstants.LOGGER.info("Starting FFT analysis from URL: {}", urlString);

            try {
                URL url = new URL(urlString);
                try (InputStream rawUrlStream = url.openStream()) {
                    if (RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Downloading audio from URL...");
                    }

                    byte[] audioData = IOUtils.toByteArray(rawUrlStream);
                    if (RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Downloaded {} bytes", audioData.length);
                    }

                    ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
                    byteStream.mark(audioData.length);

                    String pcmCacheKey = AudioPcmCache.getCacheKey(null, urlString);
                    FrequencyData result = processAudioStream(byteStream, startTime, pcmCacheKey);

                    cacheIfValid(urlString, result);
                    return result;
                }
            } catch (Exception e) {
                RhythmConstants.LOGGER.error("Error processing audio from URL: {}", urlString, e);
                return null;
            }
        });
    }

    /**
     * Analyzes audio from a file URL using a custom cache key.
     * Useful for cached files where we want to cache by original URL, not file path.
     *
     * @param fileUrlString the file:// URL to the actual audio file
     * @param cacheKey      the key to use for caching (e.g., original YouTube URL)
     * @param startTime     game time when song started
     * @param jukeboxPos    position of the jukebox
     * @return CompletableFuture containing the frequency analysis data
     */
    public static CompletableFuture<FrequencyData> preComputeFrequenciesFromUrlWithCacheKey(
            String fileUrlString, String cacheKey, long startTime, BlockPos jukeboxPos) {
        return CompletableFuture.supplyAsync(() -> {
            FrequencyData cachedData = getValidCachedData(cacheKey);
            if (cachedData != null) {
                return createWithNewStartTime(cachedData, startTime);
            }

            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Starting FFT analysis from file: {}", fileUrlString);
                RhythmConstants.LOGGER.debug("Will cache with key: {}", cacheKey);
            }

            try {
                InputStream rawStream = openFileStream(fileUrlString);
                if (rawStream == null) {
                    return null;
                }

                try (InputStream inputStream = rawStream) {
                    byte[] audioData = IOUtils.toByteArray(inputStream);
                    if (RhythmConstants.DEBUG_AUDIO) {
                        RhythmConstants.LOGGER.debug("Read {} bytes", audioData.length);
                    }

                    ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
                    byteStream.mark(audioData.length);

                    String pcmCacheKey = AudioPcmCache.getCacheKey(null, cacheKey);
                    FrequencyData result = processAudioStream(byteStream, startTime, pcmCacheKey);

                    cacheIfValid(cacheKey, result);
                    return result;
                }
            } catch (Exception e) {
                RhythmConstants.LOGGER.error("Error processing audio from file: {}", fileUrlString, e);
                return null;
            }
        });
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

            double[] audioSamples = convertBytesToMonoSamples(pcmBytes, decodedFormat);
            int expectedTicks = calculateExpectedTicks(audioSamples.length, decodedFormat.getSampleRate());

            logSampleStatistics(audioSamples, decodedFormat.getSampleRate());

            List<double[]> rawBands = calculateRawBands(audioSamples, decodedFormat.getSampleRate());
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("FFT analysis produced {} frequency frames", rawBands.size());
            }

            FrequencyData result = convertToTickData(rawBands, decodedFormat.getSampleRate(), startTime);
            validateDuration(result, expectedTicks);

            return result;
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

    // ==================== FFT Analysis ====================

    /**
     * Performs FFT analysis on audio samples and extracts power in each frequency band.
     */
    private static List<double[]> calculateRawBands(double[] audioSamples, float sampleRate) {
        List<double[]> allBands = new ArrayList<>();
        int totalSamples = audioSamples.length;

        double[] window = createHanningWindow(FFT_SIZE);
        DoubleFFT_1D fft = new DoubleFFT_1D(FFT_SIZE);

        for (int i = 0; i + FFT_SIZE <= totalSamples; i += HOP_SIZE) {
            double[] fftInput = applyWindowToSamples(audioSamples, i, window);
            fft.realForward(fftInput);

            double[] bandPowers = calculateBandPowers(fftInput, sampleRate);
            double[] bandDecibels = convertPowersToDecibels(bandPowers);
            allBands.add(bandDecibels);
        }
        return allBands;
    }

    /**
     * Creates a Hanning window of the specified size.
     */
    private static double[] createHanningWindow(int size) {
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (size - 1)));
        }
        return window;
    }

    /**
     * Applies a window function to a section of audio samples.
     */
    private static double[] applyWindowToSamples(double[] samples, int startIndex, double[] window) {
        double[] fftInput = new double[FFT_SIZE * 2];
        for (int j = 0; j < FFT_SIZE; j++) {
            fftInput[j] = samples[startIndex + j] * window[j];
        }
        return fftInput;
    }

    /**
     * Calculates power in each frequency band from FFT output.
     */
    private static double[] calculateBandPowers(double[] fftOutput, float sampleRate) {
        double[] powers = new double[FREQUENCY_BAND_COUNT];

        for (int k = 0; k < FFT_SIZE / 2; k++) {
            double real = fftOutput[2 * k];
            double imag = fftOutput[2 * k + 1];
            double power = real * real + imag * imag;
            double frequency = k * sampleRate / FFT_SIZE;

            int bandIndex = getFrequencyBandIndex(frequency);
            powers[bandIndex] += power;
        }
        return powers;
    }

    /**
     * Determines which frequency band a given frequency belongs to.
     */
    private static int getFrequencyBandIndex(double frequency) {
        if (frequency <= FREQ_SUB_BASS) return 0;
        if (frequency <= FREQ_DEEP_BASS) return 1;
        if (frequency <= FREQ_BASS) return 2;
        if (frequency <= FREQ_LOW_MID) return 3;
        if (frequency <= FREQ_MID_LOW) return 4;
        if (frequency <= FREQ_MID) return 5;
        if (frequency <= FREQ_MID_HIGH) return 6;
        if (frequency <= FREQ_HIGH_MID) return 7;
        if (frequency <= FREQ_HIGH) return 8;
        if (frequency <= FREQ_VERY_HIGH) return 9;
        if (frequency <= FREQ_ULTRA) return 10;
        return 11; // Top band
    }

    /**
     * Converts power values to decibels.
     */
    private static double[] convertPowersToDecibels(double[] powers) {
        double[] decibels = new double[powers.length];
        for (int i = 0; i < powers.length; i++) {
            decibels[i] = 10 * Math.log10(powers[i] + DB_EPSILON);
        }
        return decibels;
    }

    // ==================== Tick Data Conversion ====================

    /**
     * Converts raw FFT band data to per-tick frequency data with onset detection.
     */
    private static FrequencyData convertToTickData(List<double[]> rawBands, float sampleRate, long startTime) {
        if (rawBands.isEmpty()) {
            return new FrequencyData(startTime);
        }

        double framesPerSecond = sampleRate / HOP_SIZE;
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
        float[][] arrays = new float[FREQUENCY_BAND_COUNT][];
        for (int i = 0; i < FREQUENCY_BAND_COUNT; i++) {
            arrays[i] = new float[size];
        }
        return arrays;
    }

    private static void averageBandsToTicks(List<double[]> rawBands, float[][] tickArrays, double framesPerTick) {
        int numTicks = tickArrays[0].length;

        for (int i = 0; i < numTicks; i++) {
            int startFrame = (int) (i * framesPerTick);
            int endFrame = (int) ((i + 1) * framesPerTick);

            double[] sums = new double[FREQUENCY_BAND_COUNT];
            int frameCount = 0;

            for (int j = startFrame; j < endFrame && j < rawBands.size(); j++) {
                double[] bands = rawBands.get(j);
                for (int b = 0; b < FREQUENCY_BAND_COUNT; b++) {
                    sums[b] += bands[b];
                }
                frameCount++;
            }

            if (frameCount > 0) {
                for (int b = 0; b < FREQUENCY_BAND_COUNT; b++) {
                    tickArrays[b][i] = (float) (sums[b] / frameCount);
                }
            }
        }
    }

    private static float[][] detectAllOnsets(float[][] tickArrays) {
        float[][] onsetArrays = new float[FREQUENCY_BAND_COUNT][];
        for (int i = 0; i < FREQUENCY_BAND_COUNT; i++) {
            onsetArrays[i] = detectOnsetsMultiScale(tickArrays[i]);
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

    // ==================== Onset Detection ====================

    /**
     * Multi-scale onset detection for maximum beat capture.
     * Uses multiple window sizes to detect both fast beats (snares, hi-hats)
     * and slow beats (kicks, bass drops).
     *
     * @param energyData energy values per tick
     * @return onset strength per tick (1.0 = strong beat, 0.0 = no beat)
     */
    private static float[] detectOnsetsMultiScale(float[] energyData) {
        int length = energyData.length;
        float[] onsets = new float[length];

        EnergyStatistics stats = calculateEnergyStatistics(energyData);

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Onset detection - Mean: {} dB, Range: {} dB, StdDev: {} dB",
                String.format("%.2f", stats.mean),
                String.format("%.2f", stats.range),
                String.format("%.2f", stats.stdDev));
        }

        applyMultiScaleDetection(energyData, onsets, stats);
        applySpectralFlux(energyData, onsets);
        applyPhaseCoherence(energyData, onsets);

        return onsets;
    }

    private record EnergyStatistics(float mean, float min, float max, float range, float stdDev) {}

    private static EnergyStatistics calculateEnergyStatistics(float[] energyData) {
        float mean = 0;
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        for (float energy : energyData) {
            mean += energy;
            if (energy > max) max = energy;
            if (energy < min) min = energy;
        }
        mean /= energyData.length;

        float variance = 0;
        for (float energy : energyData) {
            variance += (energy - mean) * (energy - mean);
        }
        float stdDev = (float) Math.sqrt(variance / energyData.length);

        return new EnergyStatistics(mean, min, max, max - min, stdDev);
    }

    private static void applyMultiScaleDetection(float[] energyData, float[] onsets, EnergyStatistics stats) {
        int length = energyData.length;

        for (int scale = 0; scale < ONSET_WINDOW_SIZES.length; scale++) {
            int windowSize = ONSET_WINDOW_SIZES[scale];
            float weight = ONSET_WINDOW_WEIGHTS[scale];

            for (int i = 0; i < length; i++) {
                float localAvg = calculateLocalAverage(energyData, i, windowSize, stats.mean);
                float energyIncrease = energyData[i] - localAvg;
                float relativeIncrease = calculateRelativeIncrease(energyData[i], localAvg);
                boolean isPeak = isLocalPeak(energyData, i);

                float adaptiveThreshold = calculateAdaptiveThreshold(stats);

                if (isOnsetDetected(energyIncrease, relativeIncrease, isPeak, adaptiveThreshold)) {
                    float peakBonus = isPeak ? ONSET_PEAK_BONUS : 1.0f;
                    float onsetStrength = calculateOnsetStrength(energyIncrease, relativeIncrease, weight, peakBonus);
                    onsets[i] += onsetStrength;
                }
            }
        }
    }

    private static float calculateLocalAverage(float[] data, int index, int windowSize, float globalMean) {
        float sum = 0;
        int count = 0;
        for (int j = Math.max(0, index - windowSize); j < index; j++) {
            sum += data[j];
            count++;
        }
        return count > 0 ? sum / count : globalMean;
    }

    private static float calculateRelativeIncrease(float current, float localAvg) {
        return localAvg > SILENCE_THRESHOLD_DB ? (current - localAvg) / (Math.abs(localAvg) + 1) : 0;
    }

    private static boolean isLocalPeak(float[] data, int index) {
        if (index > 0 && data[index] <= data[index - 1]) return false;
        if (index < data.length - 1 && data[index] < data[index + 1]) return false;
        return true;
    }

    private static float calculateAdaptiveThreshold(EnergyStatistics stats) {
        return Math.max(
            ONSET_ADAPTIVE_THRESHOLD_MIN,
            Math.min(stats.range * ONSET_DYNAMIC_RANGE_FACTOR, stats.stdDev * ONSET_STD_DEV_FACTOR)
        );
    }

    private static boolean isOnsetDetected(float energyIncrease, float relativeIncrease,
            boolean isPeak, float adaptiveThreshold) {
        return energyIncrease > adaptiveThreshold
            || relativeIncrease > ONSET_RELATIVE_THRESHOLD
            || (isPeak && relativeIncrease > ONSET_PEAK_RELATIVE_THRESHOLD);
    }

    private static float calculateOnsetStrength(float energyIncrease, float relativeIncrease,
            float weight, float peakBonus) {
        return Math.max(energyIncrease, relativeIncrease * 15f) * weight * peakBonus;
    }

    /**
     * Applies spectral flux detection to catch timbral changes.
     */
    private static void applySpectralFlux(float[] energyData, float[] onsets) {
        for (int i = 1; i < energyData.length; i++) {
            float flux = Math.abs(energyData[i] - energyData[i - 1]);

            float acceleration = 0;
            if (i > 1) {
                float prevFlux = Math.abs(energyData[i - 1] - energyData[i - 2]);
                acceleration = Math.abs(flux - prevFlux);
            }

            onsets[i] += flux * ONSET_FLUX_WEIGHT + acceleration * ONSET_ACCELERATION_WEIGHT;
        }
    }

    /**
     * Applies phase coherence detection for sustained tones.
     */
    private static void applyPhaseCoherence(float[] energyData, float[] onsets) {
        for (int i = 2; i < energyData.length - 2; i++) {
            float leftSlope = energyData[i] - energyData[i - 1];
            float rightSlope = energyData[i + 1] - energyData[i];

            if (leftSlope * rightSlope < 0 && Math.abs(leftSlope) > 0.5f) {
                onsets[i] += Math.abs(leftSlope) * ONSET_PHASE_WEIGHT;
            }
        }
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
        if (hasLoggedFormats) {
            return;
        }
        hasLoggedFormats = true;

        if (!RhythmConstants.DEBUG_AUDIO) {
            return;
        }

        try {
            RhythmConstants.LOGGER.debug("========== AUDIO FORMAT DIAGNOSTICS ==========");

            AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
            RhythmConstants.LOGGER.debug("Available audio file types: {}", types.length);

            boolean hasOgg = false;
            boolean hasMp3 = false;

            for (AudioFileFormat.Type type : types) {
                RhythmConstants.LOGGER.debug("  - {}", type);
                String typeName = type.toString().toLowerCase();
                if (typeName.contains("ogg") || typeName.contains("vorbis")) {
                    hasOgg = true;
                }
                if (typeName.contains("mp3") || typeName.contains("mpeg")) {
                    hasMp3 = true;
                }
            }

            RhythmConstants.LOGGER.debug("OGG Vorbis support: {}", hasOgg ? "YES" : "NO");
            RhythmConstants.LOGGER.debug("MP3 support: {}", hasMp3 ? "YES" : "NO");

            if (!hasOgg) {
                RhythmConstants.LOGGER.warn("OGG support not detected - vorbisspi may not be loaded");
            }

            RhythmConstants.LOGGER.debug("==============================================");
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to check audio formats", e);
        }
    }
}