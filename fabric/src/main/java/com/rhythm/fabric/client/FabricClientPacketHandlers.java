package com.rhythm.fabric.client;

import com.rhythm.audio.executable.RhythmExecutable;
import com.rhythm.client.gui.DJControllerScreen;
import com.rhythm.client.gui.URLInputScreen;
import com.rhythm.network.DiscInsertedPacket;
import com.rhythm.network.DiscRemovedPacket;
import com.rhythm.network.OpenDJControllerPacket;
import com.rhythm.network.OpenURLScreenPacket;
import com.rhythm.network.PlaybackStatePacket;
import com.rhythm.network.SongStopPacket;
import com.rhythm.network.SongUpdatePacket;
import com.rhythm.util.RhythmConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Registers client-side packet handlers for Fabric.
 */
public final class FabricClientPacketHandlers {

    private FabricClientPacketHandlers() {}

    // ==================== Registration ====================

    public static void register() {
        registerPacketReceivers();
        registerPacketSenders();
        registerConnectionEvents();
        initializeExecutables();

        RhythmConstants.LOGGER.debug("[FabricClient] Registered client packet handlers");
    }

    // ==================== Packet Receivers (S2C) ====================

    private static void registerPacketReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SongUpdatePacket.TYPE,
            (payload, context) -> context.client().execute(payload::handle));

        ClientPlayNetworking.registerGlobalReceiver(SongStopPacket.TYPE,
            (payload, context) -> context.client().execute(payload::handle));

        ClientPlayNetworking.registerGlobalReceiver(DiscInsertedPacket.TYPE,
            (payload, context) -> context.client().execute(payload::handle));

        ClientPlayNetworking.registerGlobalReceiver(DiscRemovedPacket.TYPE,
            (payload, context) -> context.client().execute(payload::handle));

        ClientPlayNetworking.registerGlobalReceiver(PlaybackStatePacket.TYPE,
            (payload, context) -> context.client().execute(payload::handle));

        ClientPlayNetworking.registerGlobalReceiver(OpenURLScreenPacket.TYPE,
            (payload, context) -> context.client().execute(() ->
                URLInputScreen.open(payload.currentUrl(), payload.currentTitle(),
                    payload.currentDuration(), payload.currentLoop())));

        ClientPlayNetworking.registerGlobalReceiver(OpenDJControllerPacket.TYPE,
            (payload, context) -> context.client().execute(() ->
                Minecraft.getInstance().setScreen(
                    new DJControllerScreen(payload.controllerPos(), payload.jukeboxPos()))));
    }

    // ==================== Packet Senders (C2S) ====================

    private static void registerPacketSenders() {
        URLInputScreen.setPacketSender(ClientPlayNetworking::send);
        DJControllerScreen.setPacketSender(ClientPlayNetworking::send);
        DiscInsertedPacket.setPacketSender(ClientPlayNetworking::send);
    }

    // ==================== Connection Events ====================

    private static void registerConnectionEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (RhythmExecutable executable : RhythmExecutable.values()) {
                executable.killAllProcesses();
            }
        });
    }

    // ==================== Executables ====================

    private static void initializeExecutables() {
        for (RhythmExecutable executable : RhythmExecutable.values()) {
            if (!executable.checkForExecutable()) {
                RhythmConstants.LOGGER.warn("Failed to load executable: {}", executable);
            }
        }
    }
}

