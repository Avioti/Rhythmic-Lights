package com.rhythm.audio;

import com.rhythm.item.RhythmURLDisc;
import com.rhythm.network.DiscInsertedPacket;
import com.rhythm.network.PacketSender;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager that tracks jukeboxes with discs inserted.
 * Used to restore state when players join or chunks reload.
 * <p>
 * This allows players to rejoin a world and have their jukebox discs
 * already loaded and ready to play without having to re-insert them.
 */
public class JukeboxStateManager {

    // ==================== Constants ====================

    private static final JukeboxStateManager INSTANCE = new JukeboxStateManager();
    private static final String KEY_SEPARATOR = ":";
    private static final int SONG_ID_TIME_MULTIPLIER = 1000;
    private static final int SONG_ID_POSITION_MODULO = 1000;
    private static final String NBT_RHYTHM_URL = "rhythm_url";
    private static final String NBT_RHYTHM_LOOP = "rhythm_loop";
    private static final String NBT_RHYTHM_TITLE = "title";

    // ==================== State ====================

    private final Map<String, JukeboxDiscInfo> activeJukeboxes = new ConcurrentHashMap<>();

    // ==================== Data Classes ====================

    /**
     * Stores information about a jukebox with a disc inserted.
     */
    public record JukeboxDiscInfo(
        BlockPos pos,
        ResourceLocation soundEventId,
        String customUrl,
        long songId,
        boolean loop
    ) {}

    // ==================== Singleton ====================

    private JukeboxStateManager() {}

    public static JukeboxStateManager getInstance() {
        return INSTANCE;
    }

    // ==================== Registration ====================

