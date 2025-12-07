package com.rhythm.fabric.client;

import com.rhythm.network.DiscInsertedPacket;
import com.rhythm.util.RhythmConstants;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint for RhythmMod.
 */
public final class RhythmModFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerCallbacks();
        FabricClientRendering.register();
        FabricClientPacketHandlers.register();
        FabricClientEvents.register();
        RhythmModClientCommands.register();

        RhythmConstants.LOGGER.info("RhythmMod client initialized (Fabric)");
    }

    private void registerCallbacks() {
        DiscInsertedPacket.setLoadingCallback(new FabricLoadingCallback());
    }
}
