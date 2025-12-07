package com.rhythm.fabric;

import com.rhythm.audio.JukeboxStateManager;
import com.rhythm.block.RhythmControllerBlock;
import com.rhythm.network.AutoMetadataPacket;
import com.rhythm.network.DJSettingsPacket;
import com.rhythm.network.DiscInsertedPacket;
import com.rhythm.network.DiscRemovedPacket;
import com.rhythm.network.LoadingCompletePacket;
import com.rhythm.network.OpenDJControllerPacket;
import com.rhythm.network.OpenURLScreenPacket;
import com.rhythm.network.PacketSender;
import com.rhythm.network.PlaybackStatePacket;
import com.rhythm.network.SetURLDataPacket;
import com.rhythm.network.SongStopPacket;
import com.rhythm.network.SongUpdatePacket;
import com.rhythm.util.RhythmConstants;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric-specific networking setup for RhythmMod.
 */
public final class RhythmModNetworking {

    private RhythmModNetworking() {}

    // ==================== Registration ====================

    public static void registerPackets() {
        registerS2CPackets();
        registerC2SPackets();
        registerC2SHandlers();
        registerPlayerEvents();
        initializePacketSender();

        RhythmConstants.LOGGER.info("Registered network packets (Fabric)");
    }

    // ==================== S2C Packets (Server-to-Client) ====================

    private static void registerS2CPackets() {
        PayloadTypeRegistry.playS2C().register(SongUpdatePacket.TYPE, SongUpdatePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SongStopPacket.TYPE, SongStopPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(DiscInsertedPacket.TYPE, DiscInsertedPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(DiscRemovedPacket.TYPE, DiscRemovedPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(PlaybackStatePacket.TYPE, PlaybackStatePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenURLScreenPacket.TYPE, OpenURLScreenPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenDJControllerPacket.TYPE, OpenDJControllerPacket.CODEC);
    }

    // ==================== C2S Packets (Client-to-Server) ====================

    private static void registerC2SPackets() {
        PayloadTypeRegistry.playC2S().register(LoadingCompletePacket.TYPE, LoadingCompletePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SetURLDataPacket.TYPE, SetURLDataPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AutoMetadataPacket.TYPE, AutoMetadataPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DJSettingsPacket.TYPE, DJSettingsPacket.CODEC);
    }

    // ==================== C2S Handlers ====================

    private static void registerC2SHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(LoadingCompletePacket.TYPE,
            (payload, context) -> context.server().execute(() ->
                payload.handleOnServer(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(SetURLDataPacket.TYPE,
            (payload, context) -> context.server().execute(() ->
                payload.handleOnServer(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(AutoMetadataPacket.TYPE,
            (payload, context) -> context.server().execute(() ->
                payload.handleOnServer(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(DJSettingsPacket.TYPE,
            (payload, context) -> context.server().execute(() ->
                handleDJSettingsPacket(payload, context.player())));
    }

    private static void handleDJSettingsPacket(DJSettingsPacket payload, ServerPlayer player) {
        if (payload.command() != DJSettingsPacket.Command.SYNC_SETTINGS) {
            RhythmControllerBlock.handleDJCommand(
                player.level(),
                payload.controllerPos(),
                payload.command(),
                player
            );
        }
    }

    // ==================== Player Events ====================

    private static void registerPlayerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                var player = handler.getPlayer();
                logDebug("Player {} joined, restoring jukebox states", player.getName().getString());
                JukeboxStateManager.getInstance().onPlayerJoin(player);
            }));
    }

    // ==================== Packet Sender ====================

    private static void initializePacketSender() {
        PacketSender.setInstance(new FabricPacketSender());
    }

    // ==================== Logging ====================

    private static void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[Networking] " + message, args);
        }
    }
}

