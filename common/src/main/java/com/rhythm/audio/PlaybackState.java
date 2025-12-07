package com.rhythm.audio;

/**
 * Represents the playback state for a jukebox managed by RhythmMod.
 * This state machine controls the staged playback system where music
 * and lights only start after FFT loading is complete and the controller is clicked.
 */
public enum PlaybackState {
    /**
     * No disc in the jukebox
     */
    EMPTY,

    /**
     * Disc inserted, FFT analysis is running
     * Loading HUD is visible with progress percentage
     */
    LOADING,

    /**
     * FFT analysis complete, waiting for controller click to start
     * "Ready" indicator shown to player
     */
    READY,

    /**
     * Music and lights are actively playing and synchronized
     */
    PLAYING,

    /**
     * Playback paused mid-song (can resume from this position)
     */
    STOPPED;

    /**
     * Check if this state allows starting playback
     */
    public boolean canPlay() {
        return this == READY || this == STOPPED;
    }

    /**
     * Check if this state means music is actively playing
     */
    public boolean isPlaying() {
        return this == PLAYING;
    }

    /**
     * Check if this state has a disc loaded (in any state)
     */
    public boolean hasDisc() {
        return this != EMPTY;
    }

    /**
     * Check if FFT data should be available
     */
    public boolean hasFFTData() {
        return this == READY || this == PLAYING || this == STOPPED;
    }
}

