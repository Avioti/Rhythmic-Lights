package com.rhythm.sync;

import com.rhythm.audio.ClientSongManager;
import com.rhythm.audio.FrequencyChannel;
import com.rhythm.audio.FrequencyData;
import com.rhythm.audio.PlaybackState;
import com.rhythm.block.RhythmControllerBlockEntity;
import com.rhythm.client.light.ColoredLightRegistry;
import com.rhythm.particle.ColoredParticleEffect;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages rhythm synchronization for vanilla light-emitting blocks.
 */
public class VanillaLightSyncManager {

    // ==================== Singleton ====================

    private static final VanillaLightSyncManager INSTANCE = new VanillaLightSyncManager();

    public static VanillaLightSyncManager getInstance() {
        return INSTANCE;
    }

    private VanillaLightSyncManager() {}

    // ==================== Constants ====================

    private static final String COPPER_BULB_ID = "copper_bulb";

    private static final float BRIGHTNESS_LERP_SPEED = 0.3f;
    private static final float PARTICLE_THRESHOLD = 0.08f;
    private static final float LIGHT_ON_THRESHOLD = 0.1f;

    private static final int REDSTONE_LAMP_LIGHT_LEVEL = 15;
    private static final int CAMPFIRE_LIGHT_LEVEL = 15;
    private static final int CANDLE_MAX_LIGHT_LEVEL = 12;
    private static final int COPPER_BULB_LIGHT_LEVEL = 15;

    private static final int DEBUG_LOG_INTERVAL_TICKS = 100;

    private static final int PARTICLE_LIFETIME_BASE = 10;
    private static final int PARTICLE_LIFETIME_INTENSITY_FACTOR = 6;
    private static final float PARTICLE_SCALE = 0.3f;
    private static final String PARTICLE_TEXTURE = "flash";

    private static final float PARTICLE_INTENSITY_HIGH_THRESHOLD = 0.5f;
    private static final float PARTICLE_INTENSITY_MEDIUM_THRESHOLD = 0.3f;
    private static final int PARTICLE_COUNT_HIGH = 4;
    private static final int PARTICLE_COUNT_MEDIUM = 3;
    private static final int PARTICLE_COUNT_LOW = 2;
    private static final int PARTICLE_COUNT_INTENSITY_FACTOR = 3;

    private static final double PARTICLE_SPEED_BASE = 0.08;
    private static final double PARTICLE_SPEED_RANDOM = 0.15;
    private static final double PARTICLE_UPWARD_DRIFT = 0.05;
    private static final double PARTICLE_UPWARD_FACTOR = 0.4;

    private static final int BLOCK_UPDATE_FLAGS = 2;

    // ==================== Instance Fields ====================

    private final Map<String, Map<BlockPos, VanillaLightSyncData>> syncedBlocks = new HashMap<>();

    // ==================== Block Type Info Record ====================

    private record BlockTypeInfo(
        boolean isRedstoneLamp,
        boolean isCampfire,
        boolean isCandle,
        boolean isCopperBulb,
        boolean hasLitProperty
    ) {
        boolean isControllable() {
            return isRedstoneLamp || isCampfire || isCandle || isCopperBulb;
        }
    }

    // ==================== Registration ====================

    public void registerLight(Level level, BlockPos blockPos, BlockPos controllerPos,
                             FrequencyChannel channel, int color) {
        if (level == null || blockPos == null || controllerPos == null) {
            logError("Registration failed - null parameter");
            return;
        }

        BlockState state = level.getBlockState(blockPos);
        BlockTypeInfo blockInfo = getBlockTypeInfo(state);

        int originalLightLevel = determineOriginalLightLevel(state, blockInfo);
        if (originalLightLevel == 0) {
            logWarn("Block at {} has no light emission, cannot sync", blockPos);
            return;
        }

        storeAndRegisterLight(level, blockPos, controllerPos, channel, color, originalLightLevel);
        logDebug("Registered vanilla light at {} | Channel: {} | Light: {}",
            blockPos, channel.getDisplayName(), originalLightLevel);
    }

    private BlockTypeInfo getBlockTypeInfo(BlockState state) {
        Block block = state.getBlock();
        return new BlockTypeInfo(
            block instanceof RedstoneLampBlock,
            block instanceof CampfireBlock,
            block instanceof CandleBlock || block instanceof CandleCakeBlock,
            block.getDescriptionId().contains(COPPER_BULB_ID),
            state.hasProperty(BlockStateProperties.LIT)
        );
    }

