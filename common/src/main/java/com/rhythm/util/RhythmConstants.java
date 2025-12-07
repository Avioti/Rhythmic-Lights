package com.rhythm.util;

import com.rhythm.RhythmMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.JukeboxSong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Constants and shared values for RhythmMod.
 */
public final class RhythmConstants {

    private RhythmConstants() {}

    // ==================== Mod Info ====================

    public static final String MOD_ID = RhythmMod.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ==================== Debug Controls ====================

    /** Master debug toggle - set to false for release builds */
    public static final boolean DEBUG_ENABLED = false;

    /** Audio system debug logging */
    public static final boolean DEBUG_AUDIO = DEBUG_ENABLED;

    /** Networking/packet debug logging */
    public static final boolean DEBUG_NETWORK = false;

    /** Light/rendering debug logging */
    public static final boolean DEBUG_LIGHTS = false;

    /** Block entity debug logging */
    public static final boolean DEBUG_BLOCK_ENTITIES = false;

    /** GUI/screen debug logging */
    public static final boolean DEBUG_GUI = false;

    /** Download/executable debug logging */
    public static final boolean DEBUG_DOWNLOADS = DEBUG_ENABLED;

    // ==================== Log Prefixes ====================

    private static final String LOG_PREFIX_AUDIO = "[Audio] ";
    private static final String LOG_PREFIX_NETWORK = "[Network] ";
    private static final String LOG_PREFIX_LIGHTS = "[Lights] ";
    private static final String LOG_PREFIX_BLOCK_ENTITY = "[BlockEntity] ";
    private static final String LOG_PREFIX_GUI = "[GUI] ";
    private static final String LOG_PREFIX_DOWNLOAD = "[Download] ";

    // ==================== Jukebox Song ====================

    public static final ResourceLocation PLACEHOLDER_SOUND_ID =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "placeholder_sound");

    public static final ResourceKey<JukeboxSong> JUKEBOX_SONG =
        ResourceKey.create(Registries.JUKEBOX_SONG, PLACEHOLDER_SOUND_ID);

    // ==================== NBT Keys ====================

    public static final String NBT_URL = "music_url";
    public static final String NBT_DURATION = "duration";
    public static final String NBT_LOOP = "loop";
    public static final String NBT_LOCK = "lock";
    public static final String NBT_TITLE = "title";

    // ==================== Audio Settings ====================

    public static final String DEFAULT_AUDIO_QUALITY = "96K";
    public static final String DEFAULT_AUDIO_FORMAT = "vorbis";
    public static final int MAX_URL_LENGTH = 400;

    // ==================== File Paths ====================

    private static final String RHYTHMMOD_DIR = "rhythmmod";
    private static final String DOWNLOADS_DIR = "downloads";
    private static final String EXECUTABLES_DIR = "executables";

    public static Path getGameDirectory() {
        try {
            return Minecraft.getInstance().gameDirectory.toPath();
        } catch (Exception e) {
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    public static Path getRhythmModPath() {
        return getGameDirectory().resolve(RHYTHMMOD_DIR);
    }

    public static Path getDownloadsPath() {
        return getRhythmModPath().resolve(DOWNLOADS_DIR);
    }

    public static Path getExecutablesPath() {
        return getRhythmModPath().resolve(EXECUTABLES_DIR);
    }

    // ==================== Debug Logging Helpers ====================

    public static void debugAudio(String message, Object... args) {
        if (DEBUG_AUDIO) {
            LOGGER.info(LOG_PREFIX_AUDIO + message, args);
        }
    }

    public static void debugNetwork(String message, Object... args) {
        if (DEBUG_NETWORK) {
            LOGGER.info(LOG_PREFIX_NETWORK + message, args);
        }
    }

    public static void debugLights(String message, Object... args) {
        if (DEBUG_LIGHTS) {
            LOGGER.info(LOG_PREFIX_LIGHTS + message, args);
        }
    }

    public static void debugBlockEntity(String message, Object... args) {
        if (DEBUG_BLOCK_ENTITIES) {
            LOGGER.info(LOG_PREFIX_BLOCK_ENTITY + message, args);
        }
    }

    public static void debugGui(String message, Object... args) {
        if (DEBUG_GUI) {
            LOGGER.info(LOG_PREFIX_GUI + message, args);
        }
    }

    public static void debugDownload(String message, Object... args) {
        if (DEBUG_DOWNLOADS) {
            LOGGER.info(LOG_PREFIX_DOWNLOAD + message, args);
        }
    }
}
