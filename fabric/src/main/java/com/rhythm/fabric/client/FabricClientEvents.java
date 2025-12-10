package com.rhythm.fabric.client;

import com.rhythm.audio.state.ClientSongManager;
import com.rhythm.audio.playback.SeekableAudioPlayer;
import com.rhythm.client.gui.DownloadProgressOverlay;
import com.rhythm.client.gui.LoadingOverlay;
import com.rhythm.client.gui.RGBText;
import com.rhythm.client.light.ColoredLightRegistry;
import com.rhythm.sync.VanillaLightSyncManager;
import com.rhythm.util.RhythmConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Registers client-side tick and render events for Fabric.
 */
public final class FabricClientEvents {

    private FabricClientEvents() {}

    // ==================== Constants ====================

    private static final float TICK_DELTA = 1.0f;

    // ==================== State ====================

    private static ClientLevel lastLevel = null;

    // ==================== Registration ====================

    public static void register() {
        registerHudRenderCallback();
        registerEndTickCallback();
        registerStartTickCallback();
    }

    // ==================== HUD Rendering ====================

    private static void registerHudRenderCallback() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            float partialTick = tickCounter.getGameTimeDeltaPartialTick(true);
            LoadingOverlay.render(graphics, partialTick);
            DownloadProgressOverlay.render(graphics);
        });
    }

    // ==================== End Tick ====================

    private static void registerEndTickCallback() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickVanillaLights(client);
            tickRGBText();
            syncAudioPauseState();
            updatePlayerPosition();
        });
    }

    private static void tickVanillaLights(Minecraft client) {
        if (client.level != null) {
            VanillaLightSyncManager.getInstance().tickAllLights(client.level);
        }
    }

    private static void tickRGBText() {
        RGBText.tick(TICK_DELTA);
    }

    private static void syncAudioPauseState() {
        boolean isGamePaused = Minecraft.getInstance().isPaused();
        SeekableAudioPlayer.getInstance().setGamePaused(isGamePaused);
    }

    private static void updatePlayerPosition() {
        SeekableAudioPlayer.getInstance().updatePlayerPosition();
    }

    // ==================== Start Tick (World Change Detection) ====================

    private static void registerStartTickCallback() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ClientLevel currentLevel = client.level;
            if (isWorldUnloaded(currentLevel)) {
                clearClientState();
            }
            lastLevel = currentLevel;
        });
    }

    private static boolean isWorldUnloaded(ClientLevel currentLevel) {
        return currentLevel == null && lastLevel != null;
    }

    private static void clearClientState() {
        ColoredLightRegistry.getInstance().clearAll();
        ClientSongManager.getInstance().clearAll();
        LoadingOverlay.clearAll();

        if (RhythmConstants.DEBUG_LIGHTS) {
            RhythmConstants.LOGGER.debug("Cleared all client state on world unload");
        }
    }
}

