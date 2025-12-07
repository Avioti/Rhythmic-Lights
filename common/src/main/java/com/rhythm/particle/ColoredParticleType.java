package com.rhythm.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

/**
 * Custom particle type that supports RGB color data.
 */
public class ColoredParticleType extends ParticleType<ColoredParticleEffect> {

    // ==================== Codec Field Names ====================

    private static final String FIELD_RED = "r";
    private static final String FIELD_GREEN = "g";
    private static final String FIELD_BLUE = "b";
    private static final String FIELD_MAX_AGE = "maxAge";
    private static final String FIELD_TEXTURE = "texture";
    private static final String FIELD_SCALE = "scale";
    private static final String FIELD_GLOW = "glow";

    // ==================== Default Values ====================

    private static final int DEFAULT_MAX_AGE = 60;
    private static final String DEFAULT_TEXTURE = "portal";
    private static final float DEFAULT_SCALE = 1.5f;
    private static final boolean DEFAULT_GLOW = true;

    // ==================== Constructor ====================

    public ColoredParticleType() {
        super(false);
    }

    // ==================== Codec ====================

    @Override
    public MapCodec<ColoredParticleEffect> codec() {
        return RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.FLOAT.fieldOf(FIELD_RED).forGetter(effect -> effect.getColor().x),
                Codec.FLOAT.fieldOf(FIELD_GREEN).forGetter(effect -> effect.getColor().y),
                Codec.FLOAT.fieldOf(FIELD_BLUE).forGetter(effect -> effect.getColor().z),
                Codec.INT.optionalFieldOf(FIELD_MAX_AGE, DEFAULT_MAX_AGE).forGetter(ColoredParticleEffect::getMaxAge),
                Codec.STRING.optionalFieldOf(FIELD_TEXTURE, DEFAULT_TEXTURE).forGetter(ColoredParticleEffect::getParticleTexture),
                Codec.FLOAT.optionalFieldOf(FIELD_SCALE, DEFAULT_SCALE).forGetter(ColoredParticleEffect::getScale),
                Codec.BOOL.optionalFieldOf(FIELD_GLOW, DEFAULT_GLOW).forGetter(ColoredParticleEffect::isGlowing)
            ).apply(instance, ColoredParticleType::createEffect)
        );
    }

    private static ColoredParticleEffect createEffect(float r, float g, float b,
                                                       int age, String tex, float scale, boolean glow) {
        return new ColoredParticleEffect(new Vector3f(r, g, b), age, tex, scale, glow);
    }

    // ==================== Stream Codec ====================

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, ColoredParticleEffect> streamCodec() {
        return StreamCodec.of(
            ColoredParticleType::encode,
            ColoredParticleType::decode
        );
    }

    private static void encode(RegistryFriendlyByteBuf buf, ColoredParticleEffect effect) {
        effect.writeToNetwork(buf);
    }

    private static ColoredParticleEffect decode(RegistryFriendlyByteBuf buf) {
        float r = buf.readFloat();
        float g = buf.readFloat();
        float b = buf.readFloat();
        int age = buf.readInt();
        String texture = buf.readUtf();
        float scale = buf.readFloat();
        boolean glow = buf.readBoolean();

        return new ColoredParticleEffect(new Vector3f(r, g, b), age, texture, scale, glow);
    }
}

