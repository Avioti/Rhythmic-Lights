package com.rhythm.audio;
public class SongData {
    private final float[] bassMap;
    private final long startTime;
    private final int durationTicks;
    private volatile boolean isLoading;
    public SongData(float[] bassMap, long startTime) {
        this.bassMap = bassMap;
        this.startTime = startTime;
        this.durationTicks = bassMap.length;
        this.isLoading = false;
    }
    public SongData(long startTime) {
        this.bassMap = new float[0];
        this.startTime = startTime;
        this.durationTicks = 0;
        this.isLoading = true;
    }
    public float getBassIntensity(long currentTick) {
        if (isLoading || bassMap.length == 0) {
            return 0.0f;
        }
        long ticksElapsed = currentTick - startTime;
        if (ticksElapsed < 0 || ticksElapsed >= bassMap.length) {
            return 0.0f;
        }
        return bassMap[(int) ticksElapsed];
    }
    public void setData(float[] newBassMap) {
        if (isLoading && newBassMap != null) {
            System.arraycopy(newBassMap, 0, this.bassMap, 0, Math.min(newBassMap.length, this.bassMap.length));
            this.isLoading = false;
        }
    }
    public boolean isLoading() {
        return isLoading;
    }
    public long getStartTime() {
        return startTime;
    }
    public int getDurationTicks() {
        return durationTicks;
    }
}