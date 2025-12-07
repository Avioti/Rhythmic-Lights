package com.rhythm.mixin;

import com.rhythm.audio.JukeboxStateManager;
import com.rhythm.audio.LinkedJukeboxRegistry;
import com.rhythm.audio.ServerPlaybackTracker;
import com.rhythm.item.RhythmURLDisc;
import com.rhythm.network.DiscInsertedPacket;
import com.rhythm.network.DiscRemovedPacket;
import com.rhythm.network.PacketSender;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Mixin for jukebox disc insertion/removal with staged playback.
 * <p>
 * When disc is inserted, sends packet to client for FFT loading.
 * Music does NOT auto-play until controller click (for linked jukeboxes).
 */
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin extends BlockEntity {

    // ==================== Constants ====================

    @Unique
    private static final String[] URL_NBT_KEYS = {"music_url", "url", "URL", "songUrl"};
    @Unique
    private static final String NBT_LOOP = "loop";
    @Unique
    private static final String NBT_TITLE = "title";
    @Unique
    private static final String RHYTHMMOD_NAMESPACE = "rhythmmod";

    // ==================== Shadow ====================

    @Shadow
    public abstract JukeboxSongPlayer getSongPlayer();

    // ==================== Constructor ====================

    protected JukeboxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==================== Disc Insertion (HEAD) ====================

    @Inject(method = "setTheItem", at = @At("HEAD"))
    private void rhythmmod$onDiscInsertedHead(ItemStack stack, CallbackInfo ci) {
        if (!isServerSide()) {
            return;
        }

        BlockPos pos = this.worldPosition;
        boolean isLinked = LinkedJukeboxRegistry.getInstance().isJukeboxLinked(pos);
        boolean isUrlDisc = RhythmURLDisc.isValidUrlDisc(stack);

        if (!isLinked && !isUrlDisc) {
            logDebug("Jukebox at {} is NOT linked and disc is not URL - vanilla behavior", pos);
            return;
        }

        if (!stack.isEmpty() && stack.has(DataComponents.JUKEBOX_PLAYABLE)) {
            preRegisterDiscInsertion(pos, isLinked, isUrlDisc);
        }
    }

    @Unique
    private void preRegisterDiscInsertion(BlockPos pos, boolean isLinked, boolean isUrlDisc) {
        logDebug("========== DISC INSERTING (HEAD) ==========");
        logDebug("isLinked: {}, isUrlDisc: {}", isLinked, isUrlDisc);

        ServerPlaybackTracker tracker = ServerPlaybackTracker.getInstance();
        long songId = tracker.onDiscInserted(pos, this.level.getGameTime());

        if (isUrlDisc && !isLinked) {
            tracker.markAsUrlDisc(pos, true);
        }

        logDebug("Pre-registered at {} | SongID: {} | State: LOADING", pos, songId);
    }

    // ==================== Disc Insertion (TAIL) ====================

    @Inject(method = "setTheItem", at = @At("TAIL"))
    private void rhythmmod$onDiscInsertedTail(ItemStack stack, CallbackInfo ci) {
        if (!isServerSide()) {
            return;
        }

        BlockPos pos = this.worldPosition;
        boolean isLinked = LinkedJukeboxRegistry.getInstance().isJukeboxLinked(pos);
        boolean isUrlDisc = RhythmURLDisc.isValidUrlDisc(stack);

        if (!isLinked && !isUrlDisc) {
            return;
        }

        JukeboxSong song = this.getSongPlayer().getSong();
        if (song == null) {
            logDebug("========== DISC INSERTED (TAIL) - No song found ==========");
            return;
        }

        processDiscInsertion(stack, pos, song, isLinked, isUrlDisc);
    }

    @Unique
    private void processDiscInsertion(ItemStack stack, BlockPos pos, JukeboxSong song,
                                       boolean isLinked, boolean isUrlDisc) {
        logDebug("========== DISC INSERTED (TAIL) ==========");

        ResourceLocation soundId = song.soundEvent().value().getLocation();
        logDebug("Position: {} | Sound: {} | isLinked: {}, isUrlDisc: {}", pos, soundId, isLinked, isUrlDisc);

        long songId = getOrCreateSongId(pos);
        String customUrl = rhythmmod$extractCustomUrl(stack, soundId);
        String songTitle = rhythmmod$extractSongTitle(stack);
        boolean loop = rhythmmod$extractLoopSetting(stack);

        sendDiscInsertedPacket(pos, soundId, customUrl, songTitle, songId, loop);
        registerWithStateManager(pos, soundId, customUrl, songId, loop);

        if (!isLinked && isUrlDisc) {
            logDebug("URL disc in unlinked jukebox - will auto-play after loading");
            ServerPlaybackTracker.getInstance().markAsAutoplayOnReady(pos, true);
        }
    }

    @Unique
    private long getOrCreateSongId(BlockPos pos) {
        ServerPlaybackTracker tracker = ServerPlaybackTracker.getInstance();
        Long songId = tracker.getSongId(pos);

        if (songId == null) {
            songId = tracker.onDiscInserted(pos, this.level.getGameTime());
            logDebug("WARNING: Had to register in TAIL, SongID: {}", songId);
        } else {
            logDebug("Using SongID from HEAD: {}", songId);
        }

        return songId;
    }

    @Unique
    private void sendDiscInsertedPacket(BlockPos pos, ResourceLocation soundId,
                                         String customUrl, String songTitle, long songId, boolean loop) {
        DiscInsertedPacket packet = new DiscInsertedPacket(pos, soundId, customUrl, songTitle, songId, loop);
        ServerLevel serverLevel = (ServerLevel) this.level;
        ChunkPos chunkPos = new ChunkPos(pos);

        for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            PacketSender.getInstance().sendToPlayer(player, packet);
        }

        logDebug("DiscInsertedPacket sent to clients!");
    }

    @Unique
    private void registerWithStateManager(BlockPos pos, ResourceLocation soundId,
                                           String customUrl, long songId, boolean loop) {
        JukeboxStateManager.getInstance().registerJukebox(
            (ServerLevel) this.level, pos, soundId, customUrl, songId, loop
        );
    }

    // ==================== Disc Removal ====================

    @Inject(method = "popOutTheItem", at = @At("HEAD"))
    private void rhythmmod$onDiscRemoved(CallbackInfo ci) {
        if (!isServerSide()) {
            return;
        }

        BlockPos pos = this.worldPosition;
        ServerPlaybackTracker tracker = ServerPlaybackTracker.getInstance();

        boolean isLinked = LinkedJukeboxRegistry.getInstance().isJukeboxLinked(pos);
        boolean isTracked = tracker.isTracking(pos);

        if (!isLinked && !isTracked) {
            logDebug("Disc removed from unlinked/untracked jukebox at {} - vanilla behavior", pos);
            return;
        }

        processDiscRemoval(pos, isLinked, isTracked, tracker);
    }

    @Unique
    private void processDiscRemoval(BlockPos pos, boolean isLinked, boolean isTracked,
                                     ServerPlaybackTracker tracker) {
        logDebug("========== DISC REMOVED ==========");
        logDebug("isLinked: {}, wasTracked: {}", isLinked, isTracked);

        tracker.onDiscRemoved(pos);
        sendDiscRemovedPacket(pos);

        JukeboxStateManager.getInstance().unregisterJukebox((ServerLevel) this.level, pos);
        logDebug("DiscRemovedPacket sent to clients!");
    }

    @Unique
    private void sendDiscRemovedPacket(BlockPos pos) {
        DiscRemovedPacket packet = new DiscRemovedPacket(pos);
        ServerLevel serverLevel = (ServerLevel) this.level;
        ChunkPos chunkPos = new ChunkPos(pos);

        for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            PacketSender.getInstance().sendToPlayer(player, packet);
        }
    }

    // ==================== NBT Extraction ====================

    @Unique
    private String rhythmmod$extractCustomUrl(ItemStack stack, ResourceLocation soundId) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData != null) {
            String url = findUrlInNbt(customData);
            if (url != null) {
                logDebug("Custom URL disc detected: {}", url);
                return url;
            }
        }

        if (soundId.getNamespace().equals(RHYTHMMOD_NAMESPACE)) {
            logDebug("RhythmMod disc namespace but no URL in NBT");
        }

        return null;
    }

    @Unique
    private String findUrlInNbt(CustomData customData) {
        for (String key : URL_NBT_KEYS) {
            if (customData.contains(key)) {
                return customData.copyTag().getString(key);
            }
        }
        return null;
    }

    @Unique
    private boolean rhythmmod$extractLoopSetting(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(NBT_LOOP) &&
               customData.copyTag().getBoolean(NBT_LOOP);
    }

    @Unique
    private String rhythmmod$extractSongTitle(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains(NBT_TITLE)) {
            return customData.copyTag().getString(NBT_TITLE);
        }
        return "";
    }

    // ==================== Utility ====================

    @Unique
    private boolean isServerSide() {
        return this.level != null && !this.level.isClientSide;
    }

    @Unique
    private void logDebug(String message, Object... args) {
        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("[Jukebox] " + message, args);
        }
    }
}

