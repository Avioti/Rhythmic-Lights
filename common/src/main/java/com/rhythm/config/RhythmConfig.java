package com.rhythm.config;

import com.rhythm.audio.playback.SeekableAudioPlayer;
import com.rhythm.config.annotation.ConfigComment;
import com.rhythm.config.annotation.SyncToClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RhythmMod Configuration.
 * Manages gameplay settings and performance tuning.
 *
 * This is RhythmMod's integrated configuration system - no external dependencies required.
 * Config automatically syncs from server to client for fields marked with @SyncToClient.
 */
public class RhythmConfig {

    // ============================================================
    // VISUAL SETTINGS - Particle Effects
    // ============================================================

    @ConfigComment("""
        Particle texture used by rhythm lamps and bulbs.
        
        Built-in options:
        - flash: Bright, glowing particle with sharp edges (default)
        - portal: Swirling portal-like particle
        - flame: Fire-like particle
        - heart: Heart-shaped particle
        - note: Musical note particle
        - enchant: Enchantment table particle
        - snowflake: Snow particle
        - glow: Smooth glowing particle
        - soul: Soul flame particle
        
        You can also use any custom particle you add to customParticleTextures below.
        Custom particles are colored to match the frequency band.
        Default: flash""")
    @SyncToClient
    public String particleTexture = "flash";

    @ConfigComment("""
        Custom particle textures that will appear in the config screen dropdown.
        Add any Minecraft particle texture name here to make it selectable.
        
        Examples you can add:
        - "end_rod": End rod particles
        - "firework": Firework sparkle
        - "angry_villager": Angry villager particle
        - "happy_villager": Happy villager particle
        - "cloud": Cloud particle
        - "crit": Critical hit particle
        - "damage_indicator": Damage number particle
        - "dragon_breath": Dragon breath particle
        - "dripping_water": Dripping water
        - "electric_spark": Electric spark (1.21+)
        - "explosion": Explosion particle
        - "falling_dust": Falling dust particle
        - "fishing": Fishing bobber particle
        - "lava": Lava drip
        - "mycelium": Mycelium spore
        - "poof": Smoke puff
        - "rain": Rain particle
        - "smoke": Regular smoke
        - "splash": Water splash
        - "witch": Witch magic particle
        
        Just add the particle name (without "minecraft:" prefix) to this list.
        The config screen will automatically include these options.
        
        Default: [] (empty list)""")
    @SyncToClient
    public List<String> customParticleTextures = new ArrayList<>();

    @ConfigComment("""
        Particle size multiplier for rhythm lamps and bulbs.
        Controls how large the particles appear.
        
        Values:
        - 0.1: Tiny particles (subtle effect)
        - 0.3: Small particles (default, balanced)
        - 0.5: Medium particles (more visible)
        - 1.0: Normal size particles
        - 1.5: Large particles (dramatic effect)
        - 2.0: Very large particles (intense effect)
        - 3.0: Huge particles (maximum visual impact)
        
        Lower values = Better performance with many bulbs
        Higher values = More dramatic visual effect
        
        Default: 0.3""")
    @SyncToClient
    public float particleScale = 0.3f;

    @ConfigComment("""
        Use random colors for particles and block shaders instead of frequency-tuned colors.
        
        When enabled:
        - Particles will cycle through random RGB colors
        - Block shader colors will be randomized
        - Each bulb will have constantly changing colors
        - Creates a dynamic, disco-like effect
        
        When disabled (default):
        - Particles match the frequency channel's color
        - Sub-Bass = Red, Bass = Green, Mids = Purple, etc.
        - Consistent, color-coded visualization
        
        This affects all rhythm bulbs and lamps globally.
        Great for party/disco builds or visual variety.
        
        Default: false""")
    @SyncToClient
    public boolean useRandomColors = false;

    @ConfigComment("""
        Enable colored shader lighting around rhythm lamps and bulbs.
        
        When enabled (default):
        - Rhythm lamps emit colored light matching their frequency
        - Creates atmospheric lighting effects
        - Light color changes with music intensity
        - Uses block entity rendering for dynamic colors
        
        When disabled:
        - No colored light emission from rhythm blocks
        - Only particles are shown (if enabled)
        - Better performance on lower-end systems
        - Reduces visual clutter in dense setups
        
        Disable this if you experience performance issues
        or prefer a simpler visual style.
        
        Default: true""")
    @SyncToClient
    public boolean coloredShaderLightEnabled = true;

