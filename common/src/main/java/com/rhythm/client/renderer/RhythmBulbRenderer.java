package com.rhythm.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rhythm.RhythmMod;
import com.rhythm.audio.ClientSongManager;
import com.rhythm.audio.FrequencyChannel;
import com.rhythm.audio.FrequencyData;
import com.rhythm.audio.PlaybackState;
import com.rhythm.block.RhythmBulbBlockEntity;
import com.rhythm.block.RhythmControllerBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

/**
 * Renderer for Rhythm Bulbs with multi-channel frequency support.
 * <p>
 * Features beat-synced glow with threshold-based on/off toggle.
 */
public class RhythmBulbRenderer implements BlockEntityRenderer<RhythmBulbBlockEntity> {

    // ==================== Thresholds ====================

    private static final float BEAT_THRESHOLD = 0.08f;
    private static final float GLOW_INTENSITY_THRESHOLD = 0.3f;
    private static final float LIT_BRIGHTNESS_THRESHOLD = 0.5f;

    // ==================== Scale & Pulse ====================

    private static final float BASE_SCALE = 0.3f;
    private static final float MAX_PULSE = 0.25f;
    private static final float GLOW_SCALE = 1.35f;
    private static final float CENTER_OFFSET = 0.5f;

    // ==================== Colors ====================

    private static final float COLOR_NORMALIZE = 255.0f;
    private static final int COLOR_MASK = 0xFF;
    private static final float COLOR_BOOST = 1.2f;
    private static final float WHITE_BOOST = 0.2f;
    private static final float GLOW_MULTIPLIER = 2.2f;
    private static final float SATURATION_BOOST = 1.4f;
    private static final float GLOW_ALPHA_FACTOR = 0.75f;
    private static final float AVG_DIVISOR = 3.0f;

    // ==================== Lighting ====================

    private static final int MAX_BLOCK_LIGHT = 15;
    private static final int DIM_BLOCK_LIGHT = 8;
    private static final int VIEW_DISTANCE = 64;
    private static final int FREQUENCY_CHANNEL_COUNT = 12;

    // ==================== Constructor ====================

    public RhythmBulbRenderer(BlockEntityRendererProvider.Context context) {
        // Context parameter required by BlockEntityRenderer interface
    }

    // ==================== Main Render ====================

    @Override
    public void render(RhythmBulbBlockEntity entity, float tickDelta, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay) {
        if (entity.getLevel() == null) {
            renderBulb(poseStack, bufferSource, light, overlay, 0.0f, entity);
            return;
        }

        RenderContext ctx = buildRenderContext(entity, tickDelta);
        if (ctx == null) {
            renderBulb(poseStack, bufferSource, light, overlay, 0.0f, entity);
            return;
        }

        int finalLight = calculateFinalLight(ctx.intensity, light);
        renderBulb(poseStack, bufferSource, finalLight, overlay, ctx.intensity, entity);
    }

    // ==================== Context Building ====================

    private record RenderContext(float intensity) {}

    private RenderContext buildRenderContext(RhythmBulbBlockEntity entity, float tickDelta) {
        BlockPos controllerPos = entity.getControllerPos();
        if (controllerPos == null) {
            return null;
        }

        RhythmControllerBlockEntity controller = getController(entity, controllerPos);
        if (controller == null) {
            return null;
        }

        BlockPos jukeboxPos = controller.getJukeboxPos();
        if (jukeboxPos == null) {
            return null;
        }

        if (!isPlaying(jukeboxPos)) {
            entity.updateBrightness(0.0f, tickDelta);
            return null;
        }

        FrequencyData freqData = ClientSongManager.getInstance().getFrequencyData(jukeboxPos);
        if (freqData == null || freqData.isLoading()) {
            return null;
        }

        float intensity = calculateIntensity(entity, freqData, jukeboxPos, tickDelta);
        return new RenderContext(intensity);
    }

    private RhythmControllerBlockEntity getController(RhythmBulbBlockEntity entity, BlockPos controllerPos) {
        var blockEntity = entity.getLevel().getBlockEntity(controllerPos);
        return blockEntity instanceof RhythmControllerBlockEntity controller ? controller : null;
    }

    private boolean isPlaying(BlockPos jukeboxPos) {
        return ClientSongManager.getInstance().getState(jukeboxPos) == PlaybackState.PLAYING;
    }

    // ==================== Intensity Calculation ====================

