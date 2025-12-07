package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.AudioPreComputer;
import com.rhythm.audio.ClientSongManager;
import com.rhythm.audio.FrequencyData;
import com.rhythm.audio.RhythmAudioCache;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Server -> Client packet when a jukebox starts playing.
 */
public record SongUpdatePacket(
    BlockPos pos,
    ResourceLocation soundEventId,
    long startTime,
    String customUrl
) implements CustomPacketPayload {

    // ==================== Constants ====================

    private static final String[] STREAMING_DOMAINS = {"youtube.com", "youtu.be", "spotify.com", "soundcloud.com"};
    private static final int CACHE_WAIT_TIMEOUT_SECONDS = 30;

    // ==================== Compact Constructor ====================

    public SongUpdatePacket {
        if (customUrl == null) {
            customUrl = "";
        }
    }

    // ==================== Convenience Constructor ====================

    public SongUpdatePacket(BlockPos pos, ResourceLocation soundEventId, long startTime) {
        this(pos, soundEventId, startTime, "");
    }

    // ==================== Packet Registration ====================

    public static final Type<SongUpdatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "song_update"));

    public static final StreamCodec<FriendlyByteBuf, SongUpdatePacket> CODEC = StreamCodec.of(
        SongUpdatePacket::encode,
        SongUpdatePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, SongUpdatePacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeResourceLocation(packet.soundEventId);
        buf.writeLong(packet.startTime);
        buf.writeUtf(packet.customUrl);
    }

    private static SongUpdatePacket decode(FriendlyByteBuf buf) {
        return new SongUpdatePacket(
            buf.readBlockPos(),
            buf.readResourceLocation(),
            buf.readLong(),
            buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean hasCustomUrl() {
        return customUrl != null && !customUrl.isEmpty();
    }

    // ==================== Client Handler ====================

    public void handle() {
        logDebug("Received | Pos: {} | Sound: {} | StartTime: {} | URL: {}",
            pos, soundEventId, startTime, hasCustomUrl() ? customUrl : "(none)");

        registerPlaceholderData();

        if (hasCustomUrl()) {
            processCustomUrl();
        } else {
            processStandardDisc();
        }
    }

    private void registerPlaceholderData() {
        ClientSongManager.getInstance().registerSong(pos, new FrequencyData(startTime));
    }

    // ==================== Custom URL Processing ====================

    private void processCustomUrl() {
        if (isStreamingUrl(customUrl)) {
            processStreamingUrl();
        } else {
            processDirectAudioUrl();
        }
    }

    private boolean isStreamingUrl(String url) {
        for (String domain : STREAMING_DOMAINS) {
            if (url.contains(domain)) {
                return true;
            }
        }
        return false;
    }

    private void processStreamingUrl() {
        logDebug("Streaming URL detected, checking cache...");

        CompletableFuture.supplyAsync(() ->
            RhythmAudioCache.waitForCachedFile(customUrl, CACHE_WAIT_TIMEOUT_SECONDS)
        ).thenAccept(this::handleCachedFile);
    }

    private void handleCachedFile(Path cachedPath) {
        if (cachedPath != null) {
            analyzeFromCachedFile(cachedPath);
        } else {
            logError("Could not find cached file. Try right-clicking the disc to trigger download.");
        }
    }

    private void analyzeFromCachedFile(Path cachedPath) {
        logDebug("Found cached file: {}", cachedPath);

        try {
            String fileUrl = cachedPath.toUri().toString();
            AudioPreComputer.preComputeFrequenciesFromUrlWithCacheKey(fileUrl, customUrl, startTime, pos)
                .thenAccept(freqData -> onFFTComplete(freqData, "cached file"));
        } catch (Exception e) {
            logError("Error analyzing cached file: {}", e.getMessage());
        }
    }

    private void processDirectAudioUrl() {
        logDebug("Direct audio URL - starting FFT analysis...");

        AudioPreComputer.preComputeFrequenciesFromUrl(customUrl, startTime, pos)
            .thenAccept(freqData -> onFFTComplete(freqData, "URL"))
            .exceptionally(this::handleFFTError);
    }

    // ==================== Standard Disc Processing ====================

    private void processStandardDisc() {
        logDebug("Standard disc - using universal resolver for: {}", soundEventId);

        AudioPreComputer.preComputeFrequencies(soundEventId, startTime)
            .thenAccept(freqData -> onFFTComplete(freqData, "sound event"))
            .exceptionally(this::handleFFTError);
    }

    // ==================== FFT Completion ====================

    private void onFFTComplete(FrequencyData freqData, String source) {
        if (freqData != null) {
            ClientSongManager.getInstance().registerSong(pos, freqData);
            logDebug("FFT complete from {}: {} ticks", source, freqData.getDurationTicks());
        } else {
            logError("FFT analysis from {} failed - null result", source);
        }
    }

    private Void handleFFTError(Throwable ex) {
        logError("FFT analysis failed: {}", ex.getMessage());
        return null;
    }

    // ==================== Logging ====================

    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[SongUpdate] " + message, args);
        }
    }

    private void logError(String message, Object... args) {
        RhythmConstants.LOGGER.error("[SongUpdate] " + message, args);
    }
}

