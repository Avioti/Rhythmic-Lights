package com.rhythm.audio;

import java.util.Arrays;

/**
 * Audio customization settings for RhythmicLights.
 *
 * <p>Provides player-configurable audio enhancements:</p>
 * <ul>
 *   <li>Master volume control</li>
 *   <li>10-band parametric equalizer</li>
 *   <li>Bass boost</li>
 *   <li>Surround sound simulation</li>
 *   <li>Stereo widening</li>
 * </ul>
 *
 * <p>Settings are per-client and can be saved to config.</p>
 */
public class AudioSettings {

    private static final AudioSettings INSTANCE = new AudioSettings();

    // Volume constraints
    private static final float MIN_VOLUME = 0.0f;
    private static final float MAX_VOLUME = 2.0f;
    private static final float DEFAULT_VOLUME = 1.0f;

    // Equalizer constraints
    private static final int EQ_BAND_COUNT = 10;
    private static final float MIN_EQ_DB = -12f;
    private static final float MAX_EQ_DB = 12f;
    private static final float DEFAULT_EQ_GAIN = 1.0f;

    // Effect constraints
    private static final float MIN_EFFECT_LEVEL = 0.0f;
    private static final float MAX_EFFECT_LEVEL = 1.0f;
    private static final float MAX_STEREO_WIDTH = 2.0f;
    private static final float DEFAULT_STEREO_WIDTH = 1.0f;

    // Band frequencies and names
    private static final float[] BAND_FREQUENCIES = {32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f};
    private static final String[] BAND_NAMES = {"32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"};

    private volatile float masterVolume = DEFAULT_VOLUME;
    private final float[] equalizerBands = new float[EQ_BAND_COUNT];
    private volatile float bassBoost = MIN_EFFECT_LEVEL;
    private volatile float surroundLevel = MIN_EFFECT_LEVEL;
    private volatile float stereoWidth = DEFAULT_STEREO_WIDTH;
    private volatile boolean isEnhancementsEnabled = true;
    private volatile boolean isAutoplayEnabled = true;

    /**
     * Audio presets for quick configuration.
     */
    public enum Preset {
        FLAT("Flat", new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0, 0, 1),
        BASS_HEAVY("Bass Heavy", new float[]{6, 5, 4, 2, 0, 0, 0, 0, 0, 0}, 0.5f, 0, 1),
        TREBLE_BOOST("Treble Boost", new float[]{0, 0, 0, 0, 0, 1, 2, 3, 4, 5}, 0, 0, 1),
        VOCAL_CLARITY("Vocal Clarity", new float[]{-2, -1, 0, 2, 4, 4, 3, 2, 1, 0}, 0, 0, 1),
        SURROUND("Surround", new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0, 0.7f, 1.3f),
        CLUB("Club", new float[]{4, 3, 2, 1, 0, 0, 1, 2, 3, 4}, 0.3f, 0.4f, 1.2f),
        CONCERT("Concert", new float[]{2, 3, 3, 2, 1, 1, 2, 3, 3, 2}, 0.2f, 0.5f, 1.4f);

        private final String displayName;
        private final float[] eqBands;
        private final float bassBoost;
        private final float surround;
        private final float stereoWidth;