    private float calculateIntensity(RhythmBulbBlockEntity entity, FrequencyData freqData,
                                      BlockPos jukeboxPos, float tickDelta) {
        long ticksSinceSongStart = calculateTicksSinceSongStart(entity, freqData, jukeboxPos);

        if (isOutOfBounds(ticksSinceSongStart, freqData)) {
            entity.updateBrightness(0.0f, tickDelta);
            return 0.0f;
        }

        float rawIntensity = getChannelIntensity(freqData, ticksSinceSongStart, entity.getChannel());
        float toggledIntensity = rawIntensity > BEAT_THRESHOLD ? 1.0f : 0.0f;

        entity.updateBrightness(toggledIntensity, tickDelta);
        return entity.getCurrentBrightness();
    }

    private long calculateTicksSinceSongStart(RhythmBulbBlockEntity entity, FrequencyData freqData, BlockPos jukeboxPos) {
        Long playbackStartTime = ClientSongManager.getInstance().getPlaybackStartTime(jukeboxPos);
        if (playbackStartTime == null) {
            playbackStartTime = freqData.getStartTime();
        }
        return entity.getLevel().getGameTime() - playbackStartTime;
    }

    private boolean isOutOfBounds(long ticksSinceSongStart, FrequencyData freqData) {
        return ticksSinceSongStart < 0 || ticksSinceSongStart >= freqData.getDurationTicks();
    }

    private int calculateFinalLight(float intensity, int light) {
        int blockLight = intensity > LIT_BRIGHTNESS_THRESHOLD ? MAX_BLOCK_LIGHT : DIM_BLOCK_LIGHT;
        int skyLight = LightTexture.sky(light);
        return LightTexture.pack(blockLight, skyLight);
    }

    // ==================== Channel Intensity ====================

    private float getChannelIntensity(FrequencyData data, long tickOffset, FrequencyChannel channel) {
        return switch (channel) {
            case SUB_BASS -> data.getSubBassIntensity(tickOffset);
            case DEEP_BASS -> data.getDeepBassIntensity(tickOffset);
            case BASS -> data.getBassIntensity(tickOffset);
            case LOW_MIDS -> data.getLowMidIntensity(tickOffset);
            case MID_LOWS -> data.getMidLowIntensity(tickOffset);
            case MIDS -> data.getMidIntensity(tickOffset);
            case MID_HIGHS -> data.getMidHighIntensity(tickOffset);
            case HIGH_MIDS -> data.getHighMidIntensity(tickOffset);
            case HIGHS -> data.getHighIntensity(tickOffset);
            case VERY_HIGHS -> data.getVeryHighIntensity(tickOffset);
            case ULTRA -> data.getUltraIntensity(tickOffset);
            case TOP -> data.getTopIntensity(tickOffset);
            case ALL -> calculateAverageIntensity(data, tickOffset);
        };
    }

    private float calculateAverageIntensity(FrequencyData data, long tickOffset) {
        float total = data.getSubBassIntensity(tickOffset) + data.getDeepBassIntensity(tickOffset) +
                      data.getBassIntensity(tickOffset) + data.getLowMidIntensity(tickOffset) +
                      data.getMidLowIntensity(tickOffset) + data.getMidIntensity(tickOffset) +
                      data.getMidHighIntensity(tickOffset) + data.getHighMidIntensity(tickOffset) +
                      data.getHighIntensity(tickOffset) + data.getVeryHighIntensity(tickOffset) +
                      data.getUltraIntensity(tickOffset) + data.getTopIntensity(tickOffset);
        return total / FREQUENCY_CHANNEL_COUNT;
    }

    // ==================== Bulb Rendering ====================

    private void renderBulb(PoseStack poseStack, MultiBufferSource bufferSource,
                           int light, int overlay, float intensity, RhythmBulbBlockEntity entity) {
        poseStack.pushPose();

        applyPulseScale(poseStack, intensity);
        float[] rgb = calculateBulbColor(entity.getColor(), intensity);

        renderSolidCore(poseStack, bufferSource, light, overlay, rgb);
        renderGlowLayer(poseStack, bufferSource, overlay, intensity, rgb);

        poseStack.popPose();
    }