    private int determineOriginalLightLevel(BlockState state, BlockTypeInfo info) {
        int lightLevel = state.getLightEmission();
        if (lightLevel > 0) return lightLevel;

        if (info.isRedstoneLamp) return REDSTONE_LAMP_LIGHT_LEVEL;
        if (info.isCampfire) return CAMPFIRE_LIGHT_LEVEL;
        if (info.isCandle) return CANDLE_MAX_LIGHT_LEVEL;
        if (info.isCopperBulb) return COPPER_BULB_LIGHT_LEVEL;

        return 0;
    }

    private void storeAndRegisterLight(Level level, BlockPos blockPos, BlockPos controllerPos,
                                        FrequencyChannel channel, int color, int lightLevel) {
        String dimensionKey = getDimensionKey(level);
        VanillaLightSyncData syncData = new VanillaLightSyncData(
            blockPos, controllerPos, channel, color, lightLevel
        );

        syncedBlocks.computeIfAbsent(dimensionKey, k -> new HashMap<>())
                   .put(blockPos, syncData);

        if (level.isClientSide) {
            ColoredLightRegistry.getInstance().registerLight(blockPos, color, lightLevel, 0.0f);
        }
    }

    // ==================== Unregistration ====================

    public void unregisterLight(Level level, BlockPos blockPos) {
        if (level == null || blockPos == null) return;

        String dimensionKey = getDimensionKey(level);
        Map<BlockPos, VanillaLightSyncData> dimensionMap = syncedBlocks.get(dimensionKey);

        if (dimensionMap != null) {
            VanillaLightSyncData removed = dimensionMap.remove(blockPos);
            if (removed != null) {
                if (level.isClientSide) {
                    ColoredLightRegistry.getInstance().removeLight(blockPos);
                }
                logDebug("Unregistered vanilla light at {}", blockPos);
            }
        }
    }

    // ==================== Query Methods ====================

    public boolean isSynced(Level level, BlockPos blockPos) {
        String dimensionKey = getDimensionKey(level);
        Map<BlockPos, VanillaLightSyncData> dimensionMap = syncedBlocks.get(dimensionKey);
        return dimensionMap != null && dimensionMap.containsKey(blockPos);
    }

    public VanillaLightSyncData getSyncData(Level level, BlockPos blockPos) {
        String dimensionKey = getDimensionKey(level);
        Map<BlockPos, VanillaLightSyncData> dimensionMap = syncedBlocks.get(dimensionKey);
        return dimensionMap != null ? dimensionMap.get(blockPos) : null;
    }

    public void updateSyncData(Level level, BlockPos blockPos, FrequencyChannel newChannel, int newColor) {
        VanillaLightSyncData data = getSyncData(level, blockPos);
        if (data != null) {
            registerLight(level, blockPos, data.getControllerPos(), newChannel, newColor);
        }
    }

    public List<VanillaLightSyncData> getAllSyncedBlocks(Level level) {
        String dimensionKey = getDimensionKey(level);
        Map<BlockPos, VanillaLightSyncData> dimensionMap = syncedBlocks.get(dimensionKey);
        return dimensionMap != null ? new ArrayList<>(dimensionMap.values()) : new ArrayList<>();
    }

    // ==================== Tick All Lights ====================

    public void tickAllLights(Level level) {
        if (!level.isClientSide) return;

        String dimensionKey = getDimensionKey(level);
        Map<BlockPos, VanillaLightSyncData> dimensionMap = syncedBlocks.get(dimensionKey);
        if (dimensionMap == null || dimensionMap.isEmpty()) return;

        logPeriodicDebug(level, "Ticking {} synced lights", dimensionMap.size());

        List<BlockPos> toRemove = new ArrayList<>();
        for (Map.Entry<BlockPos, VanillaLightSyncData> entry : dimensionMap.entrySet()) {
            if (!tickSingleLight(level, entry.getKey(), entry.getValue())) {
                toRemove.add(entry.getKey());
            }
        }

        toRemove.forEach(pos -> unregisterLight(level, pos));
    }

