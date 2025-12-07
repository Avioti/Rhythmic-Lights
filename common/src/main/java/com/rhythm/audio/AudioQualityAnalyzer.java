package com.rhythm.audio;

import com.rhythm.util.RhythmConstants;

import javax.sound.sampled.AudioFormat;

/**
 * Analyzes audio quality characteristics and provides adaptive processing parameters.
 * <p>
 * Detects:
 * <ul>
 *   <li>Peak and RMS levels for normalization</li>
 *   <li>Dynamic range for compression decisions</li>
 *   <li>Frequency distribution for EQ recommendations</li>
 *   <li>Source quality (bit depth, sample rate) for optimal processing</li>
 * </ul>
 */
public class AudioQualityAnalyzer {

    // ==================== Constants ====================

    private static final float SAMPLE_MAX_16BIT = 32768f;
    private static final float DB_REFERENCE = 1.0f;
    private static final float DB_FLOOR = -96f;
    private static final float CLIPPING_THRESHOLD = 0.99f;
    private static final float LOW_LEVEL_THRESHOLD = 0.1f;
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 44100;

    // ==================== Auto EQ Constants ====================

    private static final float BASS_HEAVY_THRESHOLD = 0.45f;
    private static final float TREBLE_HEAVY_THRESHOLD = 0.40f;
    private static final float MID_HEAVY_THRESHOLD = 0.50f;

    private static final float MAX_AUTO_EQ_ADJUSTMENT_DB = 4.0f;
    private static final int EQ_BAND_COUNT = 10;

    // ==================== Analysis Results ====================

    private float peakLevel = 0f;
    private float rmsLevel = 0f;
    private float dynamicRangeDb = 0f;
    private float crestFactor = 0f;
    private int clippedSamples = 0;
    private int totalSamples = 0;

    private float bassEnergy = 0f;
    private float midEnergy = 0f;
    private float highEnergy = 0f;

    private int sourceBitDepth = 16;
    private float sourceSampleRate = 44100f;
    private int sourceChannels = 2;
    private boolean isLossless = false;

    private boolean isAnalyzed = false;

    // ==================== Public API ====================

    /**
     * Analyzes PCM audio data to determine quality characteristics.
     *
     * @param pcmData the raw PCM audio bytes (16-bit signed, little-endian)
     * @param format  the audio format
     */
    public void analyze(byte[] pcmData, AudioFormat format) {
        if (pcmData == null || pcmData.length == 0) {
            return;
        }

        resetAnalysis();
        extractFormatInfo(format);

        int frameSize = format.getFrameSize();
        int channels = format.getChannels();
        int bytesPerSample = frameSize / channels;

        if (bytesPerSample != 2) {
            RhythmConstants.LOGGER.warn("AudioQualityAnalyzer only supports 16-bit audio, got {} bytes/sample",
                bytesPerSample);
            return;
        }

        analyzeAudioData(pcmData);
        calculateDerivedMetrics();

        isAnalyzed = true;
        logAnalysisResults();
    }

    /**
     * Gets the recommended gain adjustment to normalize the audio.
     *
     * @return gain multiplier (1.0 = no change, >1 = boost, <1 = reduce)
     */
    public float getRecommendedGain() {
        if (!isAnalyzed || peakLevel <= 0) {
            return 1.0f;
        }

        float targetPeak = 0.9f;
        float gain = targetPeak / peakLevel;

        gain = Math.max(0.5f, Math.min(2.0f, gain));

        return gain;
    }

    /**
     * Gets the recommended limiter threshold based on the audio dynamics.
     *
     * @return threshold value (0.0 to 1.0)
     */
    public float getRecommendedLimiterThreshold() {
        if (!isAnalyzed) {
            return 0.95f;
        }

        if (crestFactor > 12f) {
            return 0.85f;
        } else if (crestFactor > 8f) {
            return 0.90f;
        } else {
            return 0.95f;
        }
    }

    /**
     * Checks if the audio is likely clipping or distorted.
     *
     * @return true if significant clipping detected
     */
    public boolean hasClipping() {
        if (!isAnalyzed || totalSamples == 0) {
            return false;
        }
        float clippingRatio = (float) clippedSamples / totalSamples;
        return clippingRatio > 0.001f;
    }

