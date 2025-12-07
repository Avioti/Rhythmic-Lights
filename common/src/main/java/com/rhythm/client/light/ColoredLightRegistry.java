package com.rhythm.client.light;

import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for colored light sources.
 * <p>
 * Tracks all active colored light sources in the world and provides
 * them to the rendering pipeline for shader-based lighting.
 */
public class ColoredLightRegistry {

    // ==================== Constants ====================

    private static final int MAX_LIGHTS = 64;
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_SHIFT = 4;
    private static final int COLOR_MASK = 0xFF;
    private static final float COLOR_NORMALIZE = 255.0f;
    private static final int CHUNK_KEY_SHIFT = 32;
    private static final long CHUNK_KEY_MASK = 0xFFFFFFFFL;

    // ==================== Singleton ====================

    private static final ColoredLightRegistry INSTANCE = new ColoredLightRegistry();

    public static ColoredLightRegistry getInstance() {
        return INSTANCE;
    }

    private ColoredLightRegistry() {}

    // ==================== State ====================

    private final Map<Long, List<ColoredLightSource>> lightsByChunk = new ConcurrentHashMap<>();
    private final Map<BlockPos, ColoredLightSource> allLights = new ConcurrentHashMap<>();

    // ==================== Registration ====================

    public void registerLight(BlockPos pos, int color, float radius, float intensity) {
        try {
            removeLight(pos);
            addLightToRegistry(pos, color, radius, intensity);
            logLightRegistration(pos, color, radius, intensity);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error registering light at {}: {}", pos, e.getMessage());
        }
    }

    private void addLightToRegistry(BlockPos pos, int color, float radius, float intensity) {
        long chunkPos = getChunkKey(pos);
        ColoredLightSource light = new ColoredLightSource(pos, color, radius, intensity);
        allLights.put(pos, light);
        lightsByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(light);
    }

    private void logLightRegistration(BlockPos pos, int color, float radius, float intensity) {
        if (!RhythmConstants.DEBUG_LIGHTS) {
            return;
        }

        int r = (color >> 16) & COLOR_MASK;
        int g = (color >> 8) & COLOR_MASK;
        int b = color & COLOR_MASK;

        RhythmConstants.LOGGER.debug("Registered light at {} | Color: #{} | RGB: ({}, {}, {}) | " +
            "Normalized: ({}, {}, {}) | Radius: {} | Intensity: {}",
            pos, String.format("%06X", color), r, g, b,
            String.format("%.3f", r / COLOR_NORMALIZE),
            String.format("%.3f", g / COLOR_NORMALIZE),
            String.format("%.3f", b / COLOR_NORMALIZE),
            radius, intensity);
    }

    // ==================== Update ====================

    public void updateLightIntensity(BlockPos pos, float intensity) {
        try {
            ColoredLightSource light = allLights.get(pos);
            if (light != null) {
                light.setIntensity(intensity);
            }
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error updating light intensity at {}: {}", pos, e.getMessage());
        }
    }

    public void updateLightColor(BlockPos pos, int newColor) {
        try {
            ColoredLightSource existingLight = allLights.get(pos);
            if (existingLight != null) {
                registerLight(pos, newColor, existingLight.getRadius(), existingLight.getIntensity());
            }
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error updating light color at {}: {}", pos, e.getMessage());
        }
    }

    // ==================== Removal ====================

    public void removeLight(BlockPos pos) {
        ColoredLightSource light = allLights.remove(pos);
        if (light != null) {
            removeLightFromChunk(pos, light);
            logLightRemoval(pos, light);
        }
    }

    private void removeLightFromChunk(BlockPos pos, ColoredLightSource light) {
        long chunkPos = getChunkKey(pos);
        List<ColoredLightSource> chunkLights = lightsByChunk.get(chunkPos);
        if (chunkLights != null) {
            chunkLights.remove(light);
            if (chunkLights.isEmpty()) {
                lightsByChunk.remove(chunkPos);
            }
        }
    }

    private void logLightRemoval(BlockPos pos, ColoredLightSource light) {
        if (RhythmConstants.DEBUG_LIGHTS) {
            RhythmConstants.LOGGER.debug("Removed light at {} | Color: #{}", pos, String.format("%06X", light.getColor()));
        }
    }

    public void clearAll() {
        lightsByChunk.clear();
        allLights.clear();
    }

    // ==================== Query ====================

    public List<ColoredLightSource> getLightsNear(BlockPos cameraPos, double range) {
        List<ColoredLightSource> nearbyLights = collectLightsInRange(cameraPos, range);
        return sortAndLimitLights(nearbyLights, cameraPos);
    }

    private List<ColoredLightSource> collectLightsInRange(BlockPos cameraPos, double range) {
        List<ColoredLightSource> nearbyLights = new ArrayList<>();
        int chunkRange = (int) Math.ceil(range / CHUNK_SIZE);
        int camChunkX = cameraPos.getX() >> CHUNK_SHIFT;
        int camChunkZ = cameraPos.getZ() >> CHUNK_SHIFT;

        for (int x = -chunkRange; x <= chunkRange; x++) {
            for (int z = -chunkRange; z <= chunkRange; z++) {
                addChunkLights(nearbyLights, camChunkX + x, camChunkZ + z);
            }
        }
        return nearbyLights;
    }

    private void addChunkLights(List<ColoredLightSource> nearbyLights, int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        List<ColoredLightSource> chunkLights = lightsByChunk.get(chunkKey);
        if (chunkLights != null) {
            nearbyLights.addAll(chunkLights);
        }
    }

    private List<ColoredLightSource> sortAndLimitLights(List<ColoredLightSource> lights, BlockPos cameraPos) {
        lights.sort(Comparator.comparingDouble(light -> light.getPosition().distSqr(cameraPos)));
        return lights.size() > MAX_LIGHTS ? lights.subList(0, MAX_LIGHTS) : lights;
    }

    public Collection<ColoredLightSource> getAllLights() {
        return allLights.values();
    }

    // ==================== Chunk Key Utilities ====================

    private long getChunkKey(BlockPos pos) {
        return getChunkKey(pos.getX() >> CHUNK_SHIFT, pos.getZ() >> CHUNK_SHIFT);
    }

    private long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << CHUNK_KEY_SHIFT) | (chunkZ & CHUNK_KEY_MASK);
    }
}

