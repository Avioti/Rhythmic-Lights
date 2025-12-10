package com.rhythm.audio.io;

import com.rhythm.audio.executable.RhythmExecutable;
import com.rhythm.util.RhythmConstants;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extracts metadata (title, artist, duration) from URLs using yt-dlp.
 * <p>
 * Allows automatic population of disc info from YouTube, SoundCloud, etc.
 */
public class VideoMetadata {

    // ==================== Constants ====================

    private static final String FIELD_DELIMITER = "|||";
    private static final String FIELD_DELIMITER_REGEX = "\\|\\|\\|";
    private static final int EXPECTED_FIELD_COUNT = 4;

    private static final String PROCESS_ID_PREFIX = "metadata-";
    private static final long METADATA_TIMEOUT_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 100L;

    private static final String PLACEHOLDER_NA = "NA";
    private static final String PLACEHOLDER_NONE = "None";
    private static final String DEFAULT_TITLE = "Unknown Title";
    private static final String DISPLAY_SEPARATOR = " - ";

    // ==================== Fields ====================

    private final String title;
    private final String artist;
    private final String uploader;
    private final int durationSeconds;
    private final String url;

    // ==================== Constructor ====================

    public VideoMetadata(String title, String artist, String uploader, int durationSeconds, String url) {
        this.title = nullToEmpty(title);
        this.artist = nullToEmpty(artist);
        this.uploader = nullToEmpty(uploader);
        this.durationSeconds = durationSeconds;
        this.url = url;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    // ==================== Getters ====================

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getUploader() {
        return uploader;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Gets the display name for the disc tooltip.
     * Prefers "Artist - Title" format, falls back to just title if no artist.
     */
    public String getDisplayName() {
        if (!artist.isEmpty()) {
            return artist + DISPLAY_SEPARATOR + title;
        }
        if (!uploader.isEmpty()) {
            return uploader + DISPLAY_SEPARATOR + title;
        }
        return title;
    }

    @Override
    public String toString() {
        return "VideoMetadata{title='" + title + "', artist='" + artist +
               "', uploader='" + uploader + "', duration=" + durationSeconds + '}';
    }

    // ==================== Metadata Fetching ====================

    /**
     * Fetches metadata for a URL using yt-dlp.
     * This is a fast operation that doesn't download the video.
     *
     * @param url the video/audio URL
     * @return CompletableFuture containing the metadata, or null on failure
     */
    public static CompletableFuture<VideoMetadata> fetchMetadata(String url) {
        return CompletableFuture.supplyAsync(() -> fetchMetadataSync(url));
    }

    private static VideoMetadata fetchMetadataSync(String url) {
        try {
            if (RhythmConstants.DEBUG_DOWNLOADS) {
                RhythmConstants.LOGGER.debug("Fetching metadata for: {}", url);
            }

            MetadataResult result = executeYtDlpMetadataCommand(url);

            if (result.hasError()) {
                RhythmConstants.LOGGER.error("Error fetching metadata: {}", result.getErrorMessage());
                return null;
            }

            if (result.isEmpty()) {
                RhythmConstants.LOGGER.warn("No metadata received for URL");
                return null;
            }

            return parseMetadataOutput(result.getOutput(), url);

        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to fetch metadata: {}", e.getMessage());
            return null;
        }
    }

    // ==================== yt-dlp Execution ====================

    private static MetadataResult executeYtDlpMetadataCommand(String url) throws InterruptedException {
        String processId = PROCESS_ID_PREFIX + url.hashCode();

        AtomicReference<String> outputRef = new AtomicReference<>("");
        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        AtomicReference<Throwable> error = new AtomicReference<>(null);

        startYtDlpProcess(processId, url, outputRef, completed, error);
        waitForCompletion(completed);

        return new MetadataResult(outputRef.get(), error.get());
    }

    private static void startYtDlpProcess(String processId, String url,
                                           AtomicReference<String> outputRef,
                                           AtomicReference<Boolean> completed,
                                           AtomicReference<Throwable> error) {
        String printFormat = "%(title)s" + FIELD_DELIMITER + "%(artist)s" + FIELD_DELIMITER +
                            "%(uploader)s" + FIELD_DELIMITER + "%(duration)s";

        RhythmExecutable.YT_DLP.executeCommand(
            processId,
            "--no-download",
            "--print", printFormat,
            "--no-playlist",
            "--no-warnings",
            url
        ).subscribe(processId)
            .onOutput(line -> captureDelimitedOutput(line, outputRef))
            .onComplete(() -> completed.set(true))
            .onError(e -> {
                error.set(e);
                completed.set(true);
            })
            .start();
    }

    private static void captureDelimitedOutput(String line, AtomicReference<String> outputRef) {
        if (line.contains(FIELD_DELIMITER)) {
            outputRef.set(line);
        }
    }

    private static void waitForCompletion(AtomicReference<Boolean> completed) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!completed.get() && System.currentTimeMillis() - startTime < METADATA_TIMEOUT_MS) {
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    // ==================== Parsing ====================

    private static VideoMetadata parseMetadataOutput(String output, String url) {
        String[] parts = output.split(FIELD_DELIMITER_REGEX, -1);

        if (parts.length < EXPECTED_FIELD_COUNT) {
            RhythmConstants.LOGGER.warn("Invalid metadata format: {}", output);
            return null;
        }

        String title = cleanTitle(parts[0]);
        String artist = cleanMetadataField(parts[1]);
        String uploader = cleanMetadataField(parts[2]);
        int duration = parseDuration(parts[3]);

        VideoMetadata metadata = new VideoMetadata(title, artist, uploader, duration, url);

        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.debug("Fetched metadata: {}", metadata);
        }

        return metadata;
    }

    private static String cleanTitle(String rawTitle) {
        String cleaned = cleanMetadataField(rawTitle);
        return cleaned.isEmpty() ? DEFAULT_TITLE : cleaned;
    }

    private static String cleanMetadataField(String field) {
        if (field == null) {
            return "";
        }
        String cleaned = field.trim();
        if (isPlaceholderValue(cleaned)) {
            return "";
        }
        return cleaned;
    }

    private static boolean isPlaceholderValue(String value) {
        return value.equalsIgnoreCase(PLACEHOLDER_NA) || value.equals(PLACEHOLDER_NONE);
    }

    private static int parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return 0;
        }

        String trimmed = durationStr.trim();
        if (isPlaceholderValue(trimmed)) {
            return 0;
        }

        try {
            return (int) Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== Result Container ====================

    private record MetadataResult(String output, Throwable error) {
        boolean hasError() {
            return error != null;
        }

        boolean isEmpty() {
            return output == null || output.isEmpty();
        }

        String getOutput() {
            return output != null ? output : "";
        }

        String getErrorMessage() {
            return error != null ? error.getMessage() : "";
        }
    }
}

