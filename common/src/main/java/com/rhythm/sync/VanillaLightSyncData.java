package com.rhythm.sync;

import com.rhythm.audio.FrequencyChannel;
import net.minecraft.core.BlockPos;

/**
 * Data class storing sync configuration for a vanilla light block
 */
public class VanillaLightSyncData {
    private final BlockPos blockPos;
    private final BlockPos controllerPos;
    private final FrequencyChannel channel;
    private final int color;
    private final int originalLightLevel;

    // Client-side rendering state
    private float currentBrightness = 0.0f;
    private float lastIntensity = 0.0f;

    // Light level tracking
    private boolean currentlyLit = false;
    private long lastLightUpdateTick = -1;
    private static final int LIGHT_UPDATE_COOLDOWN = 2; // Update every 2 ticks (10 times per second)

    public VanillaLightSyncData(BlockPos blockPos, BlockPos controllerPos, FrequencyChannel channel,
                                int color, int originalLightLevel) {
        this.blockPos = blockPos;
        this.controllerPos = controllerPos;
        this.channel = channel;
        this.color = color;
        this.originalLightLevel = originalLightLevel;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public FrequencyChannel getChannel() {
        return channel;
    }

    public int getColor() {
        return color;
    }

    public int getOriginalLightLevel() {
        return originalLightLevel;
    }

    public float getCurrentBrightness() {
        return currentBrightness;
    }

    public void setCurrentBrightness(float brightness) {
        this.currentBrightness = brightness;
    }

    public float getLastIntensity() {
        return lastIntensity;
    }

    public void setLastIntensity(float intensity) {
        this.lastIntensity = intensity;
    }

    public boolean isCurrentlyLit() {
        return currentlyLit;
    }

    public void setCurrentlyLit(boolean lit) {
        this.currentlyLit = lit;
    }

    public long getLastLightUpdateTick() {
        return lastLightUpdateTick;
    }

    public void setLastLightUpdateTick(long tick) {
        this.lastLightUpdateTick = tick;
    }

    public boolean canUpdateLight(long currentTick) {
        return (currentTick - lastLightUpdateTick) >= LIGHT_UPDATE_COOLDOWN;
    }
}

