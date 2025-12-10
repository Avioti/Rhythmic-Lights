package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.state.ClientSongManager;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client packet when a jukebox stops playing.
 */
public record SongStopPacket(BlockPos pos) implements CustomPacketPayload {

    // ==================== Packet Registration ====================

    public static final Type<SongStopPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "song_stop"));

    public static final StreamCodec<FriendlyByteBuf, SongStopPacket> CODEC = StreamCodec.of(
        SongStopPacket::encode,
        SongStopPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, SongStopPacket packet) {
        buf.writeBlockPos(packet.pos);
    }

    private static SongStopPacket decode(FriendlyByteBuf buf) {
        return new SongStopPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Client Handler ====================

    public void handle() {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[SongStop] Cleaning up frequency data at {}", pos);
        }
        ClientSongManager.getInstance().removeSong(pos);
    }
}

