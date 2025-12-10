package com.rhythm.audio.cache;

import com.rhythm.util.RhythmConstants;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.AudioFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches decoded PCM audio data for instant seek/resume playback.
 *
 * When a disc is first loaded, the FFT analysis already decodes the audio to PCM.
 * We cache that decoded PCM data here so that when the user pauses and resumes,
 * we can immediately start playing from the seek position without re-decoding.
 *
 * This eliminates the ~0.5-1 second delay between pressing play and hearing sound.
 */
public class AudioPcmCache {
    private static final AudioPcmCache INSTANCE = new AudioPcmCache();

    // Cache key: either soundEventId.toString() or customUrl
    private final Map<String, CachedPcmData> cache = new ConcurrentHashMap<>();

    // Maximum cache size in bytes (100MB default - about 10 minutes of 48kHz stereo audio)
    private static final long MAX_CACHE_SIZE = 100 * 1024 * 1024;
    private volatile long currentCacheSize = 0;

    private AudioPcmCache() {}

    public static AudioPcmCache getInstance() {
        return INSTANCE;
    }

    /**
     * Store decoded PCM data in the cache.
     *
     * @param key The cache key (soundEventId.toString() or customUrl)
     * @param pcmData The decoded PCM audio data
     * @param format The audio format of the PCM data
     */
    public void put(String key, byte[] pcmData, AudioFormat format) {
        if (pcmData == null || pcmData.length == 0) {
            return;
        }

        // Check if we need to evict entries
        while (currentCacheSize + pcmData.length > MAX_CACHE_SIZE && !cache.isEmpty()) {
            // Remove oldest entry (simple eviction strategy)
            String oldestKey = cache.keySet().iterator().next();
            remove(oldestKey);
            RhythmConstants.debugAudio("Cache evicted {} to make room", oldestKey);
        }

        CachedPcmData existing = cache.get(key);
        if (existing != null) {
            currentCacheSize -= existing.data.length;
        }

        cache.put(key, new CachedPcmData(pcmData, format));
        currentCacheSize += pcmData.length;

        RhythmConstants.debugAudio("Cached {} bytes of PCM data for key: {} (total cache: {} MB)",
            pcmData.length, key, String.format("%.2f", currentCacheSize / (1024.0 * 1024.0)));
    }

    /**
     * Get cached PCM data for a sound.
     *
     * @param soundEventId The sound event ID (for vanilla sounds)
     * @param customUrl The custom URL (for custom URL sounds), or null
     * @return The cached PCM data, or null if not cached
     */
    public CachedPcmData get(ResourceLocation soundEventId, String customUrl) {
        String key = getCacheKey(soundEventId, customUrl);
        return cache.get(key);
    }

    /**
     * Get the cache key for a sound.
     */
    public static String getCacheKey(ResourceLocation soundEventId, String customUrl) {
        if (customUrl != null && !customUrl.isEmpty()) {
            return "url:" + customUrl;
        }
        return "sound:" + (soundEventId != null ? soundEventId.toString() : "unknown");
    }

    /**
     * Remove cached data for a key.
     */
    public void remove(String key) {
        CachedPcmData removed = cache.remove(key);
        if (removed != null) {
            currentCacheSize -= removed.data.length;
        }
    }

    /**
     * Check if we have cached PCM data for a sound.
     */
    public boolean contains(ResourceLocation soundEventId, String customUrl) {
        return cache.containsKey(getCacheKey(soundEventId, customUrl));
    }

    /**
     * Clear the entire cache.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        currentCacheSize = 0;
        RhythmConstants.debugAudio("Cleared cache ({} entries)", size);
    }

    /**
     * Get current cache size in bytes.
     */
    public long getCacheSize() {
        return currentCacheSize;
    }

    /**
     * Get number of cached entries.
     */
    public int getEntryCount() {
        return cache.size();
    }

    /**
     * Debug method: Print all cached keys.
     */
    public void debugPrintCacheKeys() {
        RhythmConstants.debugAudio("Cache contains {} entries:", cache.size());
        for (String key : cache.keySet()) {
            CachedPcmData data = cache.get(key);
            RhythmConstants.debugAudio("  - {} ({} bytes)", key, data != null ? data.data.length : 0);
        }
    }

    /**
     * Container for cached PCM data with its format info.
     */
    public static class CachedPcmData {
        public final byte[] data;
        public final AudioFormat format;
        public final long createdAt;

        public CachedPcmData(byte[] data, AudioFormat format) {
            this.data = data;
            this.format = format;
            this.createdAt = System.currentTimeMillis();
        }
    }
}

