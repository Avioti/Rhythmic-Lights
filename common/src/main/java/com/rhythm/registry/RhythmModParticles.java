package com.rhythm.registry;

import com.rhythm.RhythmMod;
import com.rhythm.particle.ColoredParticleEffect;
import com.rhythm.particle.ColoredParticleType;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;

/**
 * Particle registry for RhythmMod.
 */
public final class RhythmModParticles {

    private RhythmModParticles() {}

    // ==================== Registry ====================

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(RhythmMod.MOD_ID, Registries.PARTICLE_TYPE);

    // ==================== Particles ====================

    public static final RegistrySupplier<ParticleType<ColoredParticleEffect>> COLORED_PARTICLE =
        PARTICLES.register("colored_particle", ColoredParticleType::new);

    // ==================== Registration ====================

    public static void register() {
        PARTICLES.register();
    }
}

