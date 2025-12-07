package com.rhythm.block;

import com.rhythm.RhythmMod;
import com.rhythm.audio.LinkedJukeboxRegistry;
import com.rhythm.audio.PlaybackState;
import com.rhythm.audio.ServerPlaybackTracker;
import com.rhythm.mixin.accessor.JukeboxSongPlayerAccessor;
import com.rhythm.network.PacketSender;
import com.rhythm.network.PlaybackStatePacket;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity for the DJ Station.
 * <p>
 * Features:
 * <ul>
 *   <li>Owner tracking (UUID + name) for access control</li>
 *   <li>Linked jukebox position for playback control</li>
 *   <li>Autoplay trigger for URL discs</li>
 * </ul>
 */
public class RhythmControllerBlockEntity extends BlockEntity {

    // ==================== Constants ====================

    private static final String NBT_JUKEBOX_POS = "JukeboxPos";
    private static final String NBT_ACTIVE = "Active";
    private static final String NBT_OWNER_UUID = "OwnerUUID";
    private static final String NBT_OWNER_NAME = "OwnerName";

    private static final int AUTOPLAY_RANGE_SQUARED = 64 * 64;
    private static final long INITIAL_SEEK_POSITION = 0L;

    // ==================== State Fields ====================

    @Nullable
    private BlockPos jukeboxPos = null;
    private boolean isActive = false;

    @Nullable
    private UUID ownerUUID = null;
    @Nullable
    private String ownerName = null;

    // ==================== Constructor ====================

