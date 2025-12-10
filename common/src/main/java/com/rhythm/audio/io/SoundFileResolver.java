package com.rhythm.audio.io;

import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Universal sound file resolver that works with any mod that registers sounds via sounds.json.
 * <p>
 * Instead of guessing file paths, this resolver asks Minecraft's SoundManager where the
 * actual file is located. This makes RhythmMod compatible with:
 * <ul>
 *   <li>Vanilla discs</li>
 *   <li>Modded discs (Twilight Forest, Blue Skies, Alex's Mobs, etc.)</li>
 *   <li>Resource pack replacements</li>
 *   <li>Any custom sound events</li>
 * </ul>
 */
public class SoundFileResolver {

    // ==================== Constants ====================

    private static final String MOD_NAMESPACE = "rhythmmod";
    private static final String PLACEHOLDER_PATH_SEGMENT = "placeholder";

    // ==================== Constructor ====================

    private SoundFileResolver() {
        // Utility class - prevent instantiation
    }

    // ==================== Public API ====================

    /**
     * Resolves a SoundEvent ID to an actual InputStream of the audio file.
     *
     * @param soundEventId the sound event ID (e.g., "minecraft:music_disc.chirp")
     * @return InputStream of the audio file, or null if not found
     */
    public static InputStream getStreamForSound(ResourceLocation soundEventId) {
        try {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            WeighedSoundEvents weighedSoundEvents = soundManager.getSoundEvent(soundEventId);

            if (weighedSoundEvents == null) {
                logMissingSoundEvent(soundEventId);
                return null;
            }

            Sound sound = resolveSound(weighedSoundEvents);
            ResourceLocation fileLocation = sound.getPath();

            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Resolved sound event: {} -> {}", soundEventId, fileLocation);
            }

            return loadAudioData(fileLocation);

        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error resolving sound: {}", soundEventId, e);
            return null;
        }
    }

    // ==================== Sound Resolution ====================

    private static Sound resolveSound(WeighedSoundEvents weighedSoundEvents) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return weighedSoundEvents.getSound(mc.level.random);
        }
        return weighedSoundEvents.getSound(RandomSource.create());
    }

    private static InputStream loadAudioData(ResourceLocation fileLocation) throws IOException {
        InputStream rawStream = Minecraft.getInstance().getResourceManager()
            .getResource(fileLocation)
            .orElseThrow(() -> new IOException("Resource not found: " + fileLocation))
            .open();

        byte[] audioData = IOUtils.toByteArray(rawStream);
        rawStream.close();

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Loaded {} bytes into memory", audioData.length);
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(audioData);
        byteStream.mark(audioData.length);

        return byteStream;
    }

    // ==================== Error Handling ====================

    private static void logMissingSoundEvent(ResourceLocation soundEventId) {
        if (isPlaceholderSound(soundEventId)) {
            RhythmConstants.LOGGER.warn("Placeholder sound event detected: {}", soundEventId);
            RhythmConstants.LOGGER.warn("This disc should have a URL but none was found. Check NBT data.");
        } else {
            RhythmConstants.LOGGER.error("Unknown sound event: {}", soundEventId);
        }
    }

    private static boolean isPlaceholderSound(ResourceLocation soundEventId) {
        return soundEventId.getNamespace().equals(MOD_NAMESPACE)
            || soundEventId.getPath().contains(PLACEHOLDER_PATH_SEGMENT);
    }
}

