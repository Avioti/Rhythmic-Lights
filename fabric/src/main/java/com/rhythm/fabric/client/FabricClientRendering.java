package com.rhythm.fabric.client;

import com.rhythm.client.light.ColoredLightPipeline;
import com.rhythm.client.particle.ColoredParticleFactory;
import com.rhythm.client.renderer.RhythmBulbRenderer;
import com.rhythm.registry.RhythmModBlockEntities;
import com.rhythm.registry.RhythmModBlocks;
import com.rhythm.registry.RhythmModParticles;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * Registers client-side rendering components for Fabric.
 */
public final class FabricClientRendering {

    private FabricClientRendering() {}

    // ==================== Registration ====================

    public static void register() {
        initializePipeline();
        registerRenderLayers();
        registerBlockEntityRenderers();
        registerParticleFactories();
    }

    // ==================== Pipeline ====================

    private static void initializePipeline() {
        ColoredLightPipeline.getInstance().initialize();
    }

    // ==================== Render Layers ====================

    private static void registerRenderLayers() {
        BlockRenderLayerMap.INSTANCE.putBlock(
            RhythmModBlocks.RHYTHM_BULB.get(),
            RenderType.translucent()
        );
    }

    // ==================== Block Entity Renderers ====================

    private static void registerBlockEntityRenderers() {
        BlockEntityRenderers.register(
            RhythmModBlockEntities.RHYTHM_BULB.get(),
            RhythmBulbRenderer::new
        );
    }

    // ==================== Particle Factories ====================

    private static void registerParticleFactories() {
        ParticleFactoryRegistry.getInstance().register(
            RhythmModParticles.COLORED_PARTICLE.get(),
            ColoredParticleFactory::new
        );
    }
}