    public RhythmControllerBlockEntity(BlockPos pos, BlockState state) {
        super(RhythmMod.RHYTHM_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    // ==================== Owner Management ====================

    /**
     * Sets the owner of this DJ Station.
     */
    public void setOwner(UUID uuid, String name) {
        this.ownerUUID = uuid;
        this.ownerName = name;
        syncToClients();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nullable
    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwner(UUID playerUUID) {
        return ownerUUID != null && ownerUUID.equals(playerUUID);
    }

    public boolean hasOwner() {
        return ownerUUID != null;
    }

    // ==================== Jukebox Link ====================

    public void setJukeboxPos(BlockPos pos) {
        unlinkOldJukebox(pos);
        this.jukeboxPos = pos;
        linkNewJukebox(pos);
        syncToClients();
    }

    private void unlinkOldJukebox(BlockPos newPos) {
        if (this.jukeboxPos != null && !this.jukeboxPos.equals(newPos)) {
            LinkedJukeboxRegistry.getInstance().unlinkJukebox(this.jukeboxPos);
        }
    }

    private void linkNewJukebox(BlockPos pos) {
        if (pos != null) {
            LinkedJukeboxRegistry.getInstance().linkJukeboxToController(this.worldPosition, pos);
        }
    }

    @Nullable
    public BlockPos getJukeboxPos() {
        return jukeboxPos;
    }

    // ==================== Active State ====================

    public void setActive(boolean active) {
        this.isActive = active;
        setChanged();
    }

    public boolean isActive() {
        return isActive;
    }

    // ==================== Sync Helper ====================

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ==================== NBT Serialization ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (jukeboxPos != null) {
            tag.putLong(NBT_JUKEBOX_POS, jukeboxPos.asLong());
        }
        tag.putBoolean(NBT_ACTIVE, isActive);
        if (ownerUUID != null) {
            tag.putUUID(NBT_OWNER_UUID, ownerUUID);
        }
        if (ownerName != null) {
            tag.putString(NBT_OWNER_NAME, ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadJukeboxPos(tag);
        isActive = tag.getBoolean(NBT_ACTIVE);
        loadOwnerData(tag);
    }

    private void loadJukeboxPos(CompoundTag tag) {
        if (tag.contains(NBT_JUKEBOX_POS)) {
            jukeboxPos = BlockPos.of(tag.getLong(NBT_JUKEBOX_POS));
            if (jukeboxPos != null) {
                LinkedJukeboxRegistry.getInstance().linkJukeboxToController(this.worldPosition, jukeboxPos);
            }
        }
    }

    private void loadOwnerData(CompoundTag tag) {
        if (tag.hasUUID(NBT_OWNER_UUID)) {
            ownerUUID = tag.getUUID(NBT_OWNER_UUID);
        }
        if (tag.contains(NBT_OWNER_NAME)) {
            ownerName = tag.getString(NBT_OWNER_NAME);
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Called when the block is removed. Cleans up the jukebox link.
     */
    public void onRemoved() {
        LinkedJukeboxRegistry.getInstance().unlinkController(this.worldPosition);
    }

    // ==================== Network Sync ====================

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    // ==================== Autoplay ====================

    /**
     * Triggers playback for autoplay feature.
     * Called by LoadingCompletePacket when autoplay is enabled.
     *
     * @param level The server level
     * @return true if playback was started successfully
     */
    public boolean triggerPlayback(Level level) {
        if (level.isClientSide || jukeboxPos == null) {
            return false;
        }

        ServerPlaybackTracker tracker = ServerPlaybackTracker.getInstance();

        if (!isReadyForAutoplay(tracker)) {
            return false;
        }

        JukeboxBlockEntity jukebox = getJukeboxEntity(level);
        if (jukebox == null) {
            return false;
        }

        long startTime = level.getGameTime();

        if (!tracker.play(jukeboxPos, startTime)) {
            RhythmConstants.LOGGER.error("Autoplay: Failed to update tracker state for {}", jukeboxPos);
            return false;
        }

        triggerVanillaPlayback(level, jukebox, tracker);
        sendPlaybackStateToNearbyPlayers(level, startTime);

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Autoplay: Started playback at {}", jukeboxPos);
        }
        return true;
    }

    private boolean isReadyForAutoplay(ServerPlaybackTracker tracker) {
        PlaybackState currentState = tracker.getState(jukeboxPos);
        if (currentState != PlaybackState.READY) {
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Autoplay: Cannot start - state is {}, expected READY", currentState);
            }
            return false;
        }
        return true;
    }

    @Nullable
    private JukeboxBlockEntity getJukeboxEntity(Level level) {
        if (level.getBlockEntity(jukeboxPos) instanceof JukeboxBlockEntity jukebox) {
            return jukebox;
        }
        RhythmConstants.LOGGER.error("Autoplay: Jukebox not found at {}", jukeboxPos);
        return null;
    }

    private void triggerVanillaPlayback(Level level, JukeboxBlockEntity jukebox, ServerPlaybackTracker tracker) {
        try {
            var songPlayer = jukebox.getSongPlayer();
            Holder<JukeboxSong> songHolder = ((JukeboxSongPlayerAccessor) songPlayer).rhythmmod$getSongHolder();

            if (songHolder == null) {
                songHolder = restoreCachedSongHolder(songPlayer, tracker);
            }

            if (songHolder != null) {
                songPlayer.play(level, songHolder);
                if (RhythmConstants.DEBUG_AUDIO) {
                    RhythmConstants.LOGGER.debug("Autoplay: Triggered vanilla jukebox playback");
                }
            }
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Autoplay: Error triggering jukebox playback: {}", e.getMessage());
        }
    }

    @Nullable
    private Holder<JukeboxSong> restoreCachedSongHolder(Object songPlayer, ServerPlaybackTracker tracker) {
        Holder<JukeboxSong> cachedHolder = tracker.getCachedSongHolder(jukeboxPos);
        if (cachedHolder != null) {
            ((JukeboxSongPlayerAccessor) songPlayer).rhythmmod$setSongHolder(cachedHolder);
        }
        return cachedHolder;
    }

    private void sendPlaybackStateToNearbyPlayers(Level level, long startTime) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PlaybackStatePacket packet = new PlaybackStatePacket(
            jukeboxPos, PlaybackState.PLAYING, startTime, INITIAL_SEEK_POSITION
        );

        for (var player : serverLevel.players()) {
            if (player.blockPosition().distSqr(jukeboxPos) < AUTOPLAY_RANGE_SQUARED) {
                PacketSender.getInstance().sendToPlayer(player, packet);
            }
        }
    }
}