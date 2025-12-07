package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.ClientSongManager;
import com.rhythm.audio.FrequencyData;
import com.rhythm.audio.PlaybackState;
import com.rhythm.audio.SeekableAudioPlayer;
import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Server -> Client packet for playback state changes.
 * <p>
 * Triggered by controller clicks (play/stop toggle).
 */
public record PlaybackStatePacket(
    BlockPos jukeboxPos,
    PlaybackState state,
    long timeValue,
    long seekToTick
) implements CustomPacketPayload {

    // ==================== Packet Registration ====================

    public static final Type<PlaybackStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "playback_state"));

    public static final StreamCodec<FriendlyByteBuf, PlaybackStatePacket> CODEC = StreamCodec.of(
        PlaybackStatePacket::encode,
        PlaybackStatePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, PlaybackStatePacket packet) {
        buf.writeBlockPos(packet.jukeboxPos);
        buf.writeEnum(packet.state);
        buf.writeLong(packet.timeValue);
        buf.writeLong(packet.seekToTick);
    }

    private static PlaybackStatePacket decode(FriendlyByteBuf buf) {
        return new PlaybackStatePacket(
            buf.readBlockPos(),
            buf.readEnum(PlaybackState.class),
            buf.readLong(),
            buf.readLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Client Handler ====================

    public void handle() {
        logDebug("State change | Pos: {} | State: {} | TimeValue: {} | SeekTo: {}",
            jukeboxPos, state, timeValue, seekToTick);

        ClientSongManager manager = ClientSongManager.getInstance();
        manager.setState(jukeboxPos, state);

        switch (state) {
            case PLAYING -> handlePlaying(manager);
            case STOPPED -> handleStopped(manager);
            case READY -> handleReady(manager);
            case LOADING -> logDebug("Loading state (unexpected via this packet)");
            case EMPTY -> handleEmpty();
        }
    }

    // ==================== State Handlers ====================

    private void handlePlaying(ClientSongManager manager) {
        FrequencyData data = manager.getFrequencyData(jukeboxPos);

        if (!isValidFrequencyData(data)) {
            logError("No valid FrequencyData for playing jukebox | data: {} | isLoading: {}",
                data, data != null ? data.isLoading() : "N/A");
            return;
        }

        manager.setPlaybackStartTime(jukeboxPos, timeValue);
        manager.clearPausedAt(jukeboxPos);

        startAudioPlayback(manager);
    }

    private boolean isValidFrequencyData(FrequencyData data) {
        return data != null && !data.isLoading();
    }

    private void startAudioPlayback(ClientSongManager manager) {
        ResourceLocation soundEventId = manager.getSoundEventId(jukeboxPos);
        String customUrl = manager.getCustomUrl(jukeboxPos);

        if (soundEventId == null) {
            logError("No sound event ID stored for playback");
            return;
        }

        float volume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
        boolean isResume = seekToTick > 0;

        logDebug("Starting audio | SoundEvent: {} | CustomUrl: {} | Resume: {} | Volume: {}",
            soundEventId, customUrl != null ? customUrl : "(none)", isResume, volume);

        SeekableAudioPlayer.getInstance().playFromPosition(jukeboxPos, soundEventId, customUrl, seekToTick, volume);
    }

    private void handleStopped(ClientSongManager manager) {
        manager.setPausedAt(jukeboxPos, timeValue);
        manager.clearPlaybackStartTime(jukeboxPos);
        SeekableAudioPlayer.getInstance().stop(jukeboxPos);

        logDebug("Stopped/Paused at tick {}", timeValue);
    }

    private void handleReady(ClientSongManager manager) {
        manager.clearPausedAt(jukeboxPos);
        SeekableAudioPlayer.getInstance().stop(jukeboxPos);

        logDebug("Ready to play");
    }

    private void handleEmpty() {
        SeekableAudioPlayer.getInstance().stop(jukeboxPos);
        logDebug("Empty state (unexpected via this packet)");
    }

    // ==================== Logging ====================

    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[PlaybackState] " + message, args);
        }
    }

    private void logError(String message, Object... args) {
        RhythmConstants.LOGGER.error("[PlaybackState] " + message, args);
    }
}

