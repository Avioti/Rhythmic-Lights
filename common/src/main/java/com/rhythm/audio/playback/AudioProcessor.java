package com.rhythm.audio.playback;

import com.rhythm.audio.AudioSettings;

/**
 * Real-time audio processor for applying effects to PCM audio data.
 *
 * <p>Applies the following effects:</p>
 * <ul>
 *   <li>Auto-normalization based on audio analysis</li>
 *   <li>Look-ahead limiter for transparent peak control</li>
 *   <li>10-band parametric equalizer</li>
 *   <li>Bass boost via low-shelf filter</li>
 *   <li>Stereo width adjustment</li>
 *   <li>Surround sound simulation (Haas effect)</li>
 *   <li>Master volume with soft clipping</li>
 * </ul>
 *
 * <p>Designed for efficient real-time processing of audio buffers.</p>
 */
public class AudioProcessor {

    // ==================== Audio Processing Constants ====================

    private static final int EQ_BAND_COUNT = 10;
    private static final int STEREO_CHANNELS = 2;
    private static final float DEFAULT_SAMPLE_RATE = 48000f;
    private static final float DEFAULT_Q_FACTOR = 1.0f;
    private static final float BASS_BOOST_FREQUENCY = 100f;
    private static final float BASS_BOOST_Q = 0.7f;
    private static final float MAX_BASS_BOOST_DB = 12f;

    // ==================== Limiter Constants ====================

    private static final float LIMITER_THRESHOLD = 0.95f;
    private static final float LIMITER_ATTACK_MS = 0.1f;
    private static final float LIMITER_RELEASE_MS = 100f;

    // ==================== Soft Clipping Constants ====================

    private static final float SOFT_CLIP_THRESHOLD = 0.95f;
    private static final float SOFT_CLIP_KNEE = 0.05f;

    // ==================== Sample Conversion Constants ====================

    private static final float SAMPLE_MAX_VALUE = 32768f;
    private static final int SAMPLE_CLAMP_MAX = 32767;
    private static final int SAMPLE_CLAMP_MIN = -32768;

    // ==================== Surround Delay Constants ====================

    private static final int SURROUND_DELAY_SAMPLES = 441;
    private static final float SURROUND_CROSS_FEED_AMOUNT = 0.3f;
    private static final float SURROUND_DELAY_RATIO = 0.5f;

    // ==================== Filter State ====================

    private final BiquadFilter[][] eqFilters = new BiquadFilter[EQ_BAND_COUNT][STEREO_CHANNELS];
    private final BiquadFilter[] bassBoostFilters = new BiquadFilter[STEREO_CHANNELS];

    private float sampleRate = DEFAULT_SAMPLE_RATE;
    private float[] surroundDelayBuffer;
    private int surroundDelayIndex = 0;

    // ==================== Limiter State ====================

    private float limiterGain = 1.0f;
    private float limiterAttackCoeff = 0f;
    private float limiterReleaseCoeff = 0f;

    // ==================== Auto-Normalization State ====================

    private float autoGain = 1.0f;
    private float targetAutoGain = 1.0f;
    private static final float AUTO_GAIN_SMOOTHING = 0.001f;

    /**
     * Creates a new audio processor with default settings.
     */
    public AudioProcessor() {
        initializeFilters();
    }

    /**
     * Sets the sample rate and reinitializes filters if changed.
     *
     * @param sampleRate the sample rate in Hz
     */
    public void setSampleRate(float sampleRate) {
        if (this.sampleRate != sampleRate) {
            this.sampleRate = sampleRate;
            initializeFilters();
        }
    }

    private void initializeFilters() {
        initializeEqualizerFilters();
        initializeBassBoostFilters();
        initializeSurroundBuffer();
        initializeLimiter();
    }

    private void initializeEqualizerFilters() {
        for (int band = 0; band < EQ_BAND_COUNT; band++) {
            float freq = AudioSettings.getBandFrequency(band);
            for (int ch = 0; ch < STEREO_CHANNELS; ch++) {
                eqFilters[band][ch] = new BiquadFilter();
                eqFilters[band][ch].setPeakingEQ(sampleRate, freq, DEFAULT_Q_FACTOR, 0);
            }
        }
    }

    private void initializeBassBoostFilters() {
        for (int ch = 0; ch < STEREO_CHANNELS; ch++) {
            bassBoostFilters[ch] = new BiquadFilter();
            bassBoostFilters[ch].setLowShelf(sampleRate, BASS_BOOST_FREQUENCY, BASS_BOOST_Q, 0);
        }
    }