    /**
     * Checks if the audio is very quiet and may need boosting.
     *
     * @return true if audio level is low
     */
    public boolean isLowLevel() {
        return isAnalyzed && rmsLevel < LOW_LEVEL_THRESHOLD;
    }

    /**
     * Gets the frequency balance of the audio.
     *
     * @return array of [bass, mid, high] energy ratios (0.0 to 1.0 each)
     */
    public float[] getFrequencyBalance() {
        if (!isAnalyzed) {
            return new float[]{0.33f, 0.33f, 0.34f};
        }

        float total = bassEnergy + midEnergy + highEnergy;
        if (total <= 0) {
            return new float[]{0.33f, 0.33f, 0.34f};
        }

        return new float[]{
            bassEnergy / total,
            midEnergy / total,
            highEnergy / total
        };
    }

    /**
     * Checks if the audio appears to be bass-heavy.
     *
     * @return true if bass dominates the frequency spectrum
     */
    public boolean isBassHeavy() {
        float[] balance = getFrequencyBalance();
        return balance[0] > BASS_HEAVY_THRESHOLD;
    }

    /**
     * Checks if the audio appears to be treble-heavy.
     *
     * @return true if high frequencies dominate
     */
    public boolean isTrebleHeavy() {
        float[] balance = getFrequencyBalance();
        return balance[2] > TREBLE_HEAVY_THRESHOLD;
    }

    /**
     * Checks if the audio appears to be mid-heavy.
     *
     * @return true if mid frequencies dominate
     */
    public boolean isMidHeavy() {
        float[] balance = getFrequencyBalance();
        return balance[1] > MID_HEAVY_THRESHOLD;
    }

    /**
     * Gets the detected audio profile type based on frequency analysis.
     *
     * @return the audio profile type
     */
    public AudioProfile getAudioProfile() {
        if (!isAnalyzed) {
            return AudioProfile.BALANCED;
        }

        float[] balance = getFrequencyBalance();
        float bass = balance[0];
        float mid = balance[1];
        float high = balance[2];

        if (bass > BASS_HEAVY_THRESHOLD && bass > mid && bass > high) {
            return AudioProfile.BASS_HEAVY;
        } else if (high > TREBLE_HEAVY_THRESHOLD && high > bass) {
            return AudioProfile.TREBLE_HEAVY;
        } else if (mid > MID_HEAVY_THRESHOLD) {
            return AudioProfile.MID_HEAVY;
        } else {
            return AudioProfile.BALANCED;
        }
    }

    /**
     * Gets recommended EQ adjustments to balance the audio.
     * Returns a 10-band EQ array with dB adjustments (-4 to +4).
     * <p>
     * Band frequencies: 32Hz, 64Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
     *
     * @return array of 10 EQ band adjustments in dB
     */
    public float[] getRecommendedEQ() {
        if (!isAnalyzed) {
            return new float[EQ_BAND_COUNT];
        }

        AudioProfile profile = getAudioProfile();
        return calculateEQForProfile(profile);
    }

    /**
     * Audio profile types based on frequency analysis.
     */
    public enum AudioProfile {
        BALANCED("Balanced"),
        BASS_HEAVY("Bass Heavy"),
        TREBLE_HEAVY("Treble Heavy"),
        MID_HEAVY("Mid Heavy");

        private final String displayName;

