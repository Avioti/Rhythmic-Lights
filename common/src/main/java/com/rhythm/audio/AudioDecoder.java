package com.rhythm.audio;

import com.rhythm.RhythmMod;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider;
import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unified audio decoding utility for RhythmicLights.
 *
 * <p>Consolidates all audio format detection and conversion logic into one place.
 * Supports: OGG Vorbis, MP3, WAV, AIFF, AU</p>
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>{@link AudioPreComputer} for FFT analysis</li>
 *   <li>{@link SeekableAudioPlayer} for playback</li>
 * </ul>
 */
public class AudioDecoder {

    private static final AudioFormat.Encoding PCM_SIGNED = AudioFormat.Encoding.PCM_SIGNED;
    private static final String LOG_PREFIX = "[AudioDecoder] ";
    private static final int PCM_BITS_PER_SAMPLE = 16;

    private AudioDecoder() {
        // Utility class - prevent instantiation
    }

    /**
     * Decodes any supported audio stream to an AudioInputStream.
     *
     * <p>Automatically detects format by trying decoders in order:</p>
     * <ol>
     *   <li>OGG Vorbis (most common for Minecraft)</li>
     *   <li>MP3</li>
     *   <li>AudioSystem fallback (WAV/AIFF/AU)</li>
     * </ol>
     *
     * @param inputStream the raw audio input stream (should support mark/reset)
     * @return AudioInputStream in the original format, or null if format not supported
     */
    public static AudioInputStream getAudioInputStream(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }

