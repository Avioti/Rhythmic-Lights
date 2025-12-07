package com.rhythm;

import com.rhythm.config.ConfigManager;
import com.rhythm.config.RhythmConfig;
import com.rhythm.particle.ColoredParticleEffect;
import com.rhythm.registry.RhythmModBlockEntities;
import com.rhythm.registry.RhythmModBlocks;
import com.rhythm.registry.RhythmModItems;
import com.rhythm.registry.RhythmModParticles;
import com.rhythm.registry.RhythmModRecipes;
import com.rhythm.registry.RhythmModCreativeTabPopulator;
import com.rhythm.registry.RhythmModTabs;
import com.rhythm.util.RhythmConstants;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

/**
 * Main mod class for RhythmMod - Redstone Rhythm Lights.
 */
public final class RhythmMod {

    // ==================== Mod Info ====================

    public static final String MOD_ID = "rhythmmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ==================== Configuration ====================

    public static RhythmConfig CONFIG;

    // ==================== Re-exports (for backward compatibility) ====================

    // Blocks
    public static final RegistrySupplier<Block> RHYTHM_CONTROLLER_BLOCK = RhythmModBlocks.RHYTHM_CONTROLLER;
    public static final RegistrySupplier<Block> RHYTHM_BULB_BLOCK = RhythmModBlocks.RHYTHM_BULB;

    // Items
    public static final RegistrySupplier<Item> RHYTHM_CONTROLLER_ITEM = RhythmModItems.RHYTHM_CONTROLLER;
    public static final RegistrySupplier<Item> RHYTHM_BULB_ITEM = RhythmModItems.RHYTHM_BULB;
    public static final RegistrySupplier<Item> TUNING_WAND_ITEM = RhythmModItems.TUNING_WAND;
    public static final RegistrySupplier<Item> RHYTHM_URL_DISC_ITEM = RhythmModItems.RHYTHM_URL_DISC;

    // Block Entities
    public static final RegistrySupplier<BlockEntityType<?>> RHYTHM_CONTROLLER_BLOCK_ENTITY =
        (RegistrySupplier) RhythmModBlockEntities.RHYTHM_CONTROLLER;
    public static final RegistrySupplier<BlockEntityType<?>> RHYTHM_BULB_BLOCK_ENTITY =
        (RegistrySupplier) RhythmModBlockEntities.RHYTHM_BULB;

    // Particles
    public static final RegistrySupplier<ParticleType<ColoredParticleEffect>> COLORED_PARTICLE =
        RhythmModParticles.COLORED_PARTICLE;

    // ==================== Initialization ====================

    public static void init() {
        loadConfiguration();
        registerAll();
        initializeTabs();
        logAudioDiagnostics();
        logStartupBanner();
    }

    private static void loadConfiguration() {
        CONFIG = ConfigManager.load(RhythmConfig.class, "rhythmmod-config");
        CONFIG.applyAudioSettings();
    }

    private static void registerAll() {
        RhythmModBlocks.register();
        RhythmModItems.register();
        RhythmModBlockEntities.register();
        RhythmModParticles.register();
        RhythmModRecipes.register();
    }

    private static void initializeTabs() {
        RhythmModTabs.init();
        RhythmModCreativeTabPopulator.init();
    }

    // ==================== Startup Logging ====================

    private static void logStartupBanner() {
        LOGGER.info("╔═══════════════════════════════════════════════╗");
        LOGGER.info("║ RhythmicLights - Redstone Rhythm Lights v2.0  ║");
        LOGGER.info("║   Multi-Band FFT Audio Analysis Active        ║");
        LOGGER.info("╚═══════════════════════════════════════════════╝");
    }

    // ==================== Audio Diagnostics ====================

    private static void logAudioDiagnostics() {
        if (!RhythmConstants.DEBUG_AUDIO) return;

        try {
            logAvailableAudioTypes();
        } catch (Exception e) {
            LOGGER.error("Failed to check audio providers", e);
        }
    }

    private static void logAvailableAudioTypes() {
        LOGGER.info("========== AUDIO SYSTEM DIAGNOSTICS ==========");

        AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
        LOGGER.info("Available audio file types: {}", types.length);

        for (AudioFileFormat.Type type : types) {
            LOGGER.info("  - {}", type);
        }

        logCodecSupport(types);
        LOGGER.info("==============================================");
    }

    private static void logCodecSupport(AudioFileFormat.Type[] types) {
        boolean hasOgg = false;
        boolean hasMp3 = false;

        for (AudioFileFormat.Type type : types) {
            String typeName = type.toString().toLowerCase();
            if (typeName.contains("ogg") || typeName.contains("vorbis")) {
                hasOgg = true;
            }
            if (typeName.contains("mp3") || typeName.contains("mpeg")) {
                hasMp3 = true;
            }
        }

        LOGGER.info("OGG Vorbis support: {}", hasOgg ? "YES" : "NO - vorbisspi may be missing!");
        LOGGER.info("MP3 support: {}", hasMp3 ? "YES" : "NO - mp3spi may be missing!");
    }

    private RhythmMod() {}
}
