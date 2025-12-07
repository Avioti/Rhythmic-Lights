package com.rhythm.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Platform-agnostic packet sending interface
 * Each loader implements this to send packets using their specific networking API
 */
public interface PacketSender {
    /**
     * Send a CustomPayload packet to a specific player
     * @param player The player to send to
     * @param payload The packet payload to send
     */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);


    static PacketSender getInstance() {
        if (Holder.INSTANCE == null) {
            throw new IllegalStateException("PacketSender not initialized! Platform module must set this during startup.");
        }
        return Holder.INSTANCE;
    }

    static void setInstance(PacketSender sender) {
        Holder.INSTANCE = sender;
    }

    class Holder {
        private static PacketSender INSTANCE = null;
    }
}

