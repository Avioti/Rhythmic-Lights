package com.rhythm.block.controller;

import com.rhythm.audio.state.PlaybackState;
import com.rhythm.audio.state.ServerPlaybackTracker;
import com.rhythm.mixin.accessor.JukeboxSongPlayerAccessor;
import com.rhythm.network.DJSettingsPacket;
import com.rhythm.network.OpenDJControllerPacket;
import com.rhythm.network.PacketSender;
import com.rhythm.network.PlaybackStatePacket;
import com.rhythm.util.RhythmConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Controller block that manages song data for connected rhythm bulbs.
 * <p>
 * Right-clicking opens the DJ Controller GUI for playback control.
 * Only the owner can control the station.
 */
public class RhythmControllerBlock extends Block implements EntityBlock {

    // ==================== Constants ====================

    private static final float DEFAULT_MASTER_VOLUME = 1.0f;
    private static final float DEFAULT_BASS_BOOST = 0.0f;
    private static final int EQ_BAND_COUNT = 12;
    private static final long INITIAL_POSITION = 0L;

    // ==================== Constructor ====================

    public RhythmControllerBlock(Properties properties) {
        super(properties);
    }

    // ==================== Block Entity ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RhythmControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof RhythmControllerBlockEntity controller) {
                controller.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ==================== Interaction ====================

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof RhythmControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }

        if (!controller.hasOwner()) {
            notifyUnclaimedStation(player);
            return InteractionResult.CONSUME;
        }

        if (!controller.isOwner(player.getUUID())) {
            notifyNotOwner(player, controller.getOwnerName());
            return InteractionResult.CONSUME;
        }

        openDJControllerGUI(level, pos, player, controller);
        return InteractionResult.CONSUME;
    }

    private void notifyUnclaimedStation(Player player) {
        player.displayClientMessage(
            Component.literal("This DJ Station is unclaimed! ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("\nShift+Right-Click with Tuning Wand to claim it.")
                    .withStyle(ChatFormatting.GRAY)),
            true
        );
    }

    private void notifyNotOwner(Player player, String ownerName) {
        player.displayClientMessage(
            Component.literal("Only ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(ownerName != null ? ownerName : "the owner")
                    .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" can control this DJ Station!")
                    .withStyle(ChatFormatting.RED)),
            true
        );
    }

    // ==================== DJ Controller GUI ====================

    private void openDJControllerGUI(Level level, BlockPos controllerPos, Player player,
                                      RhythmControllerBlockEntity controller) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BlockPos jukeboxPos = controller.getJukeboxPos();
        float[] eqBands = new float[EQ_BAND_COUNT];
        boolean loopEnabled = false;

        OpenDJControllerPacket packet = new OpenDJControllerPacket(
            controllerPos, jukeboxPos, DEFAULT_MASTER_VOLUME, DEFAULT_BASS_BOOST, eqBands, loopEnabled
        );
        PacketSender.getInstance().sendToPlayer(serverPlayer, packet);

        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("Sent OpenDJControllerPacket to {}", player.getName().getString());
        }
    }

    // ==================== Playback Control ====================

    private boolean startPlayback(Level level, BlockPos jukeboxPos,
                                  RhythmControllerBlockEntity controller,
                                  ServerPlaybackTracker tracker) {
        JukeboxBlockEntity jukebox = getJukeboxEntity(level, jukeboxPos);
        if (jukebox == null) {
            return false;
        }

        boolean isResuming = isResumingFromPause(tracker, jukeboxPos);

        Long pausedAt = tracker.getPausedAt(jukeboxPos);
        long startTime = calculateStartTime(level, jukeboxPos, tracker, isResuming);

        if (!tracker.play(jukeboxPos, startTime)) {
            return false;
        }

        preserveSongHolder(jukebox, jukeboxPos, tracker);

        long seekToTick = (isResuming && pausedAt != null) ? pausedAt : INITIAL_POSITION;
        sendStatePacket(level, jukeboxPos, PlaybackState.PLAYING, startTime, seekToTick);

        logPlaybackAction("Started", jukeboxPos, startTime, isResuming);
        return true;
    }

    private boolean pausePlayback(Level level, BlockPos jukeboxPos,
                                  RhythmControllerBlockEntity controller,
                                  ServerPlaybackTracker tracker) {
        JukeboxBlockEntity jukebox = getJukeboxEntity(level, jukeboxPos);
        if (jukebox == null) {
            return false;
        }

        long pausedAt = calculateCurrentPosition(level, jukeboxPos, tracker);
        cacheSongHolderForResume(jukebox, jukeboxPos, tracker);

        if (!tracker.stop(jukeboxPos, pausedAt)) {
            return false;
        }

        stopVanillaJukebox(level, jukebox);
        sendStatePacket(level, jukeboxPos, PlaybackState.STOPPED, pausedAt, INITIAL_POSITION);

        logPlaybackAction("Paused", jukeboxPos, pausedAt, false);
        return true;
    }

    private boolean stopPlayback(Level level, BlockPos jukeboxPos,
                                 RhythmControllerBlockEntity controller,
                                 ServerPlaybackTracker tracker) {
        JukeboxBlockEntity jukebox = getJukeboxEntity(level, jukeboxPos);
        if (jukebox == null) {
            return false;
        }

        cacheSongHolderForResume(jukebox, jukeboxPos, tracker);

        if (!tracker.stop(jukeboxPos, INITIAL_POSITION)) {
            return false;
        }

        stopVanillaJukebox(level, jukebox);
        sendStatePacket(level, jukeboxPos, PlaybackState.STOPPED, INITIAL_POSITION, INITIAL_POSITION);

        logPlaybackAction("Stopped", jukeboxPos, INITIAL_POSITION, false);
        return true;
    }

    // ==================== Playback Helpers ====================

    @Nullable
    private JukeboxBlockEntity getJukeboxEntity(Level level, BlockPos jukeboxPos) {
        if (level.getBlockEntity(jukeboxPos) instanceof JukeboxBlockEntity jukebox) {
            return jukebox;
        }
        RhythmConstants.LOGGER.error("Jukebox not found at {}", jukeboxPos);
        return null;
    }

    private boolean isResumingFromPause(ServerPlaybackTracker tracker, BlockPos jukeboxPos) {
        Long pausedAt = tracker.getPausedAt(jukeboxPos);
        return pausedAt != null && pausedAt > 0;
    }

    private long calculateStartTime(Level level, BlockPos jukeboxPos,
                                     ServerPlaybackTracker tracker, boolean isResuming) {
        if (!isResuming) {
            return level.getGameTime();
        }

        Long pausedAt = tracker.getPausedAt(jukeboxPos);
        long adjustedStartTime = level.getGameTime() - pausedAt;

        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Resuming from tick {} | Adjusted start time: {}",
                pausedAt, adjustedStartTime);
        }
        return adjustedStartTime;
    }

    private long calculateCurrentPosition(Level level, BlockPos jukeboxPos, ServerPlaybackTracker tracker) {
        Long startTime = tracker.getStartTime(jukeboxPos);
        return (startTime != null) ? level.getGameTime() - startTime : INITIAL_POSITION;
    }

    private void preserveSongHolder(JukeboxBlockEntity jukebox, BlockPos jukeboxPos,
                                     ServerPlaybackTracker tracker) {
        try {
            var songPlayer = jukebox.getSongPlayer();
            Holder<JukeboxSong> songHolder = ((JukeboxSongPlayerAccessor) songPlayer).rhythmmod$getSongHolder();

            if (songHolder == null) {
                songHolder = restoreCachedSongHolder(songPlayer, jukeboxPos, tracker);
            }

            if (songHolder != null) {
                tracker.cacheSongHolder(jukeboxPos, songHolder);
            } else {
                RhythmConstants.LOGGER.error("No song holder found in jukebox at {}", jukeboxPos);
            }
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error accessing song holder: {}", e.getMessage());
        }
    }

    @Nullable
    private Holder<JukeboxSong> restoreCachedSongHolder(Object songPlayer, BlockPos jukeboxPos,
                                                         ServerPlaybackTracker tracker) {
        Holder<JukeboxSong> cachedHolder = tracker.getCachedSongHolder(jukeboxPos);
        if (cachedHolder != null) {
            ((JukeboxSongPlayerAccessor) songPlayer).rhythmmod$setSongHolder(cachedHolder);
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Restored cached song holder for {}", jukeboxPos);
            }
        }
        return cachedHolder;
    }

    private void cacheSongHolderForResume(JukeboxBlockEntity jukebox, BlockPos jukeboxPos,
                                           ServerPlaybackTracker tracker) {
        var songPlayer = jukebox.getSongPlayer();
        Holder<JukeboxSong> songHolder = ((JukeboxSongPlayerAccessor) songPlayer).rhythmmod$getSongHolder();
        if (songHolder != null) {
            tracker.cacheSongHolder(jukeboxPos, songHolder);
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Cached song holder for resume at {}", jukeboxPos);
            }
        }
    }

    private void stopVanillaJukebox(Level level, JukeboxBlockEntity jukebox) {
        try {
            jukebox.getSongPlayer().stop(level, jukebox.getBlockState());
            if (RhythmConstants.DEBUG_AUDIO) {
                RhythmConstants.LOGGER.debug("Stopped vanilla jukebox playback");
            }
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error stopping jukebox playback: {}", e.getMessage());
        }
    }

    private void logPlaybackAction(String action, BlockPos jukeboxPos, long timeValue, boolean isResume) {
        if (RhythmConstants.DEBUG_AUDIO) {
            if (isResume) {
                RhythmConstants.LOGGER.debug("{} (resume) playback at {} | Time: {}", action, jukeboxPos, timeValue);
            } else {
                RhythmConstants.LOGGER.debug("{} playback at {} | Time: {}", action, jukeboxPos, timeValue);
            }
        }
    }

    // ==================== DJ Command Handler ====================

    /**
     * Handle a DJ settings command from the client.
     * Called by the DJSettingsPacket handler on the server.
     *
     * @param level The server level
     * @param controllerPos The controller position
     * @param command The command to execute (PLAY, PAUSE, STOP)
     * @param player The player who sent the command (for ownership check)
     * @return true if the command was executed successfully
     */
    public static boolean handleDJCommand(Level level, BlockPos controllerPos,
                                          DJSettingsPacket.Command command,
                                          Player player) {
        if (level.isClientSide) {
            return false;
        }

        if (!(level.getBlockEntity(controllerPos) instanceof RhythmControllerBlockEntity controller)) {
            return false;
        }

        // Check ownership
        if (!controller.isOwner(player.getUUID())) {
            return false;
        }

        BlockPos jukeboxPos = controller.getJukeboxPos();
        if (jukeboxPos == null) {
            return false;
        }

        ServerPlaybackTracker tracker = ServerPlaybackTracker.getInstance();
        PlaybackState currentState = tracker.getState(jukeboxPos);

        // Create a temporary block instance to access the playback methods
        RhythmControllerBlock block = (RhythmControllerBlock) level.getBlockState(controllerPos).getBlock();

        switch (command) {
            case PLAY -> {
                if (currentState == PlaybackState.READY || currentState == PlaybackState.STOPPED) {
                    return block.startPlayback(level, jukeboxPos, controller, tracker);
                }
            }
            case PAUSE -> {
                // Pause at current position - can resume from here
                if (currentState == PlaybackState.PLAYING) {
                    return block.pausePlayback(level, jukeboxPos, controller, tracker);
                }
            }
            case STOP -> {
                // Stop and reset to beginning - next play starts from 0
                if (currentState == PlaybackState.PLAYING) {
                    return block.stopPlayback(level, jukeboxPos, controller, tracker);
                }
            }
            default -> {
                // SYNC_SETTINGS doesn't need special handling here
                return true;
            }
        }

        return false;
    }

    /**
     * Send a PlaybackStatePacket to all players tracking the jukebox chunk.
     *
     * @param level The level
     * @param jukeboxPos The jukebox position
     * @param state The new playback state
     * @param timeValue Start time for PLAYING, paused tick for STOPPED
     * @param seekToTick If > 0, clients should use SeekableAudioPlayer to seek to this tick
     */
    private void sendStatePacket(Level level, BlockPos jukeboxPos, PlaybackState state, long timeValue, long seekToTick) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PlaybackStatePacket packet = new PlaybackStatePacket(jukeboxPos, state, timeValue, seekToTick);
        ChunkPos chunkPos = new ChunkPos(jukeboxPos);

        for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            PacketSender.getInstance().sendToPlayer(player, packet);
        }
    }
}