        AudioProfile(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ==================== Auto EQ Implementation ====================

    private float[] calculateEQForProfile(AudioProfile profile) {
        return switch (profile) {
            case BASS_HEAVY -> createBassHeavyCompensation();
            case TREBLE_HEAVY -> createTrebleHeavyCompensation();
            case MID_HEAVY -> createMidHeavyCompensation();
            case BALANCED -> new float[EQ_BAND_COUNT];
        };
    }

    /**
     * Creates EQ compensation for bass-heavy tracks.
     * Reduces low frequencies, slight boost to highs for clarity.
     */
    private float[] createBassHeavyCompensation() {
        float[] balance = getFrequencyBalance();
        float bassExcess = (balance[0] - 0.33f) * 2;
        float reduction = Math.min(bassExcess * MAX_AUTO_EQ_ADJUSTMENT_DB, MAX_AUTO_EQ_ADJUSTMENT_DB);

        return new float[]{
            -reduction,           // 32Hz  - reduce
            -reduction * 0.8f,    // 64Hz  - reduce
            -reduction * 0.5f,    // 125Hz - slight reduce
            -reduction * 0.2f,    // 250Hz - minimal
            0f,                   // 500Hz - neutral
            0f,                   // 1kHz  - neutral
            reduction * 0.2f,     // 2kHz  - slight boost
            reduction * 0.3f,     // 4kHz  - boost for clarity
            reduction * 0.3f,     // 8kHz  - boost for clarity
            reduction * 0.2f      // 16kHz - slight boost
        };
    }

    /**
     * Creates EQ compensation for treble-heavy tracks.
     * Reduces high frequencies, adds warmth to lows.
     */
    private float[] createTrebleHeavyCompensation() {
        float[] balance = getFrequencyBalance();
        float trebleExcess = (balance[2] - 0.33f) * 2;
        float reduction = Math.min(trebleExcess * MAX_AUTO_EQ_ADJUSTMENT_DB, MAX_AUTO_EQ_ADJUSTMENT_DB);

        return new float[]{
            reduction * 0.3f,     // 32Hz  - add warmth
            reduction * 0.4f,     // 64Hz  - add warmth
            reduction * 0.3f,     // 125Hz - slight boost
            reduction * 0.2f,     // 250Hz - minimal
            0f,                   // 500Hz - neutral
            0f,                   // 1kHz  - neutral
            -reduction * 0.2f,    // 2kHz  - slight reduce
            -reduction * 0.5f,    // 4kHz  - reduce
            -reduction * 0.8f,    // 8kHz  - reduce
            -reduction            // 16kHz - reduce
        };
    }

    /**
     * Creates EQ compensation for mid-heavy tracks.
     * Creates a slight "smile curve" to balance mids.
     */
    private float[] createMidHeavyCompensation() {
        float[] balance = getFrequencyBalance();
        float midExcess = (balance[1] - 0.33f) * 2;
        float adjustment = Math.min(midExcess * MAX_AUTO_EQ_ADJUSTMENT_DB, MAX_AUTO_EQ_ADJUSTMENT_DB);

        return new float[]{
            adjustment * 0.3f,    // 32Hz  - slight boost
            adjustment * 0.4f,    // 64Hz  - boost
            adjustment * 0.3f,    // 125Hz - slight boost
            0f,                   // 250Hz - neutral
            -adjustment * 0.3f,   // 500Hz - reduce
            -adjustment * 0.4f,   // 1kHz  - reduce (vocal harshness)
            -adjustment * 0.3f,   // 2kHz  - reduce
            0f,                   // 4kHz  - neutral
            adjustment * 0.3f,    // 8kHz  - boost
            adjustment * 0.4f     // 16kHz - boost for air
        };
    }

    /**
     * Gets quality information about the source audio.
     *
     * @return formatted string with quality details
     */
    public String getQualityInfo() {
        if (!isAnalyzed) {
            return "Not analyzed";
        }

        return String.format(
            "%dHz / %d-bit / %s / Peak: %.1fdB / RMS: %.1fdB / DR: %.1fdB",
            (int) sourceSampleRate,
            sourceBitDepth,
            isLossless ? "Lossless" : "Lossy",
            linearToDb(peakLevel),
            linearToDb(rmsLevel),
            dynamicRangeDb
        );
    }

    // ==================== Analysis Implementation ====================

    private void resetAnalysis() {
        peakLevel = 0f;
        rmsLevel = 0f;
        dynamicRangeDb = 0f;
        crestFactor = 0f;
        clippedSamples = 0;
        totalSamples = 0;
        bassEnergy = 0f;
        midEnergy = 0f;
        highEnergy = 0f;
        isAnalyzed = false;
    }

    private void extractFormatInfo(AudioFormat format) {
        sourceSampleRate = format.getSampleRate();
        sourceBitDepth = format.getSampleSizeInBits();
        sourceChannels = format.getChannels();

        isLossless = format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED ||
                     format.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED;
    }

    private void analyzeAudioData(byte[] pcmData) {
        float sumSquares = 0f;
        int sampleCount = pcmData.length / 2;
        int analyzeInterval = Math.max(1, sampleCount / MIN_SAMPLES_FOR_ANALYSIS);

        for (int i = 0; i < pcmData.length - 1; i += 2 * analyzeInterval) {
            float sample = extractSample(pcmData, i);
            sumSquares += processSample(sample, i, pcmData.length);
        }

        calculateRmsLevel(sumSquares);
    }

    private float processSample(float sample, int position, int totalLength) {
        float absSample = Math.abs(sample);

        peakLevel = Math.max(peakLevel, absSample);
        totalSamples++;

        if (absSample > CLIPPING_THRESHOLD) {
            clippedSamples++;
        }

        categorizeFrequencyEnergy(sample, position, totalLength);

        return sample * sample;
    }

    private void calculateRmsLevel(float sumSquares) {
        if (totalSamples > 0) {
            rmsLevel = (float) Math.sqrt(sumSquares / totalSamples);
        }
    }

    private float extractSample(byte[] pcmData, int offset) {
        int sample = (pcmData[offset] & 0xFF) | (pcmData[offset + 1] << 8);
        return sample / SAMPLE_MAX_16BIT;
    }

    private void categorizeFrequencyEnergy(float sample, int position, int totalLength) {
        float normalizedPosition = (float) position / totalLength;

        if (normalizedPosition < 0.15f) {
            bassEnergy += Math.abs(sample);
        } else if (normalizedPosition < 0.6f) {
            midEnergy += Math.abs(sample);
        } else {
            highEnergy += Math.abs(sample);
        }
    }

    private void calculateDerivedMetrics() {
        if (rmsLevel > 0) {
            crestFactor = linearToDb(peakLevel) - linearToDb(rmsLevel);
        }

        float peakDb = linearToDb(peakLevel);
        float noiseFloor = linearToDb(rmsLevel * 0.01f);
        dynamicRangeDb = Math.max(0, peakDb - noiseFloor);
    }

    private float linearToDb(float linear) {
        if (linear <= 0) {
            return DB_FLOOR;
        }
        return 20f * (float) Math.log10(linear / DB_REFERENCE);
    }

    private void logAnalysisResults() {
        if (!RhythmConstants.DEBUG_AUDIO) {
            return;
        }

        RhythmConstants.LOGGER.debug("Audio Quality Analysis:");
        RhythmConstants.LOGGER.debug("  Source: {}Hz / {}-bit / {} channels",
            (int) sourceSampleRate, sourceBitDepth, sourceChannels);
        RhythmConstants.LOGGER.debug("  Peak: {} ({}dB)",
            String.format("%.2f", peakLevel), String.format("%.1f", linearToDb(peakLevel)));
        RhythmConstants.LOGGER.debug("  RMS: {} ({}dB)",
            String.format("%.2f", rmsLevel), String.format("%.1f", linearToDb(rmsLevel)));
        RhythmConstants.LOGGER.debug("  Dynamic Range: {}dB", String.format("%.1f", dynamicRangeDb));
        RhythmConstants.LOGGER.debug("  Crest Factor: {}dB", String.format("%.1f", crestFactor));
        RhythmConstants.LOGGER.debug("  Clipped Samples: {} ({}%)",
            clippedSamples, String.format("%.2f", (float) clippedSamples / totalSamples * 100));
        RhythmConstants.LOGGER.debug("  Recommended Gain: {}x", String.format("%.2f", getRecommendedGain()));
    }

    // ==================== Getters ====================

    public float getPeakLevel() {
        return peakLevel;
    }

    public float getRmsLevel() {
        return rmsLevel;
    }

    public float getDynamicRangeDb() {
        return dynamicRangeDb;
    }

    public float getCrestFactor() {
        return crestFactor;
    }

    public int getSourceBitDepth() {
        return sourceBitDepth;
    }

    public float getSourceSampleRate() {
        return sourceSampleRate;
    }

    public boolean isLossless() {
        return isLossless;
    }

    public boolean isAnalyzed() {
        return isAnalyzed;
    }
}

