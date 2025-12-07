package com.rhythm.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.rhythm.audio.SeekableAudioPlayer;
import com.rhythm.mixin.accessor.SoundEngineAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Mixin to fade and pause background music when RhythmMod jukebox audio is active.
 * <p>
 * Features:
 * <ul>
 *   <li>Smooth fade out when jukebox starts playing</li>
 *   <li>Smooth fade in when jukebox stops</li>
 *   <li>Pauses music at zero volume to prevent restart</li>
 *   <li>Handles volume adjustments respecting user settings</li>
 * </ul>
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    // ==================== Constants ====================

    @Unique
    private static final int TICKS_TO_FADE_OUT = 20;

    @Unique
    private static final int TICKS_TO_FADE_IN = 40;

    @Unique
    private static final float FADE_OUT_STEP = 1.0f / TICKS_TO_FADE_OUT;

    @Unique
    private static final float FADE_IN_STEP = 1.0f / TICKS_TO_FADE_IN;

    // ==================== State ====================

    @Unique
    private float rhythmmod$musicVolumeFactor = 1.0f;

    @Unique
    private boolean rhythmmod$isMusicPaused = false;

    // ==================== Tick Injection ====================

    /**
     * Injects into the sound engine tick to handle music fading.
     */
    @Inject(method = "tick(Z)V", at = @At("HEAD"))
    private void rhythmmod$onTick(boolean isPaused, CallbackInfo ci) {
        if (rhythmmod$shouldFadeMusic()) {
            rhythmmod$fadeOutMusic();
        } else {
            rhythmmod$fadeInMusic();
        }
    }

    // ==================== Fade Logic ====================

    @Unique
    private boolean rhythmmod$shouldFadeMusic() {
        SeekableAudioPlayer player = SeekableAudioPlayer.getInstance();
        return player.shouldSuppressBackgroundMusic();
    }

    @Unique
    private void rhythmmod$fadeOutMusic() {
        if (rhythmmod$musicVolumeFactor > 0) {
            rhythmmod$musicVolumeFactor = Math.max(0, rhythmmod$musicVolumeFactor - FADE_OUT_STEP);
            rhythmmod$applyMusicVolume();
        } else {
            rhythmmod$pauseAllMusic();
        }
    }

    @Unique
    private void rhythmmod$fadeInMusic() {
        if (rhythmmod$musicVolumeFactor < 1) {
            rhythmmod$musicVolumeFactor = Math.min(1, rhythmmod$musicVolumeFactor + FADE_IN_STEP);
            rhythmmod$applyMusicVolume();
        }
    }

    // ==================== Volume Application ====================

    @Unique
    private void rhythmmod$applyMusicVolume() {
        SoundEngineAccessor accessor = (SoundEngineAccessor) this;

        if (!accessor.rhythmmod$isLoaded()) {
            return;
        }

        Collection<SoundInstance> musicInstances = accessor.rhythmmod$getInstanceBySource().get(SoundSource.MUSIC);

        for (SoundInstance sound : musicInstances) {
            rhythmmod$applyVolumeToInstance(accessor, sound);
        }
    }

    @Unique
    private void rhythmmod$applyVolumeToInstance(SoundEngineAccessor accessor, SoundInstance sound) {
        ChannelAccess.ChannelHandle handle = accessor.rhythmmod$getInstanceToChannel().get(sound);

        if (handle == null) {
            return;
        }

        float baseVolume = sound.getVolume();
        float adjustedVolume = accessor.rhythmmod$calculateVolume(
            baseVolume * rhythmmod$musicVolumeFactor,
            SoundSource.MUSIC
        );

        handle.execute(source -> source.setVolume(adjustedVolume));

        rhythmmod$handlePauseState(handle);
    }

    @Unique
    private void rhythmmod$handlePauseState(ChannelAccess.ChannelHandle handle) {
        if (rhythmmod$musicVolumeFactor <= 0 && !rhythmmod$isMusicPaused) {
            handle.execute(Channel::pause);
            rhythmmod$isMusicPaused = true;
        } else if (rhythmmod$musicVolumeFactor > 0 && rhythmmod$isMusicPaused) {
            handle.execute(Channel::unpause);
            rhythmmod$isMusicPaused = false;
        }
    }

    @Unique
    private void rhythmmod$pauseAllMusic() {
        SoundEngineAccessor accessor = (SoundEngineAccessor) this;

        if (!accessor.rhythmmod$isLoaded()) {
            return;
        }

        Collection<SoundInstance> musicInstances = accessor.rhythmmod$getInstanceBySource().get(SoundSource.MUSIC);

        for (SoundInstance sound : musicInstances) {
            ChannelAccess.ChannelHandle handle = accessor.rhythmmod$getInstanceToChannel().get(sound);
            if (handle != null) {
                handle.execute(Channel::pause);
            }
        }

        rhythmmod$isMusicPaused = true;
    }
}

