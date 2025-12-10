package com.rhythm.audio.analysis;

import com.rhythm.util.RhythmConstants;

/**
 * Multi-scale onset detection for beat and rhythm analysis.
 * Detects energy increases, spectral flux, and phase coherence
 * to identify musical beats and transients.
 */
public final class OnsetDetector {

    // ==================== Detection Parameters ====================

    private static final int[] WINDOW_SIZES = {1, 2, 3, 5, 8};
    private static final float[] WINDOW_WEIGHTS = {0.35f, 0.30f, 0.20f, 0.10f, 0.05f};

    private static final float ADAPTIVE_THRESHOLD_MIN = 0.2f;
    private static final float DYNAMIC_RANGE_FACTOR = 0.02f;
    private static final float STD_DEV_FACTOR = 0.15f;
    private static final float RELATIVE_THRESHOLD = 0.03f;
    private static final float PEAK_RELATIVE_THRESHOLD = 0.02f;
    private static final float PEAK_BONUS = 1.2f;

    private static final float FLUX_WEIGHT = 0.25f;
    private static final float ACCELERATION_WEIGHT = 0.15f;
    private static final float PHASE_WEIGHT = 0.1f;

    private static final float SILENCE_THRESHOLD_DB = -80f;

    private OnsetDetector() {
        // Utility class
    }

    // ==================== Public API ====================

    /**
     * Multi-scale onset detection for maximum beat capture.
     * Uses multiple window sizes to detect both fast beats (snares, hi-hats)
     * and slow beats (kicks, bass drops).
     *
     * @param energyData energy values per tick (in dB)
     * @return onset strength per tick (higher = stronger beat)
     */
    public static float[] detectOnsets(float[] energyData) {
        int length = energyData.length;
        float[] onsets = new float[length];

        EnergyStatistics stats = calculateEnergyStatistics(energyData);
        logStatistics(stats);

        applyMultiScaleDetection(energyData, onsets, stats);
        applySpectralFlux(energyData, onsets);
        applyPhaseCoherence(energyData, onsets);

        return onsets;
    }

    // ==================== Energy Statistics ====================

    /**
     * Container for energy statistics used in onset detection.
     */
    public record EnergyStatistics(float mean, float min, float max, float range, float stdDev) {}

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

    private static void logStatistics(EnergyStatistics stats) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Onset detection - Mean: {} dB, Range: {} dB, StdDev: {} dB",
                String.format("%.2f", stats.mean),
                String.format("%.2f", stats.range),
                String.format("%.2f", stats.stdDev));
        }
    }

    // ==================== Multi-Scale Detection ====================

    private static void applyMultiScaleDetection(float[] energyData, float[] onsets, EnergyStatistics stats) {
        int length = energyData.length;

        for (int scale = 0; scale < WINDOW_SIZES.length; scale++) {
            int windowSize = WINDOW_SIZES[scale];
            float weight = WINDOW_WEIGHTS[scale];

            for (int i = 0; i < length; i++) {
                float localAvg = calculateLocalAverage(energyData, i, windowSize, stats.mean);
                float energyIncrease = energyData[i] - localAvg;
                float relativeIncrease = calculateRelativeIncrease(energyData[i], localAvg);
                boolean isPeak = isLocalPeak(energyData, i);

                float adaptiveThreshold = calculateAdaptiveThreshold(stats);

                if (isOnsetDetected(energyIncrease, relativeIncrease, isPeak, adaptiveThreshold)) {
                    float peakBonus = isPeak ? PEAK_BONUS : 1.0f;
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
        if (localAvg <= SILENCE_THRESHOLD_DB) {
            return 0;
        }
        return (current - localAvg) / (Math.abs(localAvg) + 1);
    }

    private static boolean isLocalPeak(float[] data, int index) {
        if (index > 0 && data[index] <= data[index - 1]) {
            return false;
        }
        if (index < data.length - 1 && data[index] < data[index + 1]) {
            return false;
        }
        return true;
    }

    private static float calculateAdaptiveThreshold(EnergyStatistics stats) {
        float dynamicThreshold = stats.range * DYNAMIC_RANGE_FACTOR;
        float stdDevThreshold = stats.stdDev * STD_DEV_FACTOR;
        return Math.max(ADAPTIVE_THRESHOLD_MIN, Math.min(dynamicThreshold, stdDevThreshold));
    }

    private static boolean isOnsetDetected(float energyIncrease, float relativeIncrease,
                                           boolean isPeak, float adaptiveThreshold) {
        if (energyIncrease > adaptiveThreshold) {
            return true;
        }
        if (relativeIncrease > RELATIVE_THRESHOLD) {
            return true;
        }
        return isPeak && relativeIncrease > PEAK_RELATIVE_THRESHOLD;
    }

    private static float calculateOnsetStrength(float energyIncrease, float relativeIncrease,
                                                float weight, float peakBonus) {
        float relativeContribution = relativeIncrease * 15f;
        return Math.max(energyIncrease, relativeContribution) * weight * peakBonus;
    }

    // ==================== Spectral Flux Detection ====================

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

            onsets[i] += flux * FLUX_WEIGHT + acceleration * ACCELERATION_WEIGHT;
        }
    }

    // ==================== Phase Coherence Detection ====================

    /**
     * Applies phase coherence detection for sustained tones.
     */
    private static void applyPhaseCoherence(float[] energyData, float[] onsets) {
        for (int i = 2; i < energyData.length - 2; i++) {
            float leftSlope = energyData[i] - energyData[i - 1];
            float rightSlope = energyData[i + 1] - energyData[i];

            boolean isInflectionPoint = leftSlope * rightSlope < 0;
            boolean hasSignificantSlope = Math.abs(leftSlope) > 0.5f;

            if (isInflectionPoint && hasSignificantSlope) {
                onsets[i] += Math.abs(leftSlope) * PHASE_WEIGHT;
            }
        }
    }
}