    private boolean tickSingleLight(Level level, BlockPos pos, VanillaLightSyncData data) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        BlockTypeInfo blockInfo = getBlockTypeInfo(state);
        if (!blockInfo.isControllable() && state.getLightEmission() == 0) {
            return false;
        }

        tickLight(level, pos, data);
        return true;
    }

    // ==================== Tick Single Light ====================

    private void tickLight(Level level, BlockPos pos, VanillaLightSyncData data) {
        long gameTime = level.getGameTime();

        RhythmControllerBlockEntity controller = getController(level, data.getControllerPos());
        if (controller == null) return;

        BlockPos jukeboxPos = controller.getJukeboxPos();
        if (jukeboxPos == null) return;

        PlaybackState playbackState = ClientSongManager.getInstance().getState(jukeboxPos);
        if (playbackState != PlaybackState.PLAYING) {
            turnOffLight(level, pos, data, gameTime);
            return;
        }

        FrequencyData freqData = getValidFrequencyData(jukeboxPos);
        if (freqData == null) return;

        long tickOffset = calculateTickOffset(level, jukeboxPos, freqData);
        if (tickOffset < 0 || tickOffset >= freqData.getDurationTicks()) return;

        float intensity = getChannelIntensity(freqData, tickOffset, data.getChannel());
        updateLightWithIntensity(level, pos, data, intensity, gameTime);
    }

    private RhythmControllerBlockEntity getController(Level level, BlockPos controllerPos) {
        BlockEntity entity = level.getBlockEntity(controllerPos);
        return entity instanceof RhythmControllerBlockEntity controller ? controller : null;
    }

    private FrequencyData getValidFrequencyData(BlockPos jukeboxPos) {
        FrequencyData data = ClientSongManager.getInstance().getFrequencyData(jukeboxPos);
        if (data == null || data.isLoading()) return null;
        return data;
    }

    private long calculateTickOffset(Level level, BlockPos jukeboxPos, FrequencyData freqData) {
        Long playbackStartTime = ClientSongManager.getInstance().getPlaybackStartTime(jukeboxPos);
        if (playbackStartTime == null) {
            playbackStartTime = freqData.getStartTime();
        }
        return level.getGameTime() - playbackStartTime;
    }

    private void turnOffLight(Level level, BlockPos pos, VanillaLightSyncData data, long gameTime) {
        data.setCurrentBrightness(0);
        ColoredLightRegistry.getInstance().updateLightIntensity(pos, 0);
        updateVanillaBlockLight(level, pos, data, 0, gameTime);
    }

    private void updateLightWithIntensity(Level level, BlockPos pos, VanillaLightSyncData data,
                                           float intensity, long gameTime) {
        float currentBrightness = data.getCurrentBrightness();
        float newBrightness = currentBrightness + (intensity - currentBrightness) * BRIGHTNESS_LERP_SPEED;
        data.setCurrentBrightness(newBrightness);

        ColoredLightRegistry.getInstance().updateLightIntensity(pos, newBrightness);
        updateVanillaBlockLight(level, pos, data, intensity, gameTime);

        checkAndSpawnParticles(level, pos, data, intensity);
    }

    private void checkAndSpawnParticles(Level level, BlockPos pos, VanillaLightSyncData data, float intensity) {
        float lastIntensity = data.getLastIntensity();
        if (intensity > PARTICLE_THRESHOLD && lastIntensity <= PARTICLE_THRESHOLD) {
            spawnBeatParticles(level, pos, data, intensity);
        }
        data.setLastIntensity(intensity);
    }

    // ==================== Vanilla Block Light Update ====================

    private void updateVanillaBlockLight(Level level, BlockPos pos, VanillaLightSyncData data,
                                        float intensity, long currentTick) {
        if (!data.canUpdateLight(currentTick)) return;
        data.setLastLightUpdateTick(currentTick);

        BlockState state = level.getBlockState(pos);
        boolean shouldBeLit = intensity > LIGHT_ON_THRESHOLD;

        if (!state.hasProperty(BlockStateProperties.LIT)) return;

        boolean isCurrentlyLit = state.getValue(BlockStateProperties.LIT);
        if (shouldBeLit != isCurrentlyLit) {
            BlockState newState = state.setValue(BlockStateProperties.LIT, shouldBeLit);
            level.setBlock(pos, newState, BLOCK_UPDATE_FLAGS);
            data.setCurrentlyLit(shouldBeLit);
        }
    }

    // ==================== Particles ====================

    private void spawnBeatParticles(Level level, BlockPos pos, VanillaLightSyncData data, float intensity) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        int lifetime = PARTICLE_LIFETIME_BASE + (int)(intensity * PARTICLE_LIFETIME_INTENSITY_FACTOR);
        ColoredParticleEffect effect = new ColoredParticleEffect(
            data.getColor(), lifetime, PARTICLE_TEXTURE, PARTICLE_SCALE, true
        );

        int count = calculateParticleCount(intensity);
        for (int i = 0; i < count; i++) {
            double[] velocity = calculateParticleVelocity(intensity);
            level.addParticle(effect, x, y, z, velocity[0], velocity[1], velocity[2]);
        }
    }

    private int calculateParticleCount(float intensity) {
        int baseCount;
        if (intensity > PARTICLE_INTENSITY_HIGH_THRESHOLD) {
            baseCount = PARTICLE_COUNT_HIGH;
        } else if (intensity > PARTICLE_INTENSITY_MEDIUM_THRESHOLD) {
            baseCount = PARTICLE_COUNT_MEDIUM;
        } else {
            baseCount = PARTICLE_COUNT_LOW;
        }
        return baseCount + (int)(intensity * PARTICLE_COUNT_INTENSITY_FACTOR);
    }

    private double[] calculateParticleVelocity(float intensity) {
        double theta = Math.random() * 2 * Math.PI;
        double phi = Math.random() * Math.PI;
        double speed = PARTICLE_SPEED_BASE + Math.random() * PARTICLE_SPEED_RANDOM * (1.0 + intensity * 0.5);

        double vx = speed * Math.sin(phi) * Math.cos(theta);
        double vy = Math.abs(speed * Math.sin(phi) * Math.sin(theta)) * PARTICLE_UPWARD_FACTOR + PARTICLE_UPWARD_DRIFT;
        double vz = speed * Math.cos(phi);

        return new double[]{vx, vy, vz};
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
        return (data.getSubBassIntensity(tickOffset) + data.getDeepBassIntensity(tickOffset) +
                data.getBassIntensity(tickOffset) + data.getLowMidIntensity(tickOffset) +
                data.getMidLowIntensity(tickOffset) + data.getMidIntensity(tickOffset) +
                data.getMidHighIntensity(tickOffset) + data.getHighMidIntensity(tickOffset) +
                data.getHighIntensity(tickOffset) + data.getVeryHighIntensity(tickOffset) +
                data.getUltraIntensity(tickOffset) + data.getTopIntensity(tickOffset)) / 12.0f;
    }

    // ==================== Clear Operations ====================

    public void clearAll() {
        for (Map<BlockPos, VanillaLightSyncData> dimensionMap : syncedBlocks.values()) {
            for (BlockPos pos : dimensionMap.keySet()) {
                ColoredLightRegistry.getInstance().removeLight(pos);
            }
        }
        syncedBlocks.clear();
        logDebug("Cleared all synced vanilla lights");
    }

    public void clearDimension(Level level) {
        String dimensionKey = getDimensionKey(level);
        Map<BlockPos, VanillaLightSyncData> dimensionMap = syncedBlocks.remove(dimensionKey);

        if (dimensionMap != null) {
            dimensionMap.keySet().forEach(pos ->
                ColoredLightRegistry.getInstance().removeLight(pos)
            );
            logDebug("Cleared {} synced lights in dimension {}", dimensionMap.size(), dimensionKey);
        }
    }

    // ==================== Utility ====================

    private String getDimensionKey(Level level) {
        return level.dimension().location().toString();
    }

    // ==================== Logging ====================

    private void logPeriodicDebug(Level level, String message, Object... args) {
        if (RhythmConstants.DEBUG_LIGHTS && level.getGameTime() % DEBUG_LOG_INTERVAL_TICKS == 0) {
            RhythmConstants.LOGGER.debug("[VanillaSync] " + message, args);
        }
    }

    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_LIGHTS) {
            RhythmConstants.LOGGER.debug("[VanillaSync] " + message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        RhythmConstants.LOGGER.warn("[VanillaSync] " + message, args);
    }

    private void logError(String message, Object... args) {
        RhythmConstants.LOGGER.error("[VanillaSync] " + message, args);
    }
}