        Preset(String displayName, float[] eqBands, float bassBoost, float surround, float stereoWidth) {
            this.displayName = displayName;
            this.eqBands = eqBands;
            this.bassBoost = bassBoost;
            this.surround = surround;
            this.stereoWidth = stereoWidth;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private AudioSettings() {
        Arrays.fill(equalizerBands, DEFAULT_EQ_GAIN);
    }

    /**
     * Gets the singleton instance of audio settings.
     *
     * @return the audio settings instance
     */
    public static AudioSettings getInstance() {
        return INSTANCE;
    }

    // ==================== MASTER VOLUME ====================

    /**
     * Gets the master volume level.
     *
     * @return volume level from 0.0 to 2.0
     */
    public float getMasterVolume() {
        return masterVolume;
    }

    /**
     * Sets the master volume level.
     *
     * @param volume volume level (clamped to 0.0-2.0)
     */
    public void setMasterVolume(float volume) {
        this.masterVolume = clamp(volume, MIN_VOLUME, MAX_VOLUME);
    }

    /**
     * Syncs master volume with Minecraft's records volume setting.
     *
     * @param minecraftVolume the volume from Minecraft's sound options (0.0 to 1.0)
     */
    public void syncWithMinecraftVolume(float minecraftVolume) {
        this.masterVolume = clamp(minecraftVolume, MIN_VOLUME, MAX_VOLUME);
    }

    // ==================== EQUALIZER ====================

    /**
     * Gets equalizer band gain as a linear multiplier.
     *
     * @param band band index (0-9)
     * @return gain multiplier (0.25 to 4.0)
     */
    public float getEqualizerBand(int band) {
        if (!isValidBandIndex(band)) return DEFAULT_EQ_GAIN;
        return equalizerBands[band];
    }

    /**
     * Sets equalizer band gain in decibels.
     *
     * @param band band index (0-9)
     * @param db   gain in dB (clamped to -12 to +12)
     */
    public void setEqualizerBandDb(int band, float db) {
        if (!isValidBandIndex(band)) return;
        db = clamp(db, MIN_EQ_DB, MAX_EQ_DB);
        equalizerBands[band] = (float) Math.pow(10, db / 20.0);
    }

    /**
     * Gets equalizer band gain in decibels.
     *
     * @param band band index (0-9)
     * @return gain in dB
     */
    public float getEqualizerBandDb(int band) {
        if (!isValidBandIndex(band)) return 0;
        return (float) (20 * Math.log10(equalizerBands[band]));
    }

    /**
     * Gets the display name for an equalizer band.
     *
     * @param band band index (0-9)
     * @return band name (e.g., "1kHz")
     */
    public static String getBandName(int band) {
        if (band < 0 || band >= BAND_NAMES.length) return "?";
        return BAND_NAMES[band];
    }

    /**
     * Gets the number of equalizer bands.
     *
     * @return band count (10)
     */
    public static int getBandCount() {
        return EQ_BAND_COUNT;
    }

    /**
     * Gets the center frequency for a band.
     *
     * @param band band index (0-9)
     * @return frequency in Hz
     */
    public static float getBandFrequency(int band) {
        if (band < 0 || band >= BAND_FREQUENCIES.length) return 1000f;
        return BAND_FREQUENCIES[band];
    }

    private boolean isValidBandIndex(int band) {
        return band >= 0 && band < equalizerBands.length;
    }

    // ==================== BASS BOOST ====================

    /**
     * Gets the bass boost level.
     *
     * @return bass boost level (0.0 to 1.0)
     */
    public float getBassBoost() {
        return bassBoost;
    }

    /**
     * Sets the bass boost level.
     *
     * @param boost level (clamped to 0.0-1.0)
     */
    public void setBassBoost(float boost) {
        this.bassBoost = clamp(boost, MIN_EFFECT_LEVEL, MAX_EFFECT_LEVEL);
    }

    // ==================== SURROUND ====================

    /**
     * Gets the surround sound level.
     *
     * @return surround level (0.0 to 1.0)
     */
    public float getSurroundLevel() {
        return surroundLevel;
    }

    /**
     * Sets the surround sound level.
     *
     * @param level surround level (clamped to 0.0-1.0)
     */
    public void setSurroundLevel(float level) {
        this.surroundLevel = clamp(level, MIN_EFFECT_LEVEL, MAX_EFFECT_LEVEL);
    }

    // ==================== STEREO WIDTH ====================

    /**
     * Gets the stereo width.
     *
     * @return stereo width (0.0=mono, 1.0=normal, 2.0=extra wide)
     */
    public float getStereoWidth() {
        return stereoWidth;
    }

    /**
     * Sets the stereo width.
     *
     * @param width stereo width (clamped to 0.0-2.0)
     */
    public void setStereoWidth(float width) {
        this.stereoWidth = clamp(width, MIN_VOLUME, MAX_STEREO_WIDTH);
    }

    // ==================== ENHANCEMENTS TOGGLE ====================

    /**
     * Checks if audio enhancements are enabled.
     *
     * @return true if enhancements are enabled
     */
    public boolean isEnhancementsEnabled() {
        return isEnhancementsEnabled;
    }

    /**
     * Sets whether audio enhancements are enabled.
     *
     * @param enabled true to enable enhancements
     */
    public void setEnhancementsEnabled(boolean enabled) {
        this.isEnhancementsEnabled = enabled;
    }

    // ==================== AUTOPLAY ====================

    /**
     * Checks if autoplay is enabled.
     *
     * @return true if music auto-starts after loading
     */
    public boolean isAutoplayEnabled() {
        return isAutoplayEnabled;
    }

    /**
     * Sets whether autoplay is enabled.
     *
     * @param enabled true to auto-start music after loading
     */
    public void setAutoplayEnabled(boolean enabled) {
        this.isAutoplayEnabled = enabled;
    }

    // ==================== SPATIAL AUDIO ====================

    // Spatial audio configuration (synced with SeekableAudioPlayer)
    private float maxAudioDistance = 24.0f;
    private float referenceDistance = 4.0f;
    private float rolloffFactor = 1.0f;
    private boolean isSpatialAudioEnabled = true;

    /**
     * Gets the maximum distance at which jukebox audio can be heard.
     *
     * @return maximum distance in blocks
     */
    public float getMaxAudioDistance() {
        return maxAudioDistance;
    }

    /**
     * Sets the maximum audible distance for jukeboxes.
     *
     * @param distance distance in blocks (4-64)
     */
    public void setMaxAudioDistance(float distance) {
        this.maxAudioDistance = clamp(distance, 4.0f, 64.0f);
        syncSpatialSettings();
    }

    /**
     * Gets the reference distance (where volume is 100%).
     *
     * @return reference distance in blocks
     */
    public float getReferenceDistance() {
        return referenceDistance;
    }

    /**
     * Sets the reference distance.
     *
     * @param distance distance in blocks (1-16)
     */
    public void setReferenceDistance(float distance) {
        this.referenceDistance = clamp(distance, 1.0f, 16.0f);
        syncSpatialSettings();
    }

    /**
     * Gets the rolloff factor for distance attenuation.
     *
     * @return rolloff factor
     */
    public float getRolloffFactor() {
        return rolloffFactor;
    }

    /**
     * Sets the rolloff factor. Higher = faster volume drop-off.
     *
     * @param factor rolloff factor (0.1-3.0)
     */
    public void setRolloffFactor(float factor) {
        this.rolloffFactor = clamp(factor, 0.1f, 3.0f);
        syncSpatialSettings();
    }

    /**
     * Checks if spatial audio is enabled.
     *
     * @return true if distance-based volume is active
     */
    public boolean isSpatialAudioEnabled() {
        return isSpatialAudioEnabled;
    }

    /**
     * Sets whether spatial audio is enabled.
     *
     * @param enabled true for distance-based volume
     */
    public void setSpatialAudioEnabled(boolean enabled) {
        this.isSpatialAudioEnabled = enabled;
    }

    private void syncSpatialSettings() {
        SeekableAudioPlayer player = SeekableAudioPlayer.getInstance();
        player.setMaxDistance(maxAudioDistance);
        player.setReferenceDistance(referenceDistance);
        player.setRolloffFactor(rolloffFactor);
    }

    // ==================== PRESETS ====================

    /**
     * Applies a preset configuration.
     *
     * @param preset the preset to apply
     */
    public void applyPreset(Preset preset) {
        for (int i = 0; i < preset.eqBands.length && i < equalizerBands.length; i++) {
            setEqualizerBandDb(i, preset.eqBands[i]);
        }
        setBassBoost(preset.bassBoost);
        setSurroundLevel(preset.surround);
        setStereoWidth(preset.stereoWidth);
    }

    /**
     * Resets all settings to defaults.
     */
    public void resetToDefaults() {
        masterVolume = DEFAULT_VOLUME;
        Arrays.fill(equalizerBands, DEFAULT_EQ_GAIN);
        bassBoost = MIN_EFFECT_LEVEL;
        surroundLevel = MIN_EFFECT_LEVEL;
        stereoWidth = DEFAULT_STEREO_WIDTH;
        isEnhancementsEnabled = true;
        isAutoplayEnabled = true;

        // Reset spatial audio settings
        maxAudioDistance = 24.0f;
        referenceDistance = 4.0f;
        rolloffFactor = 1.0f;
        isSpatialAudioEnabled = true;
        syncSpatialSettings();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}



