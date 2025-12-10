package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.state.ClientSongManager;
import com.rhythm.audio.state.PlaybackState;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client packet when a disc is removed from a jukebox.
 * <p>
 * Clears all state and hides HUD elements.
 */
public record DiscRemovedPacket(BlockPos pos) implements CustomPacketPayload {

    // ==================== Packet Registration ====================

    public static final Type<DiscRemovedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "disc_removed"));

    public static final StreamCodec<FriendlyByteBuf, DiscRemovedPacket> CODEC = StreamCodec.of(
        DiscRemovedPacket::encode,
        DiscRemovedPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, DiscRemovedPacket packet) {
        buf.writeBlockPos(packet.pos);
    }

    private static DiscRemovedPacket decode(FriendlyByteBuf buf) {
        return new DiscRemovedPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Client Handler ====================

    public void handle() {
        logDebug("Disc removed at {}", pos);

        clearClientState();
        notifyLoadingCallback();

        logDebug("All state cleared for {}", pos);
    }

    private void clearClientState() {
        ClientSongManager manager = ClientSongManager.getInstance();
        manager.clearPosition(pos);
        manager.setState(pos, PlaybackState.EMPTY);
    }

    private void notifyLoadingCallback() {
        DiscInsertedPacket.LoadingCallback callback = DiscInsertedPacket.getLoadingCallback();
        if (callback != null) {
            callback.onLoadingFailed(pos);
        }
    }

    // ==================== Logging ====================

    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[DiscRemoved] " + message, args);
        }
    }
}

