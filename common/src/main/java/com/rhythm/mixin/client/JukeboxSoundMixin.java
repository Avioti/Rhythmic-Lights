package com.rhythm.mixin.client;

import com.rhythm.audio.LinkedJukeboxRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin to intercept and cancel vanilla jukebox audio playback.
 *
 * RhythmicLights takes over ALL jukebox audio playback via SeekableAudioPlayer.
 * This gives us:
 * - Unified audio handling for vanilla AND custom URL discs
 * - Real-time EQ, bass boost, and surround sound effects
 * - Proper pause/resume with seek capability
 * - Volume control via our AudioSettings system
 *
 * We intercept playLocalSound at the source (ClientLevel) and cancel any
 * RECORDS category sounds that originate from linked jukeboxes.
 */
@Mixin(ClientLevel.class)
public abstract class JukeboxSoundMixin {

    /**
     * Intercept playLocalSound (the main client-side sound playback method).
     * Cancel RECORDS category sounds from linked jukebox positions.
     */
    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
            at = @At("HEAD"),
            cancellable = true)
    private void rhythmmod$onPlayLocalSound(double x, double y, double z,
            SoundEvent soundEvent, SoundSource category, float volume, float pitch, boolean distanceDelay,
            CallbackInfo ci) {

        // Only intercept RECORDS category (jukebox music)
        if (category != SoundSource.RECORDS) {
            return;
        }

        // Check if this position corresponds to a linked jukebox
        BlockPos soundPos = BlockPos.containing(x, y, z);

        if (LinkedJukeboxRegistry.getInstance().isJukeboxLinked(soundPos)) {
            // This is a linked jukebox - cancel vanilla audio!
            // Our SeekableAudioPlayer will handle the actual playback.
            System.out.println("[RhythmMod] Cancelled vanilla jukebox audio at " + soundPos +
                " (handled by RhythmicLights audio engine)");
            ci.cancel();
        }
    }
}
