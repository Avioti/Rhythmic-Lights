package com.rhythm.mixin;

import com.rhythm.audio.LinkedJukeboxRegistry;
import com.rhythm.audio.PlaybackState;
import com.rhythm.audio.ServerPlaybackTracker;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controls JukeboxSongPlayer behavior for staged playback.
 * <p>
 * Blocks vanilla auto-play for linked jukeboxes and URL discs.
 * SeekableAudioPlayer handles all actual audio playback.
 */
@Mixin(JukeboxSongPlayer.class)
public abstract class JukeboxSongPlayerMixin {

    // ==================== Shadow Fields ====================

    @Shadow @Final
    private BlockPos blockPos;

    @Shadow
    private Holder<JukeboxSong> song;

    @Shadow
    private long ticksSinceSongStarted;

    // ==================== Play Interception ====================

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void rhythmmod$onPlay(LevelAccessor level, Holder<JukeboxSong> songHolder, CallbackInfo ci) {
        if (!isServerSide(level) || !shouldIntercept()) {
            return;
        }

        initializeSongState(songHolder);
        logPlayBlocked();
        ci.cancel();
    }

    @Unique
    private void initializeSongState(Holder<JukeboxSong> songHolder) {
        this.song = songHolder;
        this.ticksSinceSongStarted = 0;
    }

    @Unique
    private void logPlayBlocked() {
        if (RhythmConstants.DEBUG_AUDIO) {
            PlaybackState state = ServerPlaybackTracker.getInstance().getState(this.blockPos);
            boolean isLinked = LinkedJukeboxRegistry.getInstance().isJukeboxLinked(this.blockPos);
            boolean isUrlDisc = ServerPlaybackTracker.getInstance().isUrlDisc(this.blockPos);
            RhythmConstants.LOGGER.debug("Blocked vanilla audio at {} (state: {}, linked: {}, urlDisc: {})",
                this.blockPos, state, isLinked, isUrlDisc);
        }
    }

    // ==================== Stop Interception ====================

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void rhythmmod$onStop(LevelAccessor level, BlockState state, CallbackInfo ci) {
        if (!isServerSide(level) || !shouldIntercept()) {
            return;
        }

        PlaybackState playbackState = ServerPlaybackTracker.getInstance().getState(this.blockPos);

        if (shouldPreserveSongHolder(playbackState)) {
            logStopBlocked(playbackState);
            ci.cancel();
        } else {
            logStopAllowed(playbackState);
        }
    }

    @Unique
    private boolean shouldPreserveSongHolder(PlaybackState state) {
        return state == PlaybackState.LOADING || state == PlaybackState.READY;
    }

    @Unique
    private void logStopBlocked(PlaybackState state) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Blocking stop at {} to preserve song holder (state: {})",
                this.blockPos, state);
        }
    }

    @Unique
    private void logStopAllowed(PlaybackState state) {
        if (RhythmConstants.DEBUG_AUDIO) {
            RhythmConstants.LOGGER.debug("Allowing stop at {} (state: {})", this.blockPos, state);
        }
    }

    // ==================== Utility ====================

    @Unique
    private boolean isServerSide(LevelAccessor level) {
        return level != null && !level.isClientSide();
    }

    @Unique
    private boolean shouldIntercept() {
        boolean isLinked = LinkedJukeboxRegistry.getInstance().isJukeboxLinked(this.blockPos);
        boolean isUrlDisc = ServerPlaybackTracker.getInstance().isUrlDisc(this.blockPos);
        return isLinked || isUrlDisc;
    }
}

