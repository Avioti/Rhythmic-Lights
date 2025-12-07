package com.rhythm.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to get and set the song holder from JukeboxSongPlayer.
 * This allows us to properly call play() with the correct Holder type,
 * and to restore the song holder after pausing.
 */
@Mixin(JukeboxSongPlayer.class)
public interface JukeboxSongPlayerAccessor {

    /**
     * Get the song Holder from the JukeboxSongPlayer.
     * This is the internal field that holds the song reference.
     */
    @Accessor("song")
    Holder<JukeboxSong> rhythmmod$getSongHolder();

    /**
     * Set the song Holder in the JukeboxSongPlayer.
     * Used to restore the song holder after pausing.
     */
    @Accessor("song")
    void rhythmmod$setSongHolder(Holder<JukeboxSong> song);
}