    private void initializeSurroundBuffer() {
        surroundDelayBuffer = new float[SURROUND_DELAY_SAMPLES * STEREO_CHANNELS];
        surroundDelayIndex = 0;
    }

    private void initializeLimiter() {
        float attackSamples = (LIMITER_ATTACK_MS / 1000f) * sampleRate;
        float releaseSamples = (LIMITER_RELEASE_MS / 1000f) * sampleRate;
        limiterAttackCoeff = (float) Math.exp(-1.0 / attackSamples);
        limiterReleaseCoeff = (float) Math.exp(-1.0 / releaseSamples);
        limiterGain = 1.0f;
    }

    // ==================== Auto-Adjustment API ====================

    /**
     * Sets the auto-gain level for normalization.
     * Call this after analyzing the audio with AudioQualityAnalyzer.
     *
     * @param gain the recommended gain from AudioQualityAnalyzer
     */
    public void setAutoGain(float gain) {
        this.targetAutoGain = Math.max(0.5f, Math.min(2.0f, gain));
    }

    /**
     * Resets the auto-gain to neutral (1.0).
     */
    public void resetAutoGain() {
        this.autoGain = 1.0f;
        this.targetAutoGain = 1.0f;
    }

    /**
     * Applies automatic EQ adjustments based on audio analysis.
     * This blends with user EQ settings rather than replacing them.
     *
     * @param autoEqBands array of 10 EQ band adjustments in dB from AudioQualityAnalyzer
     */
    public void applyAutoEQ(float[] autoEqBands) {
        if (autoEqBands == null || autoEqBands.length != EQ_BAND_COUNT) {
            return;
        }

        AudioSettings settings = AudioSettings.getInstance();

        for (int band = 0; band < EQ_BAND_COUNT; band++) {
            float userGainDb = settings.getEqualizerBandDb(band);
            float autoGainDb = autoEqBands[band];

            // Blend user EQ with auto EQ (auto EQ is additive)
            float combinedGainDb = userGainDb + autoGainDb;

            // Clamp to reasonable range
            combinedGainDb = Math.max(-12f, Math.min(12f, combinedGainDb));

            float freq = AudioSettings.getBandFrequency(band);
            for (int ch = 0; ch < STEREO_CHANNELS; ch++) {
                eqFilters[band][ch].setPeakingEQ(sampleRate, freq, DEFAULT_Q_FACTOR, combinedGainDb);
            }
        }
    }

    /**
     * Resets EQ to user settings only (removes auto-EQ adjustments).
     */
    public void resetAutoEQ() {
        AudioSettings settings = AudioSettings.getInstance();
        for (int band = 0; band < EQ_BAND_COUNT; band++) {
            float userGainDb = settings.getEqualizerBandDb(band);
            float freq = AudioSettings.getBandFrequency(band);
            for (int ch = 0; ch < STEREO_CHANNELS; ch++) {
                eqFilters[band][ch].setPeakingEQ(sampleRate, freq, DEFAULT_Q_FACTOR, userGainDb);
            }
        }
    }

    // ==================== Audio Processing ====================
    /**
     * @param buffer   the audio buffer (16-bit signed PCM, interleaved stereo or mono)
     * @param offset   starting offset in the buffer
     * @param length   number of bytes to process
     * @param channels number of audio channels (1 or 2)
     */
    public void process(byte[] buffer, int offset, int length, int channels) {
        AudioSettings settings = AudioSettings.getInstance();

        smoothAutoGain();

        if (!settings.isEnhancementsEnabled()) {
            applyMasterVolumeWithLimiter(buffer, offset, length, settings.getMasterVolume());
            return;
        }

        updateFilters(settings);

        if (channels == STEREO_CHANNELS) {
            processStereo(buffer, offset, length, settings);
        } else {
            processMono(buffer, offset, length, settings);
        }
    }

    private void smoothAutoGain() {
        if (Math.abs(autoGain - targetAutoGain) > 0.001f) {
            autoGain += (targetAutoGain - autoGain) * AUTO_GAIN_SMOOTHING;
        }
    }

    private void updateFilters(AudioSettings settings) {
        updateEqualizerFilters(settings);
        updateBassBoostFilters(settings);
    }

    private void updateEqualizerFilters(AudioSettings settings) {
        for (int band = 0; band < EQ_BAND_COUNT; band++) {
            float gainDb = settings.getEqualizerBandDb(band);
            float freq = AudioSettings.getBandFrequency(band);
            for (int ch = 0; ch < STEREO_CHANNELS; ch++) {
                eqFilters[band][ch].setPeakingEQ(sampleRate, freq, DEFAULT_Q_FACTOR, gainDb);
            }
        }
    }