    /**
     * Registers a jukebox that has a disc inserted.
     *
     * @param level        the server level
     * @param pos          the jukebox position
     * @param soundEventId the sound event ID
     * @param customUrl    the custom URL (or null for vanilla discs)
     * @param songId       the song ID
     * @param loop         whether to loop playback
     */
    public void registerJukebox(ServerLevel level, BlockPos pos, ResourceLocation soundEventId,
                                 String customUrl, long songId, boolean loop) {
        String key = makeKey(level, pos);
        activeJukeboxes.put(key, new JukeboxDiscInfo(pos, soundEventId, customUrl, songId, loop));

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            boolean hasUrl = customUrl != null;
            RhythmConstants.LOGGER.debug("Registered jukebox at {} | URL: {}", pos, hasUrl ? "yes" : "no");
        }
    }

    /**
     * Unregisters a jukebox when its disc is removed.
     */
    public void unregisterJukebox(ServerLevel level, BlockPos pos) {
        String key = makeKey(level, pos);
        activeJukeboxes.remove(key);

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("Unregistered jukebox at {}", pos);
        }
    }

    /**
     * Checks if a jukebox is registered.
     */
    public boolean isRegistered(ServerLevel level, BlockPos pos) {
        String key = makeKey(level, pos);
        return activeJukeboxes.containsKey(key);
    }

    // ==================== Player Join Handling ====================

    /**
     * Called when a player joins the server.
     * Sends DiscInsertedPackets for all jukeboxes in chunks near the player.
     */
    public void onPlayerJoin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String dimensionPrefix = level.dimension().location() + KEY_SEPARATOR;

        int restoredCount = restoreJukeboxesForPlayer(player, level, dimensionPrefix);

        if (restoredCount > 0) {
            RhythmConstants.LOGGER.info("Restored {} jukebox(es) for player {}",
                restoredCount, player.getName().getString());
        }
    }

    private int restoreJukeboxesForPlayer(ServerPlayer player, ServerLevel level, String dimensionPrefix) {
        int count = 0;

        for (Map.Entry<String, JukeboxDiscInfo> entry : Map.copyOf(activeJukeboxes).entrySet()) {
            String key = entry.getKey();
            JukeboxDiscInfo info = entry.getValue();

            if (!isValidForRestore(key, dimensionPrefix, level, info.pos)) {
                continue;
            }

            if (!validateAndCleanupJukebox(key, level, info.pos)) {
                continue;
            }

            sendDiscInfoToPlayer(player, level, info);
            count++;
        }

        return count;
    }

    private boolean isValidForRestore(String key, String dimensionPrefix, ServerLevel level, BlockPos pos) {
        return key.startsWith(dimensionPrefix) && level.isLoaded(pos);
    }

    private boolean validateAndCleanupJukebox(String key, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
            activeJukeboxes.remove(key);
            return false;
        }

        ItemStack disc = jukebox.getTheItem();
        if (disc.isEmpty() || !disc.has(DataComponents.JUKEBOX_PLAYABLE)) {
            activeJukeboxes.remove(key);
            return false;
        }

        return true;
    }

    private void sendDiscInfoToPlayer(ServerPlayer player, ServerLevel level, JukeboxDiscInfo info) {
        DiscInfo updatedInfo = getUpdatedDiscInfo(level, info);

        ServerPlaybackTracker.getInstance().onDiscInserted(info.pos, level.getGameTime());

        DiscInsertedPacket packet = new DiscInsertedPacket(
            info.pos,
            info.soundEventId,
            updatedInfo.url,
            updatedInfo.songTitle,
            updatedInfo.songId,
            updatedInfo.loop
        );
        PacketSender.getInstance().sendToPlayer(player, packet);

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("Sent disc info to {} for jukebox at {}",
                player.getName().getString(), info.pos);
        }
    }

    private record DiscInfo(String url, String songTitle, long songId, boolean loop) {}

    private DiscInfo getUpdatedDiscInfo(ServerLevel level, JukeboxDiscInfo info) {
        String customUrl = info.customUrl;
        String songTitle = "";
        boolean loop = info.loop;

        JukeboxBlockEntity jukebox = (JukeboxBlockEntity) level.getBlockEntity(info.pos);
        if (jukebox != null) {
            ItemStack disc = jukebox.getTheItem();
            if (RhythmURLDisc.isValidUrlDisc(disc)) {
                CustomData customData = disc.get(DataComponents.CUSTOM_DATA);
                if (customData != null && customData.contains(NBT_RHYTHM_URL)) {
                    customUrl = customData.copyTag().getString(NBT_RHYTHM_URL);
                    loop = customData.copyTag().getBoolean(NBT_RHYTHM_LOOP);
                    songTitle = customData.copyTag().getString(NBT_RHYTHM_TITLE);
                }
            }
        }

        long newSongId = generateSongId(level.getGameTime(), info.pos);
        return new DiscInfo(customUrl, songTitle, newSongId, loop);
    }

    private long generateSongId(long gameTime, BlockPos pos) {
        return gameTime * SONG_ID_TIME_MULTIPLIER + Math.abs(pos.hashCode() % SONG_ID_POSITION_MODULO);
    }

    // ==================== Chunk Load Handling ====================

    /**
     * Called when a chunk is loaded.
     * Re-validates jukeboxes in that chunk.
     */
    public void onChunkLoad(ServerLevel level, ChunkPos chunkPos) {
        for (Map.Entry<String, JukeboxDiscInfo> entry : activeJukeboxes.entrySet()) {
            JukeboxDiscInfo info = entry.getValue();

            if (!isInChunk(info.pos, chunkPos)) {
                continue;
            }

            validateJukeboxOnChunkLoad(level, entry.getKey(), info.pos);
        }
    }

    private boolean isInChunk(BlockPos pos, ChunkPos chunkPos) {
        return new ChunkPos(pos).equals(chunkPos);
    }

    private void validateJukeboxOnChunkLoad(ServerLevel level, String key, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
            if (jukebox.getTheItem().isEmpty()) {
                activeJukeboxes.remove(key);
                if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
                    RhythmConstants.LOGGER.debug("Removed empty jukebox at {} on chunk load", pos);
                }
            }
        } else {
            activeJukeboxes.remove(key);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Generates a unique key for a jukebox position in a dimension.
     */
    private String makeKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + KEY_SEPARATOR + pos.asLong();
    }

    /**
     * Clears all state (called on server stop).
     */
    public void clear() {
        activeJukeboxes.clear();
        RhythmConstants.LOGGER.info("Cleared all jukebox state");
    }

    /**
     * Returns the number of tracked jukeboxes.
     */
    public int getTrackedCount() {
        return activeJukeboxes.size();
    }
}

