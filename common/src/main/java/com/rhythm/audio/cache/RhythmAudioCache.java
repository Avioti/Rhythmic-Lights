package com.rhythm.audio.cache;

import com.rhythm.audio.io.RhythmSoundManager;
import com.rhythm.util.RhythmConstants;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Manages cached audio files for URL-based music discs.
 * <p>
 * Provides functionality for:
 * <ul>
 *   <li>Finding cached audio files</li>
 *   <li>Providing audio streams from cache</li>
 *   <li>Waiting for downloads in progress</li>
 * </ul>
 * <p>
 * Note: Downloading is handled by {@link RhythmSoundManager}.
 */
public class RhythmAudioCache {

    // ==================== Constants ====================

    private static final Path CACHE_DIR = RhythmConstants.getDownloadsPath();
    private static final String[] SUPPORTED_EXTENSIONS = {".ogg", ".mp3", ".m4a", ".webm", ".wav"};

    private static final int FILE_STABILITY_CHECK_COUNT = 2;
    private static final long FILE_CHECK_INTERVAL_MS = 500L;
    private static final long MILLIS_PER_SECOND = 1000L;

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String FILE_URL_PREFIX = "file:/";

    // ==================== Constructor ====================

    private RhythmAudioCache() {
        // Utility class - prevent instantiation
    }

    // ==================== Public API ====================

    /**
     * Gets an InputStream for a URL, using the cache if available.
     *
     * @param url the URL to get audio for (can be a web URL or file:// URL)
     * @return InputStream for the audio data, or null if unavailable
     */
    public static InputStream getAudioStream(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            if (url.startsWith(FILE_URL_PREFIX)) {
                return getStreamFromFileUrl(url);
            }
            return getStreamFromCachedUrl(url);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error getting audio stream: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Waits for a cached file to appear (for when download is in progress).
     *
     * @param url            the URL to wait for
     * @param maxWaitSeconds maximum seconds to wait
     * @return the cached file path, or null if not found within timeout
     */
    public static Path waitForCachedFile(String url, int maxWaitSeconds) {
        long startTime = System.currentTimeMillis();
        long maxWaitMs = maxWaitSeconds * MILLIS_PER_SECOND;

        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.debug("Waiting for cached file (max {}s)...", maxWaitSeconds);
        }

        Path result = pollForStableFile(url, startTime, maxWaitMs);

        if (result == null) {
            RhythmConstants.LOGGER.warn("Timeout waiting for cached file");
        }

        return result;
    }

    /**
     * Finds a cached file in the cache directory.
     *
     * @param url the URL to find in cache
     * @return path to the cached file, or null if not found
     */
    public static Path findCachedFile(String url) {
        String fileHash = getFileHash(url);

        for (String ext : SUPPORTED_EXTENSIONS) {
            Path path = CACHE_DIR.resolve(fileHash + ext);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Gets the file hash (SHA-256) for a URL.
     *
     * @param url the URL to hash
     * @return hex string of the SHA-256 hash
     */
    public static String getFileHash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    // ==================== File Stream Helpers ====================

    private static InputStream getStreamFromFileUrl(String url) {
        try {
            URI uri = new URI(url);
            File file = new File(uri);

            if (!file.exists()) {
                RhythmConstants.LOGGER.warn("File not found: {}", file);
                return null;
            }

            if (RhythmConstants.DEBUG_DOWNLOADS) {
                RhythmConstants.LOGGER.debug("Reading from file: {}", file);
            }
            return new BufferedInputStream(Files.newInputStream(file.toPath()));
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error parsing file URL: {}", e.getMessage());
            return null;
        }
    }

    private static InputStream getStreamFromCachedUrl(String url) throws IOException {
        Path cached = findCachedFile(url);

        if (cached == null) {
            RhythmConstants.LOGGER.warn("No cached file found for URL");
            return null;
        }

        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.debug("Using cached file: {}", cached);
        }
        return new BufferedInputStream(Files.newInputStream(cached));
    }

    // ==================== Wait/Poll Helpers ====================

    private static Path pollForStableFile(String url, long startTime, long maxWaitMs) {
        long lastSize = -1;
        int stableCount = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            Path cached = findCachedFile(url);

            if (cached != null) {
                long currentSize = getFileSizeOrZero(cached);

                if (isFileSizeStable(currentSize, lastSize)) {
                    stableCount++;
                    if (stableCount >= FILE_STABILITY_CHECK_COUNT) {
                        logFileStable(cached);
                        return cached;
                    }
                } else {
                    stableCount = 0;
                }
                lastSize = currentSize;
            }

            if (!sleepInterruptibly(FILE_CHECK_INTERVAL_MS)) {
                break;
            }
        }

        return null;
    }

    private static boolean isFileSizeStable(long currentSize, long lastSize) {
        return currentSize == lastSize && currentSize > 0;
    }

    private static void logFileStable(Path path) {
        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.debug("File stable: {}", path);
        }
    }

    private static long getFileSizeOrZero(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean sleepInterruptibly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
