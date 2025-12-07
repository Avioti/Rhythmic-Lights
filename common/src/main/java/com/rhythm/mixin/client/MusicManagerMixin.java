package com.rhythm.mixin.client;

import com.rhythm.audio.SeekableAudioPlayer;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent background music from starting when RhythmMod jukebox audio is active.
 * <p>
 * This works in conjunction with SoundEngineMixin which handles:
 * <ul>
 *   <li>Fading out currently playing music</li>
 *   <li>Pausing music at zero volume</li>
 *   <li>Fading music back in when jukebox stops</li>
 * </ul>
 * <p>
 * This mixin specifically prevents NEW music from starting while jukebox is active.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    /**
     * Intercepts music start to prevent new tracks when jukebox is active.
     * This ensures no new background music starts while the player is listening to jukebox audio.
     */
    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void rhythmmod$onStartPlaying(Music music, CallbackInfo ci) {
        if (rhythmmod$shouldBlockMusic()) {
            ci.cancel();
        }
    }

    /**
     * Checks if background music should be blocked.
     *
     * @return true if jukebox audio is active and music should not start
     */
    @Unique
    private boolean rhythmmod$shouldBlockMusic() {
        SeekableAudioPlayer player = SeekableAudioPlayer.getInstance();
        return player.shouldSuppressBackgroundMusic();
    }
}