    // ============================================================
    // AUDIO SETTINGS - Spatial Audio
    // ============================================================

    @ConfigComment("""
        Maximum distance at which jukebox audio can be heard.
        Beyond this distance, the jukebox audio is completely silent.
        
        Values:
        - 16: Short range (intimate setups)
        - 24: Default range (balanced)
        - 32: Medium range (larger builds)
        - 48: Long range (large areas)
        - 64: Maximum range (entire area coverage)
        
        Higher values may impact performance with many jukeboxes.
        
        Default: 24""")
    public float maxAudioDistance = 24.0f;

    @ConfigComment("""
        Reference distance for audio volume.
        At this distance and closer, the jukebox plays at full volume.
        Volume decreases as you move beyond this distance.
        
        Values:
        - 2: Very close (must be near jukebox for full volume)
        - 4: Default (good for single room setups)
        - 8: Medium (fills a larger room at full volume)
        - 12: Wide (good for outdoor builds)
        
        Default: 4""")
    public float referenceDistance = 4.0f;

    @ConfigComment("""
        Rolloff factor controls how quickly volume decreases with distance.
        Higher values = faster volume drop-off.
        
        Values:
        - 0.5: Slow falloff (audio carries further)
        - 1.0: Realistic falloff (default)
        - 1.5: Fast falloff (audio stays localized)
        - 2.0: Very fast falloff (tight audio bubble)
        
        Default: 1.0""")
    public float rolloffFactor = 1.0f;

    @ConfigComment("""
        Enable spatial audio for jukeboxes.
        When enabled, jukebox volume is based on player distance.
        When disabled, jukeboxes play at full volume regardless of distance.
        
        Default: true""")
    public boolean spatialAudioEnabled = true;

    @ConfigComment("""
        Enable autoplay for jukeboxes.
        When enabled, music starts automatically after loading/downloading.
        When disabled, you must manually click the controller to start playback.
        
        Default: true""")
    public boolean autoplayEnabled = true;

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Get all available particle textures (built-in + custom).
     */
    public List<String> getAllParticleTextures() {
        List<String> all = new ArrayList<>(Arrays.asList(
            "flash", "portal", "flame", "heart", "note",
            "enchant", "snowflake", "glow", "soul"
        ));

        // Add custom particles if any are defined
        if (customParticleTextures != null && !customParticleTextures.isEmpty()) {
            for (String custom : customParticleTextures) {
                if (custom != null && !custom.trim().isEmpty()) {
                    String cleaned = custom.trim().toLowerCase();
                    if (!all.contains(cleaned)) {
                        all.add(cleaned);
                    }
                }
            }
        }

        return all;
    }

    /**
     * Get the configured particle texture, with validation.
     * Falls back to "flash" if the configured value is invalid.
     */
    public String getParticleTexture() {
        if (particleTexture == null || particleTexture.trim().isEmpty()) {
            return "flash";
        }
        return particleTexture.trim().toLowerCase();
    }

    /**
     * Get the configured particle scale, with validation.
     * Clamps to range 0.1 to 3.0 to prevent invalid values.
     */
    public float getParticleScale() {
        // Clamp to reasonable range
        if (particleScale < 0.1f) return 0.1f;
        if (particleScale > 3.0f) return 3.0f;
        return particleScale;
    }

    /**
     * Generate a random RGB color for particles/shaders.
     * Creates vibrant, saturated colors for better visibility.
     */
    public int generateRandomColor() {
        if (!useRandomColors) {
            return 0xFFFFFF; // Return white if random colors disabled
        }

        // Generate bright, saturated colors (avoid dark/muddy colors)
        java.util.Random random = new java.util.Random();

        // Use HSV color space to ensure vibrant colors
        // Hue: 0-360 (full spectrum)
        // Saturation: 0.7-1.0 (vibrant)
        // Value: 0.8-1.0 (bright)
        float hue = random.nextFloat();
        float saturation = 0.7f + (random.nextFloat() * 0.3f);
        float brightness = 0.8f + (random.nextFloat() * 0.2f);

        // Convert HSV to RGB
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);