    private void updateBassBoostFilters(AudioSettings settings) {
        float bassBoostDb = settings.getBassBoost() * MAX_BASS_BOOST_DB;
        for (int ch = 0; ch < STEREO_CHANNELS; ch++) {
            bassBoostFilters[ch].setLowShelf(sampleRate, BASS_BOOST_FREQUENCY, BASS_BOOST_Q, bassBoostDb);
        }
    }

    private void processStereo(byte[] buffer, int offset, int length, AudioSettings settings) {
        float masterVol = settings.getMasterVolume();
        float stereoWidth = settings.getStereoWidth();
        float surroundLevel = settings.getSurroundLevel();

        for (int i = offset; i < offset + length; i += 4) {
            float left = bytesToSample(buffer, i);
            float right = bytesToSample(buffer, i + 2);

            // Apply auto-gain normalization
            left *= autoGain;
            right *= autoGain;

            left = applyEqualizerToChannel(left, 0);
            right = applyEqualizerToChannel(right, 1);

            left = bassBoostFilters[0].process(left);
            right = bassBoostFilters[1].process(right);

            float[] stereoResult = applyStereoWidth(left, right, stereoWidth);
            left = stereoResult[0];
            right = stereoResult[1];

            if (surroundLevel > 0) {
                float[] surroundResult = applySurroundEffect(left, right, surroundLevel);
                left = surroundResult[0];
                right = surroundResult[1];
            }

            // Apply master volume
            left *= masterVol;
            right *= masterVol;

            // Apply limiter for transparent peak control
            float[] limited = applyLimiter(left, right);
            left = limited[0];
            right = limited[1];

            sampleToBytes(left, buffer, i);
            sampleToBytes(right, buffer, i + 2);
        }
    }

    private float applyEqualizerToChannel(float sample, int channel) {
        for (int band = 0; band < EQ_BAND_COUNT; band++) {
            sample = eqFilters[band][channel].process(sample);
        }
        return sample;
    }

    private float[] applyStereoWidth(float left, float right, float stereoWidth) {
        if (stereoWidth == 1.0f) {
            return new float[]{left, right};
        }
        float mid = (left + right) * 0.5f;
        float side = (left - right) * 0.5f * stereoWidth;
        return new float[]{mid + side, mid - side};
    }

    private float[] applySurroundEffect(float left, float right, float surroundLevel) {
        int delayOffset = (int) (SURROUND_DELAY_SAMPLES * SURROUND_DELAY_RATIO) * STEREO_CHANNELS;
        int delayIdx = (surroundDelayIndex + SURROUND_DELAY_SAMPLES * STEREO_CHANNELS - delayOffset)
            % (SURROUND_DELAY_SAMPLES * STEREO_CHANNELS);

        float delayedLeft = surroundDelayBuffer[delayIdx];
        float delayedRight = surroundDelayBuffer[delayIdx + 1];

        float crossFeed = surroundLevel * SURROUND_CROSS_FEED_AMOUNT;
        left += delayedRight * crossFeed;
        right += delayedLeft * crossFeed;

        surroundDelayBuffer[surroundDelayIndex] = left;
        surroundDelayBuffer[surroundDelayIndex + 1] = right;
        surroundDelayIndex = (surroundDelayIndex + 2) % (SURROUND_DELAY_SAMPLES * STEREO_CHANNELS);

        return new float[]{left, right};
    }

    private void processMono(byte[] buffer, int offset, int length, AudioSettings settings) {
        float masterVol = settings.getMasterVolume();

        for (int i = offset; i < offset + length; i += 2) {
            float sample = bytesToSample(buffer, i);

            // Apply auto-gain normalization
            sample *= autoGain;

            sample = applyEqualizerToChannel(sample, 0);
            sample = bassBoostFilters[0].process(sample);

            // Apply master volume
            sample *= masterVol;

            // Apply limiter
            sample = applyMonoLimiter(sample);

            sampleToBytes(sample, buffer, i);
        }
    }

    private void applyMasterVolumeWithLimiter(byte[] buffer, int offset, int length, float volume) {
        for (int i = offset; i < offset + length; i += 2) {
            float sample = bytesToSample(buffer, i);
            sample *= autoGain * volume;
            sample = applyMonoLimiter(sample);
            sampleToBytes(sample, buffer, i);
        }
    }

    // ==================== Limiter Implementation ====================

