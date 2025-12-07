package com.rhythm.client.light;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Rendering pipeline for colored lights using Shimmer-style post-processing.
 * <p>
 * SIMPLIFIED VERSION: Currently renders lights directly to screen with additive blending.
 * The lights create a glow effect by rendering semi-transparent colored cubes at the bulb positions.
 */
public class ColoredLightPipeline {

    // ==================== Constants ====================

    private static final double LIGHT_SEARCH_RADIUS = 64.0;
    private static final float MIN_VISIBLE_INTENSITY = 0.05f;
    private static final float BLOCK_CENTER_OFFSET = 0.5f;

    // ==================== Color Processing ====================

    private static final float SATURATION_BOOST = 1.5f;
    private static final float COLOR_MULTIPLIER = 4.5f;
    private static final float COLOR_DIVISOR = 3.0f;

    // ==================== Glow Rendering ====================

    private static final int GLOW_PASS_COUNT = 4;
    private static final float BASE_GLOW_SCALE = 0.5f;
    private static final float GLOW_SCALE_INCREMENT = 0.25f;
    private static final float BASE_GLOW_ALPHA = 0.85f;
    private static final float GLOW_ALPHA_DECREMENT = 0.15f;

    // ==================== Singleton ====================

    private static ColoredLightPipeline instance;

    private boolean isInitialized = false;

    private ColoredLightPipeline() {}

    public static ColoredLightPipeline getInstance() {
        if (instance == null) {
            instance = new ColoredLightPipeline();
        }
        return instance;
    }

    // ==================== Lifecycle ====================

    public void initialize() {
        isInitialized = true;
        if (RhythmConstants.DEBUG_LIGHTS) {
            RhythmConstants.LOGGER.info("ColoredLight pipeline initialized (simplified mode)");
        }
    }

    public void destroy() {
        isInitialized = false;
        if (RhythmConstants.DEBUG_LIGHTS) {
            RhythmConstants.LOGGER.info("ColoredLight pipeline destroyed");
        }
    }

    // ==================== Main Render ====================

    public void render(PoseStack poseStack, Matrix4f projectionMatrix, Camera camera) {
        if (!isInitialized) {
            return;
        }

        try {
            renderNearbyLights(poseStack, camera);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("ColoredLight pipeline render error: {}", e.getMessage());
        }
    }

    private void renderNearbyLights(PoseStack poseStack, Camera camera) {
        List<ColoredLightSource> lights = ColoredLightRegistry.getInstance()
            .getLightsNear(camera.getBlockPosition(), LIGHT_SEARCH_RADIUS);

        if (lights.isEmpty()) {
            return;
        }

        setupRenderState();
        renderAllLights(poseStack, camera.getPosition(), lights);
        restoreRenderState();
    }

    // ==================== Render State ====================

    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private void restoreRenderState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ==================== Light Rendering ====================

    private void renderAllLights(PoseStack poseStack, Vec3 camPos, List<ColoredLightSource> lights) {
        Tesselator tesselator = Tesselator.getInstance();

        for (ColoredLightSource light : lights) {
            if (light.getIntensity() < MIN_VISIBLE_INTENSITY) {
                continue;
            }
            renderSingleLight(tesselator, poseStack, camPos, light);
        }
    }

    private void renderSingleLight(Tesselator tesselator, PoseStack poseStack, Vec3 camPos, ColoredLightSource light) {
        float intensity = light.getIntensity();
        float[] rgb = calculateVibrantColor(light, intensity);

        for (int pass = 0; pass < GLOW_PASS_COUNT; pass++) {
            float scale = BASE_GLOW_SCALE + (pass * GLOW_SCALE_INCREMENT);
            float alpha = intensity * (BASE_GLOW_ALPHA - pass * GLOW_ALPHA_DECREMENT);
            renderLightCube(tesselator, poseStack, light.getPosition(), camPos, rgb[0], rgb[1], rgb[2], alpha, scale);
        }
    }

    private float[] calculateVibrantColor(ColoredLightSource light, float intensity) {
        float baseR = light.getRedF();
        float baseG = light.getGreenF();
        float baseB = light.getBlueF();

        float avg = (baseR + baseG + baseB) / COLOR_DIVISOR;
        baseR = clampColor(avg + (baseR - avg) * SATURATION_BOOST);
        baseG = clampColor(avg + (baseG - avg) * SATURATION_BOOST);
        baseB = clampColor(avg + (baseB - avg) * SATURATION_BOOST);

        return new float[] {
            baseR * intensity * COLOR_MULTIPLIER,
            baseG * intensity * COLOR_MULTIPLIER,
            baseB * intensity * COLOR_MULTIPLIER
        };
    }

    private float clampColor(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    // ==================== Cube Rendering ====================

    private void renderLightCube(Tesselator tesselator, PoseStack poseStack, BlockPos pos, Vec3 camPos,
                                  float r, float g, float b, float alpha, float scale) {
        poseStack.pushPose();
        translateToCameraRelativePosition(poseStack, pos, camPos);

        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        float half = scale / 2;
        renderAllCubeFaces(buffer, matrix, -half, -half, -half, scale, r, g, b, alpha);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poseStack.popPose();
    }

    private void translateToCameraRelativePosition(PoseStack poseStack, BlockPos pos, Vec3 camPos) {
        double relX = pos.getX() - camPos.x + BLOCK_CENTER_OFFSET;
        double relY = pos.getY() - camPos.y + BLOCK_CENTER_OFFSET;
        double relZ = pos.getZ() - camPos.z + BLOCK_CENTER_OFFSET;
        poseStack.translate(relX, relY, relZ);
    }

    private void renderAllCubeFaces(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                     float scale, float r, float g, float b, float alpha) {
        renderFrontFace(buffer, matrix, x, y, z, scale, r, g, b, alpha);
        renderBackFace(buffer, matrix, x, y, z, scale, r, g, b, alpha);
        renderLeftFace(buffer, matrix, x, y, z, scale, r, g, b, alpha);
        renderRightFace(buffer, matrix, x, y, z, scale, r, g, b, alpha);
        renderTopFace(buffer, matrix, x, y, z, scale, r, g, b, alpha);
        renderBottomFace(buffer, matrix, x, y, z, scale, r, g, b, alpha);
    }

    // ==================== Individual Face Rendering ====================

    private void renderFrontFace(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                  float scale, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x, y, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y + scale, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y + scale, z + scale).setColor(r, g, b, alpha);
    }

    private void renderBackFace(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                 float scale, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y + scale, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y + scale, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y, z).setColor(r, g, b, alpha);
    }

    private void renderLeftFace(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                 float scale, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y + scale, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y + scale, z).setColor(r, g, b, alpha);
    }

    private void renderRightFace(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                  float scale, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x + scale, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y + scale, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y + scale, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y, z + scale).setColor(r, g, b, alpha);
    }

    private void renderTopFace(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                float scale, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x, y + scale, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y + scale, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y + scale, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y + scale, z).setColor(r, g, b, alpha);
    }

    private void renderBottomFace(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                   float scale, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + scale, y, z + scale).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x, y, z + scale).setColor(r, g, b, alpha);
    }
}

