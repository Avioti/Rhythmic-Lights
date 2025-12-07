package com.rhythm.audio;

import com.rhythm.audio.executable.RhythmExecutable;
import com.rhythm.client.gui.DownloadProgressOverlay;
import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sound downloading and playback for URL discs.
 * Uses SeekableAudioPlayer for playback with seeking support.
 */
public class RhythmSoundManager {

    // ==================== Constants ====================

    private static final String AUDIO_EXTENSION = ".ogg";
    private static final String DOWNLOAD_PROCESS_SUFFIX = "/download";
    private static final String MAIN_SUBSCRIBER_ID = "main";
    private static final String PROGRESS_PREFIX = "PROGRESS:";
    private static final String WARNING_PREFIX = "WARNING:";
    private static final String ERROR_PREFIX = "ERROR:";
    private static final float DEFAULT_VOLUME = 1.0f;
    private static final long START_POSITION_MS = 0L;

    public static final Path AUDIO_DIRECTORY = RhythmConstants.getDownloadsPath();

    // ==================== State ====================

    private static final ConcurrentHashMap<BlockPos, PendingSound> pendingSounds = new ConcurrentHashMap<>();

    private record PendingSound(String fileName, String url, boolean loop) {}

    // ==================== Download API ====================

    /**
     * Downloads audio from a URL using yt-dlp and converts it to OGG format.
     *
     * @param url      the source URL to download from
     * @param fileName the target file name (without extension)
     */
    public static void downloadSound(String url, String fileName) {
        if (!isPlayerAvailable()) {
            return;
        }

        ensureDirectoryExists();
        DownloadProgressOverlay.set(fileName, 0);

        startDownloadProcess(url, fileName);
    }

    private static boolean isPlayerAvailable() {
        return Minecraft.getInstance().player != null;
    }

    private static void ensureDirectoryExists() {
        AUDIO_DIRECTORY.toFile().mkdirs();
    }

    private static void startDownloadProcess(String url, String fileName) {
        String processId = fileName + DOWNLOAD_PROCESS_SUFFIX;

        RhythmExecutable.YT_DLP.executeCommand(processId, buildYtDlpArguments(url, fileName))
            .subscribe(MAIN_SUBSCRIBER_ID)
            .onOutput(line -> handleDownloadOutput(line, fileName))
            .onError(error -> handleDownloadError(fileName))
            .onComplete(() -> handleDownloadComplete(fileName))
            .start();
    }

    private static String[] buildYtDlpArguments(String url, String fileName) {
        return new String[] {
            url,
            "-x", "-q", "--progress", "--add-metadata", "--no-playlist",
            "--progress-template", "PROGRESS: %(progress._percent)d",
            "--newline",
            "--break-match-filter", "ext~=3gp|aac|flv|m4a|mov|mp3|mp4|ogg|wav|webm|opus",
            "--audio-format", "vorbis",
            "--audio-quality", "96K",
            "--postprocessor-args", "ffmpeg:-ac 1 -c:a libvorbis",
            "-P", AUDIO_DIRECTORY.toString(),
            "--ffmpeg-location", RhythmExecutable.FFMPEG.getFilePath().getParent().toString(),
            "-o", fileName + ".%(ext)s"
        };
    }

