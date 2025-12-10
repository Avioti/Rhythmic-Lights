package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.analysis.AudioPreComputer;
import com.rhythm.audio.AudioSettings;
import com.rhythm.audio.state.ClientSongManager;
import com.rhythm.audio.analysis.FrequencyData;
import com.rhythm.audio.state.PlaybackState;
import com.rhythm.audio.io.RhythmSoundManager;
import com.rhythm.client.gui.DownloadProgressOverlay;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.io.File;

/**
 * Server -> Client packet when a disc is inserted into a jukebox.
 * <p>
 * Triggers FFT loading without starting playback.
 */
public record DiscInsertedPacket(
    BlockPos pos,
    ResourceLocation soundEventId,
    String customUrl,
    String songTitle,
    long songId,
    boolean loop
) implements CustomPacketPayload {

    // ==================== Constants ====================

    private static final int POST_DOWNLOAD_DELAY_MS = 500;
    private static final int INITIAL_FREQUENCY_DATA_TICKS = 0;

    // ==================== Callbacks (set by client module) ====================

    private static LoadingCallback loadingCallback = null;
    private static ClientPacketSender packetSender = null;

    /**
     * Callback interface for tracking disc loading progress.
     * Set by the client module to receive loading state updates.
     */
    public interface LoadingCallback {
        /** Called when FFT loading begins for a disc. */
        void onLoadingStart(BlockPos pos, ResourceLocation soundId, String songTitle);

        /** Called periodically with loading progress (0.0 to 1.0). */
        void onLoadingProgress(BlockPos pos, float progress);

        /** Called when FFT loading completes successfully. */
        void onLoadingComplete(BlockPos pos);

        /** Called when FFT loading fails. */
        void onLoadingFailed(BlockPos pos);
    }

    /**
     * Interface for sending packets to the server from the client module.
     */
    public interface ClientPacketSender {
        void sendToServer(CustomPacketPayload payload);
    }

    /**
     * Sets the loading callback for receiving disc loading state updates.
     *
     * @param callback the callback implementation
     */
    public static void setLoadingCallback(LoadingCallback callback) {
        loadingCallback = callback;
    }

    /**
     * Gets the current loading callback.
     *
     * @return the loading callback, or null if not set
     */
    public static LoadingCallback getLoadingCallback() {
        return loadingCallback;
    }

    /**
     * Sets the packet sender for client-to-server communication.
     *
     * @param sender the packet sender implementation
     */
    public static void setPacketSender(ClientPacketSender sender) {
        packetSender = sender;
    }

    // ==================== Compact Constructor ====================

    public DiscInsertedPacket {
        if (customUrl == null) {
            customUrl = "";
        }
        if (songTitle == null) {
            songTitle = "";
        }
    }

    // ==================== Packet Registration ====================

    public static final Type<DiscInsertedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "disc_inserted"));

    public static final StreamCodec<FriendlyByteBuf, DiscInsertedPacket> CODEC = StreamCodec.of(
        DiscInsertedPacket::encode,
        DiscInsertedPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, DiscInsertedPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeResourceLocation(packet.soundEventId);
        buf.writeUtf(packet.customUrl);
        buf.writeUtf(packet.songTitle);
        buf.writeLong(packet.songId);
        buf.writeBoolean(packet.loop);
    }

    private static DiscInsertedPacket decode(FriendlyByteBuf buf) {
        return new DiscInsertedPacket(
            buf.readBlockPos(),
            buf.readResourceLocation(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readLong(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Checks if this packet contains a custom URL for streaming audio.
     *
     * @return true if a custom URL is present
     */
    public boolean hasCustomUrl() {
        return customUrl != null && !customUrl.isEmpty();
    }

    /**
     * Checks if this packet contains a song title.
     *
     * @return true if a song title is present
     */
    public boolean hasSongTitle() {
        return songTitle != null && !songTitle.isEmpty();
    }

    // ==================== Client Handler ====================

    /**
     * Handles this packet on the client side.
     * Initializes client state and begins FFT processing for the inserted disc.
     */
    public void handle() {
        logDebug("========== DISC INSERTED ==========");
        logDebug("Position: {} | Sound: {} | SongID: {} | URL: {} | Title: {} | Loop: {}",
            pos, soundEventId, songId, hasCustomUrl() ? customUrl : "(none)",
            hasSongTitle() ? songTitle : "(none)", loop);

        initializeClientState();
        notifyLoadingStarted();
        startFFTProcessing();
    }

    private void initializeClientState() {
        ClientSongManager manager = ClientSongManager.getInstance();
        manager.clearPosition(pos);
        manager.setState(pos, PlaybackState.LOADING);
        manager.setSongId(pos, songId);
        manager.setLoadingProgress(pos, 0f);
        manager.setSoundEventId(pos, soundEventId);
        manager.setCustomUrl(pos, customUrl);
        manager.setSongTitle(pos, songTitle);
        manager.setLoopEnabled(pos, loop);
        manager.registerSong(pos, new FrequencyData(INITIAL_FREQUENCY_DATA_TICKS));
    }

    private void notifyLoadingStarted() {
        if (loadingCallback != null) {
            loadingCallback.onLoadingStart(pos, soundEventId, songTitle);
        }
    }

    // ==================== FFT Processing ====================

    private void startFFTProcessing() {
        if (hasCustomUrl()) {
            processCustomUrl();
        } else {
            processStandardSoundEvent();
        }
    }

    private void processCustomUrl() {
        if (RhythmConstants.isStreamingUrl(customUrl)) {
            processStreamingUrl();
        } else {
            logDebug("Direct audio URL - starting FFT...");
            processAudioFile(customUrl, customUrl, pos, songId);
        }
    }

    private void processStandardSoundEvent() {
        logDebug("Standard sound event - resolving file...");
        AudioPreComputer.preComputeFrequencies(soundEventId, INITIAL_FREQUENCY_DATA_TICKS)
            .thenAccept(freqData -> onFFTComplete(freqData, pos, songId));
    }


    // ==================== Streaming URL Handling ====================

    private void processStreamingUrl() {
        logDebug("Streaming URL detected - checking cache...");

        String fileName = RhythmSoundManager.getFileName(customUrl);
        File audioFile = RhythmSoundManager.getAudioFile(fileName);
        boolean isDownloading = RhythmSoundManager.isDownloading(fileName);

        if (audioFile.exists() && !isDownloading) {
            processCachedFile(audioFile);
        } else if (isDownloading) {
            waitForExistingDownload(fileName, audioFile);
        } else {
            startNewDownload(fileName, audioFile);
        }
    }

    private void processCachedFile(File audioFile) {
        logDebug("Found cached file (no download in progress): {}", audioFile);
        String fileUrl = audioFile.toURI().toString();
        processAudioFile(fileUrl, customUrl, pos, songId);
    }

    private void waitForExistingDownload(String fileName, File audioFile) {
        logDebug("Download/conversion in progress, waiting for completion...");
        RhythmSoundManager.queueSound(fileName, pos, customUrl);
        waitForDownloadCompletion(fileName, audioFile, pos, songId);
    }

    private void startNewDownload(String fileName, File audioFile) {
        logDebug("Starting download from: {}", customUrl);
        RhythmSoundManager.downloadSound(customUrl, fileName);
        RhythmSoundManager.queueSound(fileName, pos, customUrl);
        waitForDownloadCompletion(fileName, audioFile, pos, songId);
    }

    // ==================== Download Completion ====================

    private void waitForDownloadCompletion(String fileName, File audioFile,
                                            BlockPos targetPos, long targetSongId) {
        registerCompletionListener(fileName, audioFile, targetPos, targetSongId);
        handleNoActiveDownload(fileName, audioFile, targetPos, targetSongId);
    }

    private void registerCompletionListener(String fileName, File audioFile,
                                             BlockPos targetPos, long targetSongId) {
        RhythmSoundManager.addCompletionListener(fileName, success -> {
            if (success) {
                logDebug("Download completed via event listener - proceeding to FFT");
                sleepSafely();
                processCompletedDownload(audioFile, targetPos, targetSongId);
            } else {
                logError("Download failed via event listener");
                handleDownloadFailure(targetPos);
            }
        });
    }

    private void handleNoActiveDownload(String fileName, File audioFile,
                                         BlockPos targetPos, long targetSongId) {
        if (RhythmSoundManager.isDownloading(fileName)) {
            return;
        }

        logDebug("No active download process found, checking if file already exists...");
        RhythmSoundManager.removeCompletionListener(fileName);

        boolean fileExists = audioFile.exists() && audioFile.length() > 0;
        if (fileExists) {
            logDebug("File already exists, proceeding to FFT");
            processCompletedDownload(audioFile, targetPos, targetSongId);
        } else {
            logError("No download in progress and file doesn't exist: {}", audioFile);
            handleDownloadFailure(targetPos);
        }
    }

    private void processCompletedDownload(File audioFile, BlockPos targetPos, long targetSongId) {
        ClientSongManager manager = ClientSongManager.getInstance();
        boolean isValidSong = manager.validateSongId(targetPos, targetSongId);

        if (audioFile.exists() && isValidSong) {
            processAudioFile(audioFile.toURI().toString(), customUrl, targetPos, targetSongId);
        } else if (!audioFile.exists()) {
            logError("Audio file not found after download: {}", audioFile);
            handleDownloadFailure(targetPos);
        } else {
            logDebug("Song ID no longer valid - disc was changed during download");
        }
    }

    // ==================== FFT Completion ====================

    private void processAudioFile(String fileUrl, String cacheKey, BlockPos targetPos, long targetSongId) {
        AudioPreComputer.preComputeFrequenciesFromUrlWithCacheKey(fileUrl, cacheKey, INITIAL_FREQUENCY_DATA_TICKS, targetPos)
            .thenAccept(freqData -> onFFTComplete(freqData, targetPos, targetSongId))
            .exceptionally(e -> {
                logError("FFT analysis failed: {}", e.getMessage());
                handleFFTFailure(ClientSongManager.getInstance(), targetPos);
                return null;
            });
    }

    private void onFFTComplete(FrequencyData freqData, BlockPos targetPos, long targetSongId) {
        ClientSongManager manager = ClientSongManager.getInstance();

        if (!manager.validateSongId(targetPos, targetSongId)) {
            logDebug("FFT complete but songId no longer valid - discarding");
            return;
        }

        if (freqData != null) {
            handleFFTSuccess(manager, freqData, targetPos, targetSongId);
        } else {
            handleFFTFailure(manager, targetPos);
        }
    }

    private void handleFFTSuccess(ClientSongManager manager, FrequencyData freqData,
                                   BlockPos targetPos, long targetSongId) {
        manager.registerSongIfValid(targetPos, freqData, targetSongId);
        manager.setState(targetPos, PlaybackState.READY);
        manager.setLoadingProgress(targetPos, 1f);


        if (hasCustomUrl()) {
            String fileName = RhythmSoundManager.getFileName(customUrl);
            DownloadProgressOverlay.stop(fileName);
        }

        if (loadingCallback != null) {
            loadingCallback.onLoadingComplete(targetPos);
        }

        boolean autoplay = AudioSettings.getInstance().isAutoplayEnabled();
        sendLoadingCompletePacket(targetPos, targetSongId, autoplay);

        logDebug("FFT complete! Duration: {} ticks | State: READY", freqData.getDurationTicks());
    }

    private void handleFFTFailure(ClientSongManager manager, BlockPos targetPos) {
        logError("FFT analysis failed - null result");
        manager.setState(targetPos, PlaybackState.EMPTY);


        if (hasCustomUrl()) {
            String fileName = RhythmSoundManager.getFileName(customUrl);
            DownloadProgressOverlay.stopFailed(fileName);
        }

        if (loadingCallback != null) {
            loadingCallback.onLoadingFailed(targetPos);
        }
    }

    private void sendLoadingCompletePacket(BlockPos targetPos, long targetSongId, boolean autoplay) {
        if (packetSender != null) {
            packetSender.sendToServer(new LoadingCompletePacket(targetPos, targetSongId, autoplay));
            logDebug("Sent LoadingCompletePacket to server (autoplay: {})", autoplay);
        }
    }

    private void handleDownloadFailure(BlockPos targetPos) {
        // Clear download/converting overlay for custom URL discs
        if (hasCustomUrl()) {
            String fileName = RhythmSoundManager.getFileName(customUrl);
            DownloadProgressOverlay.stopFailed(fileName);
        }

        ClientSongManager.getInstance().setState(targetPos, PlaybackState.EMPTY);
        if (loadingCallback != null) {
            loadingCallback.onLoadingFailed(targetPos);
        }
    }

    // ==================== Utility ====================

    private void sleepSafely() {
        try {
            Thread.sleep(DiscInsertedPacket.POST_DOWNLOAD_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[DiscInserted] " + message, args);
        }
    }

    private void logError(String message, Object... args) {
        RhythmConstants.LOGGER.error("[DiscInserted] " + message, args);
    }
}

