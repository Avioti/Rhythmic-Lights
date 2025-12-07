package com.rhythm.particle;

import com.rhythm.RhythmMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

/**
 * Custom particle effect with RGB color support for RhythmMod particles.
 * Allows coloring any particle texture with custom RGB values.
 */
public class ColoredParticleEffect implements ParticleOptions {

    private final Vector3f color;
    private final int maxAge;
    private final String particleTexture;
    private final float scale;
    private final boolean glow;

    public ColoredParticleEffect(Vector3f color, int maxAge, String particleTexture, float scale, boolean glow) {
        this.color = color;
        this.maxAge = maxAge;
        this.particleTexture = particleTexture;
        this.scale = scale;
        this.glow = glow;
    }

    public ColoredParticleEffect(int hexColor, String particleTexture) {
        this(hexToVector3f(hexColor), 60, particleTexture, 1.5f, true);
    }

    public ColoredParticleEffect(int hexColor, int maxAge, String particleTexture, float scale, boolean glow) {
        this(hexToVector3f(hexColor), maxAge, particleTexture, scale, glow);
    }

    private static Vector3f hexToVector3f(int hex) {
        return new Vector3f(
            ((hex >> 16) & 0xFF) / 255.0f,
            ((hex >> 8) & 0xFF) / 255.0f,
            (hex & 0xFF) / 255.0f
        );
    }

    public Vector3f getColor() {
        return color;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public String getParticleTexture() {
        return particleTexture;
    }

    public float getScale() {
        return scale;
    }

    public boolean isGlowing() {
        return glow;
    }

    @Override
    public ParticleType<?> getType() {
        return RhythmMod.COLORED_PARTICLE.get();
    }

    public void writeToNetwork(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(color.x);
        buf.writeFloat(color.y);
        buf.writeFloat(color.z);
        buf.writeInt(maxAge);
        buf.writeUtf(particleTexture);
        buf.writeFloat(scale);
        buf.writeBoolean(glow);
    }

    public static ColoredParticleEffect fromNetwork(RegistryFriendlyByteBuf buf) {
        float r = buf.readFloat();
        float g = buf.readFloat();
        float b = buf.readFloat();
        int age = buf.readInt();
        String texture = buf.readUtf();
        float scale = buf.readFloat();
        boolean glow = buf.readBoolean();
        return new ColoredParticleEffect(new Vector3f(r, g, b), age, texture, scale, glow);
    }

    public String writeToString() {
        return String.format("%.2f %.2f %.2f %d %s %.2f %b",
            color.x, color.y, color.z, maxAge, particleTexture, scale, glow);
    }

    public static ColoredParticleEffect white() {
        return new ColoredParticleEffect(0xFFFFFF, "portal");
    }
}