    private static void handleDownloadOutput(String line, String fileName) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            logInfo(line);
            return;
        }

        String prefix = line.substring(0, colonIndex + 1);
        String message = line.substring(colonIndex + 1).trim();

        switch (prefix) {
            case PROGRESS_PREFIX -> parseAndUpdateProgress(fileName, message);
            case WARNING_PREFIX -> RhythmConstants.LOGGER.warn(message);
            case ERROR_PREFIX -> RhythmConstants.LOGGER.error(message);
            default -> logInfo(line);
        }
    }

    private static void parseAndUpdateProgress(String fileName, String message) {
        try {
            int progress = Integer.parseInt(message);
            DownloadProgressOverlay.set(fileName, progress);
        } catch (NumberFormatException e) {
            // Non-numeric progress value, ignore
        }
    }

    private static void handleDownloadError(String fileName) {
        DownloadProgressOverlay.stopFailed(fileName);
        deleteSound(fileName);
    }

    private static void handleDownloadComplete(String fileName) {
        DownloadProgressOverlay.stop(fileName);
        notifyDownloadComplete(fileName);
    }

    private static void logInfo(String message) {
        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.info(message);
        }
    }

    // ==================== Sound Operations ====================

    /**
     * Deletes all downloaded files matching the given fileName.
     *
     * @param fileName the base file name to match
     */
    public static void deleteSound(String fileName) {
        File[] filesToDelete = AUDIO_DIRECTORY.toFile().listFiles(
            file -> file.getName().contains(fileName)
        );
        if (filesToDelete == null) {
            return;
        }

        for (File file : filesToDelete) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Plays a sound at the specified position using SeekableAudioPlayer.
     *
     * @param pos the block position to play at
     * @param url the source URL (used to derive file name)
     */
    public static void playSound(BlockPos pos, String url) {
        String fileName = getFileName(url);
        File audioFile = getAudioFile(fileName);

        if (!audioFile.exists()) {
            RhythmConstants.LOGGER.warn("Cannot play - file doesn't exist: {}", audioFile);
            return;
        }

        SeekableAudioPlayer.getInstance().playFromPosition(
            pos,
            RhythmConstants.PLACEHOLDER_SOUND_ID,
            audioFile.toURI().toString(),
            START_POSITION_MS,
            DEFAULT_VOLUME
        );

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Started playback at {}", pos);
        }
    }

    /**
     * Stops sound playback at the specified position.
     *
     * @param pos the block position to stop playback at
     */
    public static void stopSound(BlockPos pos) {
        SeekableAudioPlayer.getInstance().stop(pos);
        pendingSounds.remove(pos);
    }

    // ==================== Sound Queue Management ====================

    /**
     * Adds a sound to the pending queue (waiting for download).
     *
     * @param fileName the file name
     * @param pos      the block position
     * @param loop     whether to loop playback
     */
    public static void addSound(String fileName, BlockPos pos, boolean loop) {
        pendingSounds.put(pos, new PendingSound(fileName, "", loop));
    }

    /**
     * Queues a sound to be tracked during download.
     * <p>
     * Note: Does not auto-play on completion. The autoplay flow via
     * LoadingCompletePacket handles starting playback after FFT is ready.
     *
     * @param fileName the file name
     * @param pos      the block position
     * @param url      the source URL
     */
    public static void queueSound(String fileName, BlockPos pos, String url) {
        pendingSounds.put(pos, new PendingSound(fileName, url, false));

        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.debug("Queued sound for position {} (waiting for FFT)", pos);
        }
    }

    /**
     * Notifies that a download has completed and cleans up pending entries.
     */
    private static void notifyDownloadComplete(String fileName) {
        pendingSounds.entrySet().removeIf(entry ->
            entry.getValue().fileName.equals(fileName)
        );
    }

    /**
     * Removes a sound from the queue and optionally cancels the download.
     *
     * @param fileName the file name
     * @param pos      the block position
     * @param shouldCancel    whether to cancel the download if no subscribers remain
     */
    public static void unqueueSound(String fileName, BlockPos pos, boolean shouldCancel) {
        pendingSounds.remove(pos);

        String processId = fileName + DOWNLOAD_PROCESS_SUFFIX;
        RhythmExecutable.ProcessStream processStream = RhythmExecutable.YT_DLP.getProcessStream(processId);

        if (processStream == null) {
            return;
        }

        processStream.unsubscribe(pos.toString());

        if (shouldCancel && processStream.subscriberCount() <= 1) {
            RhythmExecutable.YT_DLP.killProcess(processStream.getId());
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Gets the audio file for a given file name.
     *
     * @param fileName the file name (without extension)
     * @return the File object for the audio file
     */
    public static File getAudioFile(String fileName) {
        return AUDIO_DIRECTORY.resolve(fileName + AUDIO_EXTENSION).toFile();
    }

    /**
     * Generates a file name (SHA-256 hash) from a URL.
     *
     * @param url the source URL
     * @return the hashed file name, or empty string if URL is null/empty
     */
    public static String getFileName(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return DigestUtils.sha256Hex(url);
    }
}