        // Remove alpha channel (keep only RGB)
        return rgb & 0xFFFFFF;
    }

    /**
     * Get the color to use for a particle or shader.
     * Returns either the frequency color or a random color based on config.
     *
     * @param frequencyColor The default color for the frequency channel
     * @return The color to use (random if enabled, frequency color otherwise)
     */
    public int getParticleColor(int frequencyColor) {
        if (useRandomColors) {
            return generateRandomColor();
        }
        return frequencyColor;
    }

    /**
     * Check if colored shader lighting is enabled.
     *
     * @return true if colored light effects should render
     */
    public boolean isColoredShaderLightEnabled() {
        return coloredShaderLightEnabled;
    }

    /**
     * Set colored shader lighting enabled state.
     *
     * @param enabled true to enable colored light effects
     */
    public void setColoredShaderLightEnabled(boolean enabled) {
        this.coloredShaderLightEnabled = enabled;
    }

    /**
     * Save config to disk.
     */
    public void save() {
        ConfigManager.save(this, "rhythmmod-config");
    }

    // ============================================================
    // AUDIO SETTINGS HELPERS
    // ============================================================

    /**
     * Get the maximum audio distance, with validation.
     * Clamps to range 4 to 64 blocks.
     */
    public float getMaxAudioDistance() {
        return clamp(maxAudioDistance, 4.0f, 64.0f);
    }

    /**
     * Set the maximum audio distance and apply to audio system.
     */
    public void setMaxAudioDistance(float distance) {
        this.maxAudioDistance = clamp(distance, 4.0f, 64.0f);
        applyAudioSettings();
    }

    /**
     * Get the reference distance, with validation.
     * Clamps to range 1 to 16 blocks.
     */
    public float getReferenceDistance() {
        return clamp(referenceDistance, 1.0f, 40.0f);
    }

    /**
     * Set the reference distance and apply to audio system.
     */
    public void setReferenceDistance(float distance) {
        this.referenceDistance = clamp(distance, 1.0f, 40.0f);
        applyAudioSettings();
    }

    /**
     * Get the rolloff factor, with validation.
     * Clamps to range 0.1 to 3.0.
     */
    public float getRolloffFactor() {
        return clamp(rolloffFactor, 0.1f, 3.0f);
    }

    /**
     * Set the rolloff factor and apply to audio system.
     */
    public void setRolloffFactor(float factor) {
        this.rolloffFactor = clamp(factor, 0.1f, 3.0f);
        applyAudioSettings();
    }

    /**
     * Check if spatial audio is enabled.
     */
    public boolean isSpatialAudioEnabled() {
        return spatialAudioEnabled;
    }

    /**
     * Set spatial audio enabled state and apply.
     */
    public void setSpatialAudioEnabled(boolean enabled) {
        this.spatialAudioEnabled = enabled;
        applyAudioSettings();
    }

    /**
     * Check if autoplay is enabled.
     */
    public boolean isAutoplayEnabled() {
        return autoplayEnabled;
    }

    /**
     * Set autoplay enabled state and apply.
     */
    public void setAutoplayEnabled(boolean enabled) {
        this.autoplayEnabled = enabled;
        applyAudioSettings();
    }

    /**
     * Apply all audio settings to the audio system.
     * Call this after loading config or changing settings.
     */
    public void applyAudioSettings() {
        com.rhythm.audio.AudioSettings audioSettings = com.rhythm.audio.AudioSettings.getInstance();
        SeekableAudioPlayer audioPlayer = SeekableAudioPlayer.getInstance();

        // Apply spatial audio settings
        audioSettings.setMaxAudioDistance(getMaxAudioDistance());
        audioSettings.setReferenceDistance(getReferenceDistance());
        audioSettings.setRolloffFactor(getRolloffFactor());
        audioSettings.setSpatialAudioEnabled(spatialAudioEnabled);

        // Apply autoplay setting
        audioSettings.setAutoplayEnabled(autoplayEnabled);

        // Sync to audio player
        audioPlayer.setMaxDistance(getMaxAudioDistance());
        audioPlayer.setReferenceDistance(getReferenceDistance());
        audioPlayer.setRolloffFactor(getRolloffFactor());
    }

    /**
     * Clamp a float value to a range.
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

