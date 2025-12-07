package com.rhythm.network;

import com.rhythm.RhythmMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from client to server to sync DJ controller settings.
 * Also used for play/pause/stop commands.
 */
public record DJSettingsPacket(
    BlockPos controllerPos,
    Command command,
    float masterVolume,
    float bassBoost,
    float[] eqBands,
    boolean loopEnabled
) implements CustomPacketPayload {

    public enum Command {
        SYNC_SETTINGS,  // Just sync audio settings
        PLAY,           // Start/resume playback
        PAUSE,          // Pause playback
        STOP            // Stop playback (reset to beginning)
    }

    public static final CustomPacketPayload.Type<DJSettingsPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "dj_settings"));

    public static final StreamCodec<FriendlyByteBuf, DJSettingsPacket> CODEC = StreamCodec.of(
        DJSettingsPacket::write,
        DJSettingsPacket::read
    );

    private static void write(FriendlyByteBuf buf, DJSettingsPacket packet) {
        buf.writeBlockPos(packet.controllerPos);
        buf.writeEnum(packet.command);
        buf.writeFloat(packet.masterVolume);
        buf.writeFloat(packet.bassBoost);

        // Write EQ bands
        buf.writeVarInt(packet.eqBands.length);
        for (float band : packet.eqBands) {
            buf.writeFloat(band);
        }

        buf.writeBoolean(packet.loopEnabled);
    }

    private static DJSettingsPacket read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Command cmd = buf.readEnum(Command.class);
        float volume = buf.readFloat();
        float bass = buf.readFloat();

        int bandCount = buf.readVarInt();
        float[] bands = new float[bandCount];
        for (int i = 0; i < bandCount; i++) {
            bands[i] = buf.readFloat();
        }

        boolean loop = buf.readBoolean();

        return new DJSettingsPacket(pos, cmd, volume, bass, bands, loop);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Factory methods for commands
    public static DJSettingsPacket createPlayCommand(BlockPos controllerPos) {
        return new DJSettingsPacket(controllerPos, Command.PLAY, 1.0f, 0.0f, new float[12], false);
    }

    public static DJSettingsPacket createPauseCommand(BlockPos controllerPos) {
        return new DJSettingsPacket(controllerPos, Command.PAUSE, 1.0f, 0.0f, new float[12], false);
    }

    public static DJSettingsPacket createStopCommand(BlockPos controllerPos) {
        return new DJSettingsPacket(controllerPos, Command.STOP, 1.0f, 0.0f, new float[12], false);
    }

    // Constructor for settings sync
    public DJSettingsPacket(BlockPos controllerPos, float masterVolume, float bassBoost,
                            float[] eqBands, boolean loopEnabled) {
        this(controllerPos, Command.SYNC_SETTINGS, masterVolume, bassBoost, eqBands, loopEnabled);
    }
}