        try {
            markStreamIfSupported(inputStream);

            AudioInputStream result = tryDecodeOggVorbis(inputStream);
            if (result != null) return result;

            result = tryDecodeMp3(inputStream);
            if (result != null) return result;

            return tryDecodeWithAudioSystem(inputStream);

        } catch (Exception e) {
            RhythmMod.LOGGER.error(LOG_PREFIX + "Failed to decode audio: {}", e.getMessage());
            return null;
        }
    }

    private static void markStreamIfSupported(InputStream inputStream) {
        if (inputStream.markSupported()) {
            inputStream.mark(Integer.MAX_VALUE);
        }
    }

    private static AudioInputStream tryDecodeOggVorbis(InputStream inputStream) {
        try {
            RhythmMod.LOGGER.debug(LOG_PREFIX + "Trying OGG Vorbis decoder...");
            AudioFileReader vorbisReader = new VorbisAudioFileReader();
            AudioInputStream result = vorbisReader.getAudioInputStream(inputStream);
            RhythmMod.LOGGER.info(LOG_PREFIX + "✓ Decoded as OGG Vorbis");
            return result;
        } catch (Exception e) {
            RhythmMod.LOGGER.debug(LOG_PREFIX + "Not OGG: {}", e.getMessage());
            resetStream(inputStream);
            return null;
        }
    }

    private static AudioInputStream tryDecodeMp3(InputStream inputStream) {
        try {
            RhythmMod.LOGGER.debug(LOG_PREFIX + "Trying MP3 decoder...");
            AudioFileReader mp3Reader = new MpegAudioFileReader();
            AudioInputStream result = mp3Reader.getAudioInputStream(inputStream);
            RhythmMod.LOGGER.info(LOG_PREFIX + "✓ Decoded as MP3");
            return result;
        } catch (Exception e) {
            RhythmMod.LOGGER.debug(LOG_PREFIX + "Not MP3: {}", e.getMessage());
            resetStream(inputStream);
            return null;
        }
    }

    private static AudioInputStream tryDecodeWithAudioSystem(InputStream inputStream) {
        try {
            RhythmMod.LOGGER.debug(LOG_PREFIX + "Trying AudioSystem fallback...");
            AudioInputStream result = AudioSystem.getAudioInputStream(inputStream);
            RhythmMod.LOGGER.info(LOG_PREFIX + "✓ Decoded via AudioSystem ({})", result.getFormat().getEncoding());
            return result;
        } catch (Exception e) {
            RhythmMod.LOGGER.error(LOG_PREFIX + "AudioSystem fallback failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converts an AudioInputStream to a specific target format.
     *
     * @param source       the source AudioInputStream
     * @param targetFormat the desired output format
     * @return converted AudioInputStream, or null if conversion failed
     */
    public static AudioInputStream convertToFormat(AudioInputStream source, AudioFormat targetFormat) {
        if (source == null) {
            return null;
        }

        AudioFormat sourceFormat = source.getFormat();
        if (sourceFormat.matches(targetFormat)) {
            return source;
        }

        String encodingName = sourceFormat.getEncoding().toString().toUpperCase();
        RhythmMod.LOGGER.debug(LOG_PREFIX + "Converting from {} to PCM", encodingName);

        try {
            AudioInputStream result = tryConvertWithVorbis(source, targetFormat, sourceFormat, encodingName);
            if (result != null) return result;

            result = tryConvertWithMpeg(source, targetFormat, sourceFormat, encodingName);
            if (result != null) return result;

            return tryConvertWithAudioSystem(source, targetFormat, sourceFormat);

        } catch (Exception e) {
            RhythmMod.LOGGER.error(LOG_PREFIX + "Format conversion error: {}", e.getMessage());
            return null;
        }
    }

    private static AudioInputStream tryConvertWithVorbis(AudioInputStream source, AudioFormat targetFormat,
                                                          AudioFormat sourceFormat, String encodingName) {
        if (!encodingName.contains("VORBIS")) {
            return null;
        }
        try {
            FormatConversionProvider vorbisConverter = new VorbisFormatConversionProvider();
            if (vorbisConverter.isConversionSupported(targetFormat, sourceFormat)) {
                AudioInputStream result = vorbisConverter.getAudioInputStream(targetFormat, source);
                RhythmMod.LOGGER.info(LOG_PREFIX + "✓ Vorbis → PCM conversion successful");
                return result;
            }
        } catch (Exception e) {
            RhythmMod.LOGGER.warn(LOG_PREFIX + "Vorbis conversion failed: {}", e.getMessage());
        }
        return null;
    }

    private static AudioInputStream tryConvertWithMpeg(AudioInputStream source, AudioFormat targetFormat,
                                                        AudioFormat sourceFormat, String encodingName) {
        if (!encodingName.contains("MP") && !encodingName.contains("MPEG")) {
            return null;
        }
        try {
            FormatConversionProvider mp3Converter = new MpegFormatConversionProvider();
            if (mp3Converter.isConversionSupported(targetFormat, sourceFormat)) {
                AudioInputStream result = mp3Converter.getAudioInputStream(targetFormat, source);
                RhythmMod.LOGGER.info(LOG_PREFIX + "✓ MP3 → PCM conversion successful");
                return result;
            }
        } catch (Exception e) {
            RhythmMod.LOGGER.warn(LOG_PREFIX + "MP3 conversion failed: {}", e.getMessage());
        }
        return null;
    }

    private static AudioInputStream tryConvertWithAudioSystem(AudioInputStream source, AudioFormat targetFormat,
                                                               AudioFormat sourceFormat) {
        if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
            RhythmMod.LOGGER.debug(LOG_PREFIX + "Using AudioSystem for conversion");
            return AudioSystem.getAudioInputStream(targetFormat, source);
        }
        return null;
    }

    /**
     * Builds a standard PCM format for a given sample rate and channel count.
     *
     * @param sampleRate sample rate in Hz
     * @param channels   number of channels (1 = mono, 2 = stereo)
     * @return AudioFormat for 16-bit signed PCM, little-endian
     */
    public static AudioFormat buildPcmFormat(float sampleRate, int channels) {
        int frameSize = channels * (PCM_BITS_PER_SAMPLE / 8);
        return new AudioFormat(
            PCM_SIGNED,
            sampleRate,
            PCM_BITS_PER_SAMPLE,
            channels,
            frameSize,
            sampleRate,
            false
        );
    }

    private static void resetStream(InputStream stream) {
        if (stream.markSupported()) {
            try {
                stream.reset();
                stream.mark(Integer.MAX_VALUE);
            } catch (IOException e) {
                RhythmMod.LOGGER.debug(LOG_PREFIX + "Could not reset stream: {}", e.getMessage());
            }
        }
    }
}

