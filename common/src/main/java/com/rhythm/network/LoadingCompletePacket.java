package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.state.LinkedJukeboxRegistry;
import com.rhythm.audio.state.PlaybackState;
import com.rhythm.audio.state.ServerPlaybackTracker;
import com.rhythm.block.controller.RhythmControllerBlockEntity;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Client -> Server packet when FFT loading is complete.
 * <p>
 * Transitions jukebox from LOADING to READY and optionally triggers autoplay.
 */
public record LoadingCompletePacket(
    BlockPos jukeboxPos,
    long songId,
    boolean autoplay
) implements CustomPacketPayload {

    // ==================== Constants ====================

    private static final long RESUME_OFFSET_NONE = 0L;

    // ==================== Packet Registration ====================

    public static final Type<LoadingCompletePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "loading_complete"));

    public static final StreamCodec<FriendlyByteBuf, LoadingCompletePacket> CODEC = StreamCodec.of(
        LoadingCompletePacket::encode,
        LoadingCompletePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, LoadingCompletePacket packet) {
        buf.writeBlockPos(packet.jukeboxPos);
        buf.writeLong(packet.songId);
        buf.writeBoolean(packet.autoplay);
    }

    private static LoadingCompletePacket decode(FriendlyByteBuf buf) {
        return new LoadingCompletePacket(
            buf.readBlockPos(),
            buf.readLong(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Server Handler ====================

    public void handleOnServer(ServerPlayer player) {
        logDebug("Loading complete received | Pos: {} | SongID: {} | Autoplay: {}", jukeboxPos, songId, autoplay);

        ServerPlaybackTracker tracker = ServerPlaybackTracker.getInstance();

        if (!validateAndTransition(tracker)) {
            return;
        }

        if (player != null) {
            handleAutoplay(player, tracker);
        }
    }

    public void handleOnServer() {
        handleOnServer(null);
    }

    // ==================== Validation ====================

    private boolean validateAndTransition(ServerPlaybackTracker tracker) {
        if (!tracker.validateSongId(jukeboxPos, songId)) {
            logDebug("SongId mismatch - ignoring stale loading complete");
            return false;
        }

        tracker.onLoadingComplete(jukeboxPos);
        logDebug("State updated to READY");
        return true;
    }

    // ==================== Autoplay ====================

    private void handleAutoplay(ServerPlayer player, ServerPlaybackTracker tracker) {
        boolean shouldAutoplayUrlDisc = tracker.shouldAutoplayOnReady(jukeboxPos);
        boolean doAutoplay = autoplay || shouldAutoplayUrlDisc;

        if (shouldAutoplayUrlDisc) {
            tracker.clearAutoplayFlag(jukeboxPos);
            logDebug("URL disc autoplay triggered for unlinked jukebox");
        }

        if (doAutoplay) {
            triggerAutoplay(player.serverLevel(), tracker);
        }
    }

    private void triggerAutoplay(ServerLevel level, ServerPlaybackTracker tracker) {
        BlockPos controllerPos = LinkedJukeboxRegistry.getInstance().getControllerForJukebox(jukeboxPos);

        if (controllerPos != null) {
            triggerControllerPlayback(level, controllerPos);
        } else {
            triggerDirectPlayback(level, tracker);
        }
    }

    private void triggerControllerPlayback(ServerLevel level, BlockPos controllerPos) {
        if (level.getBlockEntity(controllerPos) instanceof RhythmControllerBlockEntity controller) {
            logDebug("Autoplay: Triggering playback via controller at {}", controllerPos);
            controller.triggerPlayback(level);
        } else {
            logDebug("Autoplay: Controller block entity not found at {}", controllerPos);
        }
    }

    private void triggerDirectPlayback(ServerLevel level, ServerPlaybackTracker tracker) {
        logDebug("Autoplay: Starting direct playback for URL disc");

        long startTime = level.getGameTime();

        if (!tracker.play(jukeboxPos, startTime)) {
            return;
        }

        sendPlaybackPacketToNearbyPlayers(level, startTime);
        logDebug("Direct playback started for URL disc at {}", jukeboxPos);
    }

    private void sendPlaybackPacketToNearbyPlayers(ServerLevel level, long startTime) {
        ChunkPos chunkPos = new ChunkPos(jukeboxPos);
        PlaybackStatePacket packet = new PlaybackStatePacket(
            jukeboxPos, PlaybackState.PLAYING, startTime, RESUME_OFFSET_NONE
        );

        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            PacketSender.getInstance().sendToPlayer(p, packet);
        }
    }

    // ==================== Logging ====================

    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[LoadingComplete] " + message, args);
        }
    }
}

