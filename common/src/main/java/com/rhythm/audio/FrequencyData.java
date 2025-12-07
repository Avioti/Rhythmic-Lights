package com.rhythm.audio;

import com.rhythm.util.RhythmConstants;

/**
 * Stores frequency-separated audio analysis data.
 * <p>
 * 12-Band Ultra-Precision System for Maximum Beat Detection:
 * <ul>
 *   <li>SubBass (20-40Hz)</li>
 *   <li>DeepBass (40-80Hz)</li>
 *   <li>Bass (80-150Hz)</li>
 *   <li>LowMid (150-300Hz)</li>
 *   <li>MidLow (300-500Hz)</li>
 *   <li>Mid (500-800Hz)</li>
 *   <li>MidHigh (800-1200Hz)</li>
 *   <li>HighMid (1200-2000Hz)</li>
 *   <li>High (2000-4000Hz)</li>
 *   <li>VeryHigh (4000-8000Hz)</li>
 *   <li>Ultra (8000-12000Hz)</li>
 *   <li>Top (12000-20000Hz)</li>
 * </ul>
 */
public class FrequencyData {

    // ==================== Constants ====================

    private static final int CHANNEL_SUB_BASS = 0;
    private static final int CHANNEL_DEEP_BASS = 1;
    private static final int CHANNEL_BASS = 2;
    private static final int CHANNEL_LOW_MID = 3;
    private static final int CHANNEL_MID_LOW = 4;
    private static final int CHANNEL_MID = 5;
    private static final int CHANNEL_MID_HIGH = 6;
    private static final int CHANNEL_HIGH_MID = 7;
    private static final int CHANNEL_HIGH = 8;
    private static final int CHANNEL_VERY_HIGH = 9;
    private static final int CHANNEL_ULTRA = 10;
    private static final int CHANNEL_TOP = 11;

    private static final int DEBUG_LOG_INTERVAL_TICKS = 20;
    private static final int ZERO_CHECK_INTERVAL_TICKS = 100;
    private static final int ZERO_CHECK_SAMPLE_COUNT = 10;
    private static final float ZERO_THRESHOLD = 0.001f;

    // ==================== Frequency Band Data ====================

    private final float[] subBassMap;
    private final float[] deepBassMap;
    private final float[] bassMap;
    private final float[] lowMidMap;
    private final float[] midLowMap;
    private final float[] midMap;
    private final float[] midHighMap;
    private final float[] highMidMap;
    private final float[] highMap;
    private final float[] veryHighMap;
    private final float[] ultraMap;
    private final float[] topMap;

    // ==================== State ====================

    private final long startTime;
    private final int durationTicks;
    private final boolean isLoading;

    // ==================== Constructors ====================

    /**
     * Constructor for completed frequency data with 12 bands.
     */
    public FrequencyData(float[] subBassMap, float[] deepBassMap, float[] bassMap, float[] lowMidMap,
                         float[] midLowMap, float[] midMap, float[] midHighMap, float[] highMidMap,
                         float[] highMap, float[] veryHighMap, float[] ultraMap, float[] topMap, long startTime) {
        this.subBassMap = subBassMap;
        this.deepBassMap = deepBassMap;
        this.bassMap = bassMap;
        this.lowMidMap = lowMidMap;
        this.midLowMap = midLowMap;
        this.midMap = midMap;
        this.midHighMap = midHighMap;
        this.highMidMap = highMidMap;
        this.highMap = highMap;
        this.veryHighMap = veryHighMap;
        this.ultraMap = ultraMap;
        this.topMap = topMap;
        this.startTime = startTime;
        this.durationTicks = bassMap.length;
        this.isLoading = false;
    }

