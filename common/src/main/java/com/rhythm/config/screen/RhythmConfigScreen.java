package com.rhythm.config.screen;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.rhythm.RhythmMod;
import com.rhythm.config.RhythmConfig;

import java.util.List;

/**
 * RhythmMod Configuration Screen.
 * Uses Cloth Config API for cross-platform GUI (Fabric + Forge).
 */
public class RhythmConfigScreen {

    /**
     * Create the config screen.
     * @param parent The parent screen to return to when done
     * @return The config screen
     */
    public static Screen create(Screen parent) {
        RhythmConfig config = RhythmMod.CONFIG;

        // Create config builder
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("config.rhythmmod.title"))
            .setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ============================================================
        // CATEGORY: Visual Settings
        // ============================================================
        ConfigCategory visual = builder.getOrCreateCategory(
            Component.translatable("config.rhythmmod.category.visual")
        );

        // Get all available particle textures (built-in + custom from config)
        List<String> availableParticles = config.getAllParticleTextures();
        String[] particleArray = availableParticles.toArray(new String[0]);

        visual.addEntry(entryBuilder.startSelector(
                Component.translatable("config.rhythmmod.particleTexture"),
                particleArray,
                config.particleTexture
            )
            .setDefaultValue("flash")
            .setNameProvider(texture -> Component.literal(capitalizeFirst(texture)))
            .setTooltip(
                Component.translatable("config.rhythmmod.particleTexture.tooltip.line1"),
                Component.translatable("config.rhythmmod.particleTexture.tooltip.line2"),
                Component.translatable("config.rhythmmod.particleTexture.tooltip.line3"),
                Component.literal(""),
                Component.literal("§7Available: §f" + availableParticles.size() + " particle types"),
                Component.literal("§7Edit config file to add custom particles!")
            )
            .setSaveConsumer(value -> config.particleTexture = value)
            .build()
        );

        visual.addEntry(entryBuilder.startFloatField(
                Component.translatable("config.rhythmmod.particleScale"),
                config.particleScale
            )
            .setDefaultValue(0.3f)
            .setMin(0.1f)
            .setMax(3.0f)
            .setTooltip(
                Component.translatable("config.rhythmmod.particleScale.tooltip.line1"),
                Component.translatable("config.rhythmmod.particleScale.tooltip.line2"),
                Component.literal(""),
                Component.literal("§70.1 = Tiny (subtle)"),
                Component.literal("§70.3 = Small (default)"),
                Component.literal("§70.5 = Medium"),
                Component.literal("§71.0 = Normal"),
                Component.literal("§71.5 = Large"),
                Component.literal("§72.0 = Very Large"),
                Component.literal("§73.0 = Huge (max)")
            )
            .setSaveConsumer(value -> config.particleScale = value)
            .build()
        );

        visual.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.rhythmmod.useRandomColors"),
                config.useRandomColors
            )
            .setDefaultValue(false)
            .setTooltip(
                Component.translatable("config.rhythmmod.useRandomColors.tooltip.line1"),
                Component.translatable("config.rhythmmod.useRandomColors.tooltip.line2"),
                Component.literal(""),
                Component.literal("§aEnabled: §fRandom rainbow colors"),
                Component.literal("§cDisabled: §fFrequency-tuned colors"),
                Component.literal(""),
                Component.literal("§7Perfect for disco/party builds!")
            )
            .setSaveConsumer(value -> config.useRandomColors = value)
            .build()
        );

        visual.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.rhythmmod.coloredShaderLightEnabled"),
                config.coloredShaderLightEnabled
            )
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("§fEnable colored glow lighting around rhythm blocks."),
                Component.literal(""),
                Component.literal("§aEnabled: §7Colored light aura around lamps/bulbs"),
                Component.literal("§cDisabled: §7No glow effect (better performance)"),
                Component.literal(""),
                Component.literal("§7Disable if you have performance issues or"),
                Component.literal("§7prefer a cleaner visual style.")
            )
            .setSaveConsumer(value -> config.setColoredShaderLightEnabled(value))
            .build()
        );

        // ============================================================
        // CATEGORY: Audio Settings
        // ============================================================
        ConfigCategory audio = builder.getOrCreateCategory(
            Component.translatable("config.rhythmmod.category.audio")
        );

        audio.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.rhythmmod.spatialAudioEnabled"),
                config.spatialAudioEnabled
            )
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("§fEnable distance-based volume for jukeboxes."),
                Component.literal(""),
                Component.literal("§aEnabled: §7Volume fades with distance"),
                Component.literal("§cDisabled: §7Full volume everywhere")
            )
            .setSaveConsumer(value -> config.setSpatialAudioEnabled(value))
            .build()
        );

        audio.addEntry(entryBuilder.startFloatField(
                Component.translatable("config.rhythmmod.maxAudioDistance"),
                config.maxAudioDistance
            )
            .setDefaultValue(24.0f)
            .setMin(4.0f)
            .setMax(64.0f)
            .setTooltip(
                Component.literal("§fMaximum distance to hear jukebox audio."),
                Component.literal("§7Beyond this, the jukebox is silent."),
                Component.literal(""),
                Component.literal("§716 = Short range (intimate)"),
                Component.literal("§724 = Default (balanced)"),
                Component.literal("§732 = Medium (larger builds)"),
                Component.literal("§748 = Long range (large areas)"),
                Component.literal("§764 = Maximum (entire area)")
            )
            .setSaveConsumer(value -> config.setMaxAudioDistance(value))
            .build()
        );

        audio.addEntry(entryBuilder.startFloatField(
                Component.translatable("config.rhythmmod.referenceDistance"),
                config.referenceDistance
            )
            .setDefaultValue(4.0f)
            .setMin(1.0f)
            .setMax(16.0f)
            .setTooltip(
                Component.literal("§fDistance for full volume playback."),
                Component.literal("§7At this distance and closer, volume is 100%."),
                Component.literal(""),
                Component.literal("§72 = Must be very close"),
                Component.literal("§74 = Default (single room)"),
                Component.literal("§78 = Medium (larger room)"),
                Component.literal("§712 = Wide (outdoor builds)")
            )
            .setSaveConsumer(value -> config.setReferenceDistance(value))
            .build()
        );

        audio.addEntry(entryBuilder.startFloatField(
                Component.translatable("config.rhythmmod.rolloffFactor"),
                config.rolloffFactor
            )
            .setDefaultValue(1.0f)
            .setMin(0.1f)
            .setMax(3.0f)
            .setTooltip(
                Component.literal("§fHow quickly volume drops with distance."),
                Component.literal("§7Higher = faster volume falloff."),
                Component.literal(""),
                Component.literal("§70.5 = Slow (audio carries far)"),
                Component.literal("§71.0 = Realistic (default)"),
                Component.literal("§71.5 = Fast (localized audio)"),
                Component.literal("§72.0 = Very fast (tight bubble)")
            )
            .setSaveConsumer(value -> config.setRolloffFactor(value))
            .build()
        );

        audio.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.rhythmmod.autoplayEnabled"),
                config.autoplayEnabled
            )
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("§fAutomatically start playback after loading."),
                Component.literal(""),
                Component.literal("§aEnabled: §7Music starts when ready"),
                Component.literal("§cDisabled: §7Must click controller to play")
            )
            .setSaveConsumer(value -> config.setAutoplayEnabled(value))
            .build()
        );

        return builder.build();
    }

    /**
     * Capitalize the first letter of a string for display purposes.
     */
    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