    private void applyPulseScale(PoseStack poseStack, float intensity) {
        float scale = BASE_SCALE + (intensity * MAX_PULSE);
        poseStack.translate(CENTER_OFFSET, CENTER_OFFSET, CENTER_OFFSET);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-CENTER_OFFSET, -CENTER_OFFSET, -CENTER_OFFSET);
    }

    private float[] calculateBulbColor(int colorRGB, float intensity) {
        float baseR = ((colorRGB >> 16) & COLOR_MASK) / COLOR_NORMALIZE;
        float baseG = ((colorRGB >> 8) & COLOR_MASK) / COLOR_NORMALIZE;
        float baseB = (colorRGB & COLOR_MASK) / COLOR_NORMALIZE;

        if (intensity > LIT_BRIGHTNESS_THRESHOLD) {
            return new float[] {
                Math.min(1.0f, baseR * COLOR_BOOST + WHITE_BOOST),
                Math.min(1.0f, baseG * COLOR_BOOST + WHITE_BOOST),
                Math.min(1.0f, baseB * COLOR_BOOST + WHITE_BOOST)
            };
        }
        return new float[] { 0.0f, 0.0f, 0.0f };
    }

    private void renderSolidCore(PoseStack poseStack, MultiBufferSource bufferSource,
                                  int light, int overlay, float[] rgb) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        Matrix4f matrix = poseStack.last().pose();
        renderCube(consumer, matrix, rgb[0], rgb[1], rgb[2], 1.0f, light, overlay);
    }

    private void renderGlowLayer(PoseStack poseStack, MultiBufferSource bufferSource,
                                  int overlay, float intensity, float[] rgb) {
        if (intensity <= GLOW_INTENSITY_THRESHOLD) {
            return;
        }

        // Check if colored shader lighting is disabled in config
        if (!RhythmMod.CONFIG.isColoredShaderLightEnabled()) {
            return;
        }

        poseStack.pushPose();
        applyGlowScale(poseStack);

        float[] glowRgb = calculateGlowColor(rgb);
        float glowAlpha = intensity * GLOW_ALPHA_FACTOR;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        Matrix4f matrix = poseStack.last().pose();
        renderCube(consumer, matrix, glowRgb[0], glowRgb[1], glowRgb[2], glowAlpha,
                  LightTexture.pack(MAX_BLOCK_LIGHT, MAX_BLOCK_LIGHT), overlay);

        poseStack.popPose();
    }

    private void applyGlowScale(PoseStack poseStack) {
        poseStack.translate(CENTER_OFFSET, CENTER_OFFSET, CENTER_OFFSET);
        poseStack.scale(GLOW_SCALE, GLOW_SCALE, GLOW_SCALE);
        poseStack.translate(-CENTER_OFFSET, -CENTER_OFFSET, -CENTER_OFFSET);
    }

    private float[] calculateGlowColor(float[] baseRgb) {
        float glowR = Math.min(1.0f, baseRgb[0] * GLOW_MULTIPLIER);
        float glowG = Math.min(1.0f, baseRgb[1] * GLOW_MULTIPLIER);
        float glowB = Math.min(1.0f, baseRgb[2] * GLOW_MULTIPLIER);

        float avg = (glowR + glowG + glowB) / AVG_DIVISOR;
        glowR = clampColor(avg + (glowR - avg) * SATURATION_BOOST);
        glowG = clampColor(avg + (glowG - avg) * SATURATION_BOOST);
        glowB = clampColor(avg + (glowB - avg) * SATURATION_BOOST);

        return new float[] { glowR, glowG, glowB };
    }

    private float clampColor(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    // ==================== Cube Rendering ====================

    private void renderCube(VertexConsumer consumer, Matrix4f matrix,
                           float r, float g, float b, float a, int light, int overlay) {
        renderQuad(consumer, matrix, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, r, g, b, a, light, overlay);
        renderQuad(consumer, matrix, 1, 0, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, r, g, b, a, light, overlay);
        renderQuad(consumer, matrix, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, r, g, b, a, light, overlay);
        renderQuad(consumer, matrix, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, r, g, b, a, light, overlay);
        renderQuad(consumer, matrix, 0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, r, g, b, a, light, overlay);
        renderQuad(consumer, matrix, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, r, g, b, a, light, overlay);
    }

    private void renderQuad(VertexConsumer consumer, Matrix4f matrix,
                           float x1, float y1, float z1, float x2, float y2, float z2,
                           float x3, float y3, float z3, float x4, float y4, float z4,
                           float r, float g, float b, float a, int light, int overlay) {
        float[] normal = calculateNormal(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(normal[0], normal[1], normal[2]);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(normal[0], normal[1], normal[2]);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(normal[0], normal[1], normal[2]);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(normal[0], normal[1], normal[2]);
    }

    private float[] calculateNormal(float x1, float y1, float z1, float x2, float y2, float z2,
                                     float x3, float y3, float z3, float x4, float y4, float z4) {
        float nx = 0, ny = 0, nz = 0;
        if (x1 == x2 && x2 == x3 && x3 == x4) nx = x1 < CENTER_OFFSET ? -1 : 1;
        else if (y1 == y2 && y2 == y3 && y3 == y4) ny = y1 < CENTER_OFFSET ? -1 : 1;
        else if (z1 == z2 && z2 == z3 && z3 == z4) nz = z1 < CENTER_OFFSET ? -1 : 1;
        return new float[] { nx, ny, nz };
    }

    // ==================== View Distance ====================

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}