    /**
     * Constructor for loading state
     */
    public FrequencyData(long startTime) {
        this.subBassMap = new float[0];
        this.deepBassMap = new float[0];
        this.bassMap = new float[0];
        this.lowMidMap = new float[0];
        this.midLowMap = new float[0];
        this.midMap = new float[0];
        this.midHighMap = new float[0];
        this.highMidMap = new float[0];
        this.highMap = new float[0];
        this.veryHighMap = new float[0];
        this.ultraMap = new float[0];
        this.topMap = new float[0];
        this.startTime = startTime;
        this.durationTicks = 0;
        this.isLoading = true;
    }


    /**
     * Get sub-bass intensity at specific tick offset (20-40Hz)
     * @param tickOffset Ticks since song started (not absolute game time)
     * @return Intensity value 0.0-1.0
     */
    public float getSubBassIntensity(long tickOffset) {
        return getIntensity(subBassMap, tickOffset);
    }

    /**
     * Get deep bass intensity at specific tick offset (40-80Hz)
     * @param tickOffset Ticks since song started (not absolute game time)
     * @return Intensity value 0.0-1.0
     */
    public float getDeepBassIntensity(long tickOffset) {
        return getIntensity(deepBassMap, tickOffset);
    }

    /**
     * Get bass intensity at specific tick offset (80-150Hz)
     * @param tickOffset Ticks since song started (not absolute game time)
     * @return Intensity value 0.0-1.0
     */
    public float getBassIntensity(long tickOffset) {
        return getIntensity(bassMap, tickOffset);
    }

    /**
     * Get low-mid intensity at specific tick offset (150-300Hz)
     */
    public float getLowMidIntensity(long tickOffset) {
        return getIntensity(lowMidMap, tickOffset);
    }

    /**
     * Get mid-low intensity at specific tick offset (300-500Hz)
     */
    public float getMidLowIntensity(long tickOffset) {
        return getIntensity(midLowMap, tickOffset);
    }

    /**
     * Get mid-range intensity at specific tick offset (500-800Hz)
     */
    public float getMidIntensity(long tickOffset) {
        return getIntensity(midMap, tickOffset);
    }

    /**
     * Get mid-high intensity at specific tick offset (800-1200Hz)
     */
    public float getMidHighIntensity(long tickOffset) {
        return getIntensity(midHighMap, tickOffset);
    }

    /**
     * Get high-mid intensity at specific tick offset (1200-2000Hz)
     */
    public float getHighMidIntensity(long tickOffset) {
        return getIntensity(highMidMap, tickOffset);
    }

    /**
     * Get high frequency intensity at specific tick offset (2000-4000Hz)
     */
    public float getHighIntensity(long tickOffset) {
        return getIntensity(highMap, tickOffset);
    }

    /**
     * Get very high frequency intensity at specific tick offset (4000-8000Hz)
     */
    public float getVeryHighIntensity(long tickOffset) {
        return getIntensity(veryHighMap, tickOffset);
    }

    /**
     * Get ultra-high frequency intensity at specific tick offset (8000-12000Hz)
     */
    public float getUltraIntensity(long tickOffset) {
        return getIntensity(ultraMap, tickOffset);
    }

    /**
     * Get top frequency intensity at specific tick offset (12000-20000Hz)
     */
    public float getTopIntensity(long tickOffset) {
        return getIntensity(topMap, tickOffset);
    }

    /**
     * @deprecated Legacy method for backward compatibility. Use getLowMidIntensity() instead.
     * Maps to the new LOW_MIDS band (150-300Hz) which covers the old LOW_BASS range
     */
    @Deprecated
    public float getLowBassIntensity(long tickOffset) {
        return getLowMidIntensity(tickOffset);
    }

