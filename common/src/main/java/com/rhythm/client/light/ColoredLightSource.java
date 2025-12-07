package com.rhythm.client.light;

import net.minecraft.core.BlockPos;

/**
 * Represents a single colored light source in the world
 * Based on Shimmer mod's ColorPointLight architecture
 * <p>
 * This class is actively used by ColoredLightPipeline and RhythmBulbRenderer
 * for beat-synced colored lighting effects.
 */
public class ColoredLightSource {
    private final BlockPos position;
    private final int color; // RGB packed (0xRRGGBB)
    private final float radius;
    private float intensity; // 0.0 to 1.0, for beat-synced animations

    public ColoredLightSource(BlockPos position, int color, float radius, float intensity) {
        this.position = position;
        this.color = color;
        this.radius = radius;
        this.intensity = Math.max(0.0f, Math.min(1.0f, intensity));
    }

    public BlockPos getPosition() {
        return position;
    }

    public int getColor() {
        return color;
    }

    public float getRadius() {
        return radius;
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = Math.max(0.0f, Math.min(1.0f, intensity));
    }

    /**
     * Get RGB components as normalized floats (0.0-1.0)
     */
    public float getRedF() {
        return ((color >> 16) & 0xFF) / 255.0f;
    }

    public float getGreenF() {
        return ((color >> 8) & 0xFF) / 255.0f;
    }

    public float getBlueF() {
        return (color & 0xFF) / 255.0f;
    }

    /**
     * Calculate light contribution at a given position
     * Uses inverse square falloff with intensity modifier
     */
    public float[] calculateLightAt(BlockPos targetPos) {
        double distSq = position.distSqr(targetPos);
        double radiusSq = radius * radius;

        // Inverse square falloff
        float attenuation = (float) Math.max(0.0, 1.0 - (distSq / radiusSq));
        attenuation = attenuation * attenuation; // Square for smoother falloff
        attenuation *= intensity; // Apply intensity

        return new float[] {
            getRedF() * attenuation,
            getGreenF() * attenuation,
            getBlueF() * attenuation
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ColoredLightSource other)) return false;
        return position.equals(other.position);
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }
}

