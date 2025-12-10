package com.rhythm.audio.analysis;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs FFT (Fast Fourier Transform) analysis on audio samples.
 * Extracts power in each of 12 frequency bands for beat detection.
 */
public final class FFTAnalyzer {

    // ==================== FFT Configuration ====================

    /** FFT window size - larger = better frequency resolution, worse time resolution */
    public static final int FFT_SIZE = 2048;

    /** Hop size between FFT frames (~2.5ms at 44.1kHz for maximum temporal precision) */
    public static final int HOP_SIZE = 110;

    /** Number of frequency bands for analysis */
    public static final int FREQUENCY_BAND_COUNT = 12;

    // ==================== Frequency Band Cutoffs (Hz) ====================

    private static final double FREQ_SUB_BASS = 40;
    private static final double FREQ_DEEP_BASS = 80;
    private static final double FREQ_BASS = 150;
    private static final double FREQ_LOW_MID = 300;
    private static final double FREQ_MID_LOW = 500;
    private static final double FREQ_MID = 800;
    private static final double FREQ_MID_HIGH = 1200;
    private static final double FREQ_HIGH_MID = 2000;
    private static final double FREQ_HIGH = 4000;
    private static final double FREQ_VERY_HIGH = 8000;
    private static final double FREQ_ULTRA = 12000;

    private static final double DB_EPSILON = 1e-10;

    private FFTAnalyzer() {
        // Utility class
    }

    // ==================== Public API ====================

    /**
     * Performs FFT analysis on audio samples and extracts power in each frequency band.
     *
     * @param audioSamples mono audio samples normalized to [-1.0, 1.0]
     * @param sampleRate   the audio sample rate in Hz
     * @return list of decibel values per frequency band for each FFT frame
     */
    public static List<double[]> calculateRawBands(double[] audioSamples, float sampleRate) {
        List<double[]> allBands = new ArrayList<>();
        int totalSamples = audioSamples.length;

        double[] window = createHanningWindow();
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

    // ==================== Window Functions ====================

    /**
     * Creates a Hanning window of the FFT size.
     */
    private static double[] createHanningWindow() {
        double[] window = new double[FFT_SIZE];
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
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

    // ==================== Band Power Calculation ====================

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
        return 11;
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
}