    /**
     * Gets intensity for specific frequency channel.
     *
     * @param channel 0=SubBass, 1=DeepBass, 2=Bass, 3=LowMid, 4=MidLow, 5=Mid,
     *                6=MidHigh, 7=HighMid, 8=High, 9=VeryHigh, 10=Ultra, 11=Top
     * @return intensity value 0.0-1.0
     */
    public float getChannelIntensity(long tickOffset, int channel) {
        return switch (channel) {
            case CHANNEL_SUB_BASS -> getSubBassIntensity(tickOffset);
            case CHANNEL_DEEP_BASS -> getDeepBassIntensity(tickOffset);
            case CHANNEL_BASS -> getBassIntensity(tickOffset);
            case CHANNEL_LOW_MID -> getLowMidIntensity(tickOffset);
            case CHANNEL_MID_LOW -> getMidLowIntensity(tickOffset);
            case CHANNEL_MID -> getMidIntensity(tickOffset);
            case CHANNEL_MID_HIGH -> getMidHighIntensity(tickOffset);
            case CHANNEL_HIGH_MID -> getHighMidIntensity(tickOffset);
            case CHANNEL_HIGH -> getHighIntensity(tickOffset);
            case CHANNEL_VERY_HIGH -> getVeryHighIntensity(tickOffset);
            case CHANNEL_ULTRA -> getUltraIntensity(tickOffset);
            case CHANNEL_TOP -> getTopIntensity(tickOffset);
            default -> 0.0f;
        };
    }

    // ==================== Intensity Retrieval ====================

    private float getIntensity(float[] map, long tickOffset) {
        if (!isValidForRetrieval(map, tickOffset)) {
            return 0.0f;
        }

        float value = map[(int) tickOffset];
        logDebugInfo(map, tickOffset, value);
        return value;
    }

    private boolean isValidForRetrieval(float[] map, long tickOffset) {
        if (isLoading || map.length == 0) {
            RhythmConstants.debugAudio("getIntensity called while loading or empty map");
            return false;
        }

        if (tickOffset < 0 || tickOffset >= map.length) {
            RhythmConstants.debugAudio("getIntensity tickOffset out of bounds: {} (map length: {})",
                tickOffset, map.length);
            return false;
        }

        return true;
    }

    private void logDebugInfo(float[] map, long tickOffset, float value) {
        if (!RhythmConstants.DEBUG_AUDIO) {
            return;
        }

        if (!shouldLogAtTick(tickOffset)) {
            return;
        }

        RhythmConstants.debugAudio("Retrieved value at tick {}: {}", tickOffset, String.format("%.6f", value));
        checkForZeroData(map, tickOffset);
    }

    private boolean shouldLogAtTick(long tickOffset) {
        return tickOffset % DEBUG_LOG_INTERVAL_TICKS == 0;
    }

    private void checkForZeroData(float[] map, long tickOffset) {
        if (tickOffset <= 0 || tickOffset % ZERO_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        float sum = calculateSampleSum(map, tickOffset);
        if (sum < ZERO_THRESHOLD) {
            RhythmConstants.LOGGER.warn("Data appears to be all zeros around tick {}!", tickOffset);
            RhythmConstants.LOGGER.warn("This suggests corrupt cached FFT data. The cache should auto-clear on next song play.");
        }
    }

    private float calculateSampleSum(float[] map, long tickOffset) {
        int checkCount = Math.min(ZERO_CHECK_SAMPLE_COUNT, map.length - (int) tickOffset);
        float sum = 0;
        for (int i = 0; i < checkCount; i++) {
            sum += Math.abs(map[(int) tickOffset + i]);
        }
        return sum;
    }

    // ==================== Getters ====================

    public boolean isLoading() {
        return isLoading;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public float[] getSubBassMap() {
        return subBassMap;
    }

    public float[] getDeepBassMap() {
        return deepBassMap;
    }

    public float[] getBassMap() {
        return bassMap;
    }

    public float[] getLowMidMap() {
        return lowMidMap;
    }

    public float[] getMidLowMap() {
        return midLowMap;
    }

    public float[] getMidMap() {
        return midMap;
    }

    public float[] getMidHighMap() {
        return midHighMap;
    }

    public float[] getHighMidMap() {
        return highMidMap;
    }

    public float[] getHighMap() {
        return highMap;
    }

    public float[] getVeryHighMap() {
        return veryHighMap;
    }

    public float[] getUltraMap() {
        return ultraMap;
    }

    public float[] getTopMap() {
        return topMap;
    }
}
