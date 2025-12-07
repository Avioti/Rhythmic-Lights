package com.rhythm.fabric.client;

import com.rhythm.client.gui.LoadingOverlay;
import com.rhythm.network.DiscInsertedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Loading callback implementation for Fabric client.
 */
public final class FabricLoadingCallback implements DiscInsertedPacket.LoadingCallback {

    @Override
    public void onLoadingStart(BlockPos pos, ResourceLocation soundId, String songTitle) {
        LoadingOverlay.show(pos, soundId, songTitle);
    }

    @Override
    public void onLoadingProgress(BlockPos pos, float progress) {
        LoadingOverlay.updateProgress(pos, progress);
    }

    @Override
    public void onLoadingComplete(BlockPos pos) {
        LoadingOverlay.hide(pos);
    }

    @Override
    public void onLoadingFailed(BlockPos pos) {
        LoadingOverlay.hide(pos);
    }
}

