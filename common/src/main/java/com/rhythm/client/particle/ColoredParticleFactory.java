package com.rhythm.client.particle;

import com.rhythm.particle.ColoredParticleEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Client-side factory that creates colored particles with RGB tinting.
 * <p>
 * Supports any vanilla particle texture with custom color overlay.
 */
public class ColoredParticleFactory implements ParticleProvider<ColoredParticleEffect> {

    // ==================== Reflection Constants ====================

    private static final String[] SPRITE_SETS_FIELD_NAMES = {"spriteSets", "field_3832", "f_107315_"};
    private static final String RHYTHMMOD_NAMESPACE = "rhythmmod";

    // ==================== State ====================

    private final SpriteSet fallbackSpriteSet;
    private static Field spriteSetsField = null;

    // ==================== Constructor ====================

    public ColoredParticleFactory(SpriteSet spriteSet) {
        this.fallbackSpriteSet = spriteSet;
    }

    // ==================== Particle Creation ====================

    @Override
    public Particle createParticle(ColoredParticleEffect effect, ClientLevel level,
                                    double x, double y, double z,
                                    double dx, double dy, double dz) {
        SpriteSet targetSpriteSet = resolveTargetSpriteSet(effect.getParticleTexture());
        ColoredParticle particle = new ColoredParticle(level, x, y, z, dx, dy, dz);

        applyEffectProperties(particle, effect);
        particle.pickSprite(targetSpriteSet);

        return particle;
    }

    private SpriteSet resolveTargetSpriteSet(String particleTexture) {
        SpriteSet spriteSet = getSpriteSetForParticle(particleTexture);
        return spriteSet != null ? spriteSet : fallbackSpriteSet;
    }

    private void applyEffectProperties(ColoredParticle particle, ColoredParticleEffect effect) {
        particle.setColor(effect.getColor().x, effect.getColor().y, effect.getColor().z);

        if (effect.getMaxAge() > 0) {
            particle.setLifetime(effect.getMaxAge());
        }

        particle.setScale(effect.getScale());
        particle.setGlowing(effect.isGlowing());
    }

    // ==================== Sprite Set Resolution ====================

    private SpriteSet getSpriteSetForParticle(String particleName) {
        try {
            ParticleEngine engine = Minecraft.getInstance().particleEngine;
            initSpriteSetsField();

            if (spriteSetsField == null) {
                return null;
            }

            return lookupSpriteSet(particleName, engine);
        } catch (Exception e) {
            return null;
        }
    }

    private void initSpriteSetsField() {
        if (spriteSetsField != null) {
            return;
        }

        for (String fieldName : SPRITE_SETS_FIELD_NAMES) {
            try {
                spriteSetsField = ParticleEngine.class.getDeclaredField(fieldName);
                spriteSetsField.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
    }

    private static SpriteSet lookupSpriteSet(String particleName, ParticleEngine engine) throws IllegalAccessException {
        @SuppressWarnings("unchecked")
        Map<ResourceLocation, SpriteSet> spriteSets = (Map<ResourceLocation, SpriteSet>) spriteSetsField.get(engine);

        ResourceLocation particleId = parseParticleId(particleName);
        SpriteSet spriteSet = spriteSets.get(particleId);

        // Fallback to rhythmmod namespace if not found
        if (spriteSet == null && !particleName.contains(":")) {
            spriteSet = spriteSets.get(ResourceLocation.fromNamespaceAndPath(RHYTHMMOD_NAMESPACE, particleName));
        }

        return spriteSet;
    }

    /**
     * Parses a particle name into a ResourceLocation.
     * Supports formats: "particle_name" (defaults to minecraft) or "modid:particle_name"
     */
    private static ResourceLocation parseParticleId(String particleName) {
        if (particleName.contains(":")) {
            String[] parts = particleName.split(":", 2);
            return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
        }
        return ResourceLocation.withDefaultNamespace(particleName);
    }

    // ==================== Colored Particle ====================

    /**
     * Custom particle that supports RGB tinting and optional glow effect.
     */
    private static class ColoredParticle extends TextureSheetParticle {

        private static final float BASE_QUAD_SIZE = 0.15F;
        private static final float RANDOM_SCALE_MIN = 0.5F;
        private static final float RANDOM_SCALE_RANGE = 0.5F;
        private static final int DEFAULT_LIFETIME = 60;
        private static final double VELOCITY_DECAY = 0.96;
        private static final double GRAVITY = 0.01;
        private static final int MAX_LIGHT_LEVEL = 15728880;

        private boolean isGlowing = false;

        protected ColoredParticle(ClientLevel level, double x, double y, double z,
                                   double dx, double dy, double dz) {
            super(level, x, y, z, dx, dy, dz);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = DEFAULT_LIFETIME;
            this.quadSize = calculateRandomQuadSize(1.0f);
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
        }

        public void setScale(float scale) {
            this.quadSize = calculateRandomQuadSize(scale);
        }

        public void setGlowing(boolean glowing) {
            this.isGlowing = glowing;
        }

        public void setLifetime(int lifetime) {
            this.lifetime = lifetime;
        }

        private float calculateRandomQuadSize(float scale) {
            return BASE_QUAD_SIZE * (this.random.nextFloat() * RANDOM_SCALE_RANGE + RANDOM_SCALE_MIN) * scale;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return isGlowing ? ParticleRenderType.PARTICLE_SHEET_LIT : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public int getLightColor(float partialTick) {
            return isGlowing ? MAX_LIGHT_LEVEL : super.getLightColor(partialTick);
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;

            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }

            updateMovement();
            updateAlpha();
        }

        private void updateMovement() {
            this.move(this.xd, this.yd, this.zd);
            this.xd *= VELOCITY_DECAY;
            this.yd *= VELOCITY_DECAY;
            this.zd *= VELOCITY_DECAY;
            this.yd -= GRAVITY;
        }

        private void updateAlpha() {
            this.alpha = 1.0F - ((float) this.age / (float) this.lifetime);
        }
    }
}

