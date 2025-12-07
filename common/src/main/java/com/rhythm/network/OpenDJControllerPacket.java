package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client packet to open the DJ Controller GUI.
 */
public record OpenDJControllerPacket(
    BlockPos controllerPos,
    BlockPos jukeboxPos,
    float masterVolume,
    float bassBoost,
    float[] eqBands,
    boolean loopEnabled
) implements CustomPacketPayload {

    // ==================== Packet Registration ====================

    public static final Type<OpenDJControllerPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "open_dj_controller"));

    public static final StreamCodec<FriendlyByteBuf, OpenDJControllerPacket> CODEC = StreamCodec.of(
        OpenDJControllerPacket::write,
        OpenDJControllerPacket::read
    );

    // ==================== Encode/Decode ====================

    private static void write(FriendlyByteBuf buf, OpenDJControllerPacket packet) {
        buf.writeBlockPos(packet.controllerPos);
        buf.writeBlockPos(packet.jukeboxPos != null ? packet.jukeboxPos : BlockPos.ZERO);
        buf.writeBoolean(packet.jukeboxPos != null);
        buf.writeFloat(packet.masterVolume);
        buf.writeFloat(packet.bassBoost);
        writeEqBands(buf, packet.eqBands);
        buf.writeBoolean(packet.loopEnabled);
    }

    private static void writeEqBands(FriendlyByteBuf buf, float[] bands) {
        buf.writeVarInt(bands.length);
        for (float band : bands) {
            buf.writeFloat(band);
        }
    }

    private static OpenDJControllerPacket read(FriendlyByteBuf buf) {
        BlockPos controllerPos = buf.readBlockPos();
        BlockPos jukeboxPos = readNullableBlockPos(buf);
        float volume = buf.readFloat();
        float bass = buf.readFloat();
        float[] bands = readEqBands(buf);
        boolean loop = buf.readBoolean();

        return new OpenDJControllerPacket(controllerPos, jukeboxPos, volume, bass, bands, loop);
    }

    private static BlockPos readNullableBlockPos(FriendlyByteBuf buf) {
        BlockPos posRaw = buf.readBlockPos();
        boolean hasPos = buf.readBoolean();
        return hasPos ? posRaw : null;
    }

    private static float[] readEqBands(FriendlyByteBuf buf) {
        int bandCount = buf.readVarInt();
        float[] bands = new float[bandCount];
        for (int i = 0; i < bandCount; i++) {
            bands[i] = buf.readFloat();
        }
        return bands;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Client Handler ====================

    public void handle() {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[OpenDJController] Received for controller at {}", controllerPos);
        }
    }
}

