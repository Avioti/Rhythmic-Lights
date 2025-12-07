package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.audio.AudioPreComputer;
import com.rhythm.audio.AudioSettings;
import com.rhythm.audio.ClientSongManager;
import com.rhythm.audio.FrequencyData;
import com.rhythm.audio.PlaybackState;
import com.rhythm.audio.RhythmSoundManager;
import com.rhythm.audio.executable.RhythmExecutable;
import com.rhythm.client.gui.DownloadProgressOverlay;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private static final String[] STREAMING_DOMAINS = {"youtube.com", "youtu.be", "spotify.com", "soundcloud.com"};
    private static final String DOWNLOAD_PROCESS_SUFFIX = "/download";
    private static final String FFT_SUBSCRIBER_PREFIX = "fft-";

    private static final int DOWNLOAD_TIMEOUT_MINUTES = 5;
    private static final int POST_DOWNLOAD_DELAY_MS = 500;
    private static final int FILE_CHECK_INTERVAL_MS = 1000;
    private static final int FILE_STABLE_COUNT_REQUIRED = 3;
    private static final int INITIAL_FREQUENCY_DATA_TICKS = 0;

    // ==================== Callbacks (set by client module) ====================

    private static LoadingCallback loadingCallback = null;
    private static ClientPacketSender packetSender = null;

    public interface LoadingCallback {
        void onLoadingStart(BlockPos pos, ResourceLocation soundId, String songTitle);
        void onLoadingProgress(BlockPos pos, float progress);
        void onLoadingComplete(BlockPos pos);
        void onLoadingFailed(BlockPos pos);
    }

    public interface ClientPacketSender {
        void sendToServer(CustomPacketPayload payload);
    }

    public static void setLoadingCallback(LoadingCallback callback) {
        loadingCallback = callback;
    }

    public static LoadingCallback getLoadingCallback() {
        return loadingCallback;
    }

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

    public boolean hasCustomUrl() {
        return customUrl != null && !customUrl.isEmpty();
    }

    public boolean hasSongTitle() {
        return songTitle != null && !songTitle.isEmpty();
    }

    // ==================== Client Handler ====================

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
        if (isStreamingUrl(customUrl)) {
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

    private boolean isStreamingUrl(String url) {
        return java.util.Arrays.stream(STREAMING_DOMAINS)
            .anyMatch(url::contains);
    }

    // ==================== Streaming URL Handling ====================

    private void processStreamingUrl() {
        logDebug("Streaming URL detected - checking cache...");

        String fileName = RhythmSoundManager.getFileName(customUrl);
        File audioFile = RhythmSoundManager.getAudioFile(fileName);
        boolean isDownloading = RhythmExecutable.YT_DLP.isProcessRunning(fileName + DOWNLOAD_PROCESS_SUFFIX);

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
        CompletableFuture.runAsync(() ->
            processDownloadResult(fileName, audioFile, targetPos, targetSongId));
    }

    private void processDownloadResult(String fileName, File audioFile,
                                        BlockPos targetPos, long targetSongId) {
        boolean success = waitForProcessOrFile(fileName, audioFile);

        if (!success) {
            handleDownloadFailure(targetPos);
            return;
        }

        logDebug("Download/conversion COMPLETE - starting FFT analysis...");
        sleepSafely(POST_DOWNLOAD_DELAY_MS);
        processCompletedDownload(audioFile, targetPos, targetSongId);
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

    private boolean waitForProcessOrFile(String fileName, File audioFile) {
        RhythmExecutable.ProcessStream processStream =
            RhythmExecutable.YT_DLP.getProcessStream(fileName + DOWNLOAD_PROCESS_SUFFIX);

        if (processStream != null) {
            return waitForProcessStream(processStream);
        } else {
            logDebug("No process stream found, waiting for file with stability check...");
            return waitForStableFile(audioFile, (int) TimeUnit.MINUTES.toSeconds(DOWNLOAD_TIMEOUT_MINUTES));
        }
    }

    private boolean waitForProcessStream(RhythmExecutable.ProcessStream processStream) {
        CountDownLatch completionLatch = new CountDownLatch(1);
        final boolean[] success = {false};

        subscribeToProcessStream(processStream, completionLatch, success);
        return awaitProcessCompletion(completionLatch, success);
    }

    private void subscribeToProcessStream(RhythmExecutable.ProcessStream processStream,
                                           CountDownLatch latch, boolean[] success) {
        processStream.subscribe(FFT_SUBSCRIBER_PREFIX + pos.toShortString())
            .onComplete(() -> {
                success[0] = true;
                latch.countDown();
            })
            .onError(e -> {
                logError("Download process error: {}", e.getMessage());
                latch.countDown();
            })
            .start();
    }

    private boolean awaitProcessCompletion(CountDownLatch latch, boolean[] success) {
        try {
            boolean completed = latch.await(DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                logError("Download process timed out after {} minutes", DOWNLOAD_TIMEOUT_MINUTES);
                return false;
            }
            return success[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean waitForStableFile(File file, int maxSeconds) {
        long lastSize = -1;
        int stableCount = 0;

        for (int i = 0; i < maxSeconds; i++) {
            if (!sleepSafely(FILE_CHECK_INTERVAL_MS)) {
                return false;
            }

            if (file.exists()) {
                long currentSize = file.length();
                if (currentSize == lastSize && currentSize > 0) {
                    stableCount++;
                    if (stableCount >= FILE_STABLE_COUNT_REQUIRED) {
                        return true;
                    }
                } else {
                    stableCount = 0;
                }
                lastSize = currentSize;
            }
        }
        return false;
    }

    // ==================== FFT Completion ====================

    private void processAudioFile(String fileUrl, String cacheKey, BlockPos targetPos, long targetSongId) {
        AudioPreComputer.preComputeFrequenciesFromUrlWithCacheKey(fileUrl, cacheKey, INITIAL_FREQUENCY_DATA_TICKS, targetPos)
            .thenAccept(freqData -> onFFTComplete(freqData, targetPos, targetSongId));
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

        // Clear download/converting overlay for custom URL discs
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

        // Clear download/converting overlay for custom URL discs
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

    private boolean sleepSafely(int millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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