    /**
     * Applies a transparent look-ahead style limiter to stereo samples.
     * Uses adaptive gain reduction with fast attack and slow release.
     */
    private float[] applyLimiter(float left, float right) {
        float peak = Math.max(Math.abs(left), Math.abs(right));

        if (peak > LIMITER_THRESHOLD) {
            float targetGain = LIMITER_THRESHOLD / peak;
            limiterGain = Math.min(limiterGain, targetGain);
            limiterGain = limiterGain * limiterAttackCoeff + targetGain * (1 - limiterAttackCoeff);
        } else {
            limiterGain = limiterGain * limiterReleaseCoeff + 1.0f * (1 - limiterReleaseCoeff);
        }

        limiterGain = Math.min(1.0f, limiterGain);

        return new float[]{
            softClip(left * limiterGain),
            softClip(right * limiterGain)
        };
    }

    /**
     * Applies limiter to a mono sample.
     */
    private float applyMonoLimiter(float sample) {
        float peak = Math.abs(sample);

        if (peak > LIMITER_THRESHOLD) {
            float targetGain = LIMITER_THRESHOLD / peak;
            limiterGain = Math.min(limiterGain, targetGain);
            limiterGain = limiterGain * limiterAttackCoeff + targetGain * (1 - limiterAttackCoeff);
        } else {
            limiterGain = limiterGain * limiterReleaseCoeff + 1.0f * (1 - limiterReleaseCoeff);
        }

        limiterGain = Math.min(1.0f, limiterGain);

        return softClip(sample * limiterGain);
    }

    // ==================== Sample Conversion ====================

    private float bytesToSample(byte[] buffer, int offset) {
        int sample = (buffer[offset] & 0xFF) | (buffer[offset + 1] << 8);
        return sample / SAMPLE_MAX_VALUE;
    }

    private void sampleToBytes(float sample, byte[] buffer, int offset) {
        int intSample = Math.max(SAMPLE_CLAMP_MIN, Math.min(SAMPLE_CLAMP_MAX, (int) (sample * SAMPLE_CLAMP_MAX)));
        buffer[offset] = (byte) (intSample & 0xFF);
        buffer[offset + 1] = (byte) ((intSample >> 8) & 0xFF);
    }

    // ==================== Soft Clipping ====================

    private float softClip(float sample) {
        if (sample > SOFT_CLIP_THRESHOLD) {
            return SOFT_CLIP_THRESHOLD + SOFT_CLIP_KNEE * (float) Math.tanh((sample - SOFT_CLIP_THRESHOLD) / SOFT_CLIP_KNEE);
        } else if (sample < -SOFT_CLIP_THRESHOLD) {
            return -SOFT_CLIP_THRESHOLD + SOFT_CLIP_KNEE * (float) Math.tanh((sample + SOFT_CLIP_THRESHOLD) / SOFT_CLIP_KNEE);
        }
        return sample;
    }

    // ==================== Biquad Filter ====================

    /**
     * Biquad filter implementation for EQ and bass boost.
     */
    private static class BiquadFilter {
        private float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;

        float process(float input) {
            float output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = input;
            y2 = y1;
            y1 = output;
            return output;
        }

        void setPeakingEQ(float sampleRate, float centerFreq, float Q, float gainDb) {
            float A = (float) Math.pow(10, gainDb / 40);
            float w0 = 2 * (float) Math.PI * centerFreq / sampleRate;
            float cosW0 = (float) Math.cos(w0);
            float sinW0 = (float) Math.sin(w0);
            float alpha = sinW0 / (2 * Q);

            float a0 = 1 + alpha / A;
            b0 = (1 + alpha * A) / a0;
            b1 = (-2 * cosW0) / a0;
            b2 = (1 - alpha * A) / a0;
            a1 = (-2 * cosW0) / a0;
            a2 = (1 - alpha / A) / a0;
        }

        void setLowShelf(float sampleRate, float freq, float Q, float gainDb) {
            float A = (float) Math.pow(10, gainDb / 40);
            float w0 = 2 * (float) Math.PI * freq / sampleRate;
            float cosW0 = (float) Math.cos(w0);
            float sinW0 = (float) Math.sin(w0);
            float alpha = sinW0 / (2 * Q);
            float sqrtA = (float) Math.sqrt(A);

            float a0 = (A + 1) + (A - 1) * cosW0 + 2 * sqrtA * alpha;
            b0 = (A * ((A + 1) - (A - 1) * cosW0 + 2 * sqrtA * alpha)) / a0;
            b1 = (2 * A * ((A - 1) - (A + 1) * cosW0)) / a0;
            b2 = (A * ((A + 1) - (A - 1) * cosW0 - 2 * sqrtA * alpha)) / a0;
            a1 = (-2 * ((A - 1) + (A + 1) * cosW0)) / a0;
            a2 = ((A + 1) + (A - 1) * cosW0 - 2 * sqrtA * alpha) / a0;
        }
    }
}

