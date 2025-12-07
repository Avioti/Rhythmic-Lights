package com.rhythm.client.gui;

import com.rhythm.util.RhythmConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side HUD overlay that shows a loading animation when a disc is inserted.
 * <p>
 * Features:
 * <ul>
 *   <li>Animated "Loading..." text with fading dots</li>
 *   <li>RGB cycling colors (Red/Green on Christmas)</li>
 *   <li>Pulsing song name display</li>
 * </ul>
 */
public class LoadingOverlay {

    // ==================== Animation Timing ====================

    private static final long FADE_CYCLE_MS = 1500;
    private static final long DOT_DELAY_MS = 200;
    private static final long RGB_CYCLE_MS = 3000;
    private static final long PULSE_CYCLE_MS = 800;
    private static final long CHRISTMAS_CYCLE_MS = 2000;

    // ==================== Layout ====================

    private static final double MAX_DISTANCE = 32.0;
    private static final int CENTER_Y_OFFSET = 40;
    private static final int SONG_NAME_Y_OFFSET = 4;
    private static final int DOT_SPACING = 2;
    private static final int DOT_COUNT = 3;
    private static final int DOT_RGB_OFFSET = 200;

    // ==================== Colors ====================

    private static final int COLOR_CHRISTMAS_RED = 0xFFFF2222;
    private static final int COLOR_CHRISTMAS_GREEN = 0xFF22DD22;

    // ==================== Animation Factors ====================

    private static final float PULSE_MIN = 0.6f;
    private static final float PULSE_AMPLITUDE = 0.4f;
    private static final float SCALE_PULSE_MIN = 1.0f;
    private static final float SCALE_PULSE_AMPLITUDE = 0.05f;
    private static final float DOT_FADE_IN_END = 0.3f;
    private static final float DOT_FADE_OUT_START = 0.7f;
    private static final float DOT_GAMMA = 0.7f;
    private static final float VIBRANT_SATURATION = 0.9f;

    // ==================== Text Constants ====================

    private static final String TEXT_LOADING = "Loading";
    private static final String CHAR_DOT = ".";
    private static final String PREFIX_MUSIC_DISC = "music_disc.";
    private static final String PREFIX_RECORDS = "records/";

    // ==================== State ====================

    private static final Map<BlockPos, LoadingInfo> activeLoaders = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    // ==================== Public API ====================

    public static void show(BlockPos pos, ResourceLocation soundId, String songTitle) {
        int randomColor = generateRandomVibrantColor();
        activeLoaders.put(pos, new LoadingInfo(soundId, songTitle, 0f, System.currentTimeMillis(), randomColor));
        if (RhythmConstants.DEBUG_GUI) {
            RhythmConstants.LOGGER.debug("Loading overlay shown for {} - Title: {}", pos, songTitle);
        }
    }

    public static void updateProgress(BlockPos pos, float progress) {
        LoadingInfo info = activeLoaders.get(pos);
        if (info != null) {
            activeLoaders.put(pos, new LoadingInfo(info.soundId, info.songTitle, progress, info.startTime, info.songNameColor));
        }
    }

    public static void hide(BlockPos pos) {
        activeLoaders.remove(pos);
        if (RhythmConstants.DEBUG_GUI) {
            RhythmConstants.LOGGER.debug("Loading overlay hidden for {}", pos);
        }
    }

    public static boolean isActive() {
        return !activeLoaders.isEmpty();
    }

    public static boolean isActiveFor(BlockPos pos) {
        return activeLoaders.containsKey(pos);
    }

    public static void clearAll() {
        activeLoaders.clear();
    }

    // ==================== Rendering ====================

    public static void render(GuiGraphics graphics, float partialTick) {
        if (activeLoaders.isEmpty() || DownloadProgressOverlay.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        LoadingInfo nearestInfo = findNearestLoader(mc.player.blockPosition());
        if (nearestInfo == null || nearestInfo.progress >= 1.0f) {
            return;
        }

        renderLoadingState(graphics, mc, nearestInfo);
    }

    private static LoadingInfo findNearestLoader(BlockPos playerPos) {
        LoadingInfo nearestInfo = null;
        double nearestDistSq = Double.MAX_VALUE;
        double maxDistSq = MAX_DISTANCE * MAX_DISTANCE;

        for (Map.Entry<BlockPos, LoadingInfo> entry : activeLoaders.entrySet()) {
            double distSq = playerPos.distSqr(entry.getKey());
            if (distSq < nearestDistSq && distSq <= maxDistSq) {
                nearestDistSq = distSq;
                nearestInfo = entry.getValue();
            }
        }
        return nearestInfo;
    }

    private static void renderLoadingState(GuiGraphics graphics, Minecraft mc, LoadingInfo info) {
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2 + CENTER_Y_OFFSET;
        long elapsed = System.currentTimeMillis() - info.startTime;

        renderAnimatedLoading(graphics, mc, centerX, centerY, elapsed);

        // Prefer the song title from the disc, fallback to parsing the sound ID
        String songName = getDisplayName(info);
        if (songName != null && !songName.isEmpty()) {
            int songNameY = centerY + mc.font.lineHeight + SONG_NAME_Y_OFFSET;
            renderPulsingSongName(graphics, mc, songName, centerX, songNameY, elapsed, info.songNameColor);
        }
    }

    private static String getDisplayName(LoadingInfo info) {
        // Prefer explicit song title from URL disc
        if (info.songTitle != null && !info.songTitle.isEmpty()) {
            return info.songTitle;
        }
        // Fallback to parsing the sound event ID
        return getSongDisplayName(info.soundId);
    }

    // ==================== Animated Loading Text ====================

    private static void renderAnimatedLoading(GuiGraphics graphics, Minecraft mc, int centerX, int y, long elapsed) {
        int rgbColor = getRGBColor(elapsed);
        int loadingWidth = mc.font.width(TEXT_LOADING);
        int dotWidth = mc.font.width(CHAR_DOT);
        int totalWidth = loadingWidth + DOT_SPACING + (dotWidth + DOT_SPACING) * DOT_COUNT;
        int startX = centerX - totalWidth / 2;

        graphics.drawString(mc.font, TEXT_LOADING, startX, y, rgbColor, false);
        renderAnimatedDots(graphics, mc, startX + loadingWidth + DOT_SPACING, y, elapsed, dotWidth);
    }

    private static void renderAnimatedDots(GuiGraphics graphics, Minecraft mc, int dotX, int y,
                                            long elapsed, int dotWidth) {
        for (int i = 0; i < DOT_COUNT; i++) {
            long dotElapsed = elapsed - (i * DOT_DELAY_MS);
            float dotAlpha = calculateDotAlpha(dotElapsed);

            int dotRgbColor = getRGBColor(elapsed + i * DOT_RGB_OFFSET);
            int dotColor = applyAlpha(dotRgbColor, dotAlpha);
            graphics.drawString(mc.font, CHAR_DOT, dotX + i * (dotWidth + DOT_SPACING), y, dotColor, false);
        }
    }

    private static float calculateDotAlpha(long dotElapsed) {
        float dotPhase = (dotElapsed % FADE_CYCLE_MS) / (float) FADE_CYCLE_MS;

        float dotAlpha;
        if (dotPhase < DOT_FADE_IN_END) {
            dotAlpha = dotPhase / DOT_FADE_IN_END;
        } else if (dotPhase < DOT_FADE_OUT_START) {
            dotAlpha = 1.0f;
        } else {
            dotAlpha = 1.0f - ((dotPhase - DOT_FADE_OUT_START) / (1.0f - DOT_FADE_OUT_START));
        }
        return (float) Math.pow(dotAlpha, DOT_GAMMA);
    }

    // ==================== Pulsing Song Name ====================

    private static void renderPulsingSongName(GuiGraphics graphics, Minecraft mc, String songName,
                                               int centerX, int y, long elapsed, int baseColor) {
        float pulsePhase = (elapsed % PULSE_CYCLE_MS) / (float) PULSE_CYCLE_MS;
        float pulseFactor = PULSE_MIN + PULSE_AMPLITUDE * (float) Math.sin(pulsePhase * Math.PI * 2);
        int pulsedColor = modulateBrightness(baseColor, pulseFactor);

        float scalePulse = SCALE_PULSE_MIN + SCALE_PULSE_AMPLITUDE * (float) Math.sin(pulsePhase * Math.PI * 2);
        int textWidth = mc.font.width(songName);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + mc.font.lineHeight / 2.0f, 0);
        graphics.pose().scale(scalePulse, scalePulse, 1.0f);
        graphics.pose().translate(-textWidth / 2.0f, -mc.font.lineHeight / 2.0f, 0);
        graphics.drawString(mc.font, songName, 0, 0, pulsedColor, false);
        graphics.pose().popPose();
    }

    // ==================== Song Name Parsing ====================

    private static String getSongDisplayName(ResourceLocation soundId) {
        if (soundId == null) {
            return null;
        }

        String path = soundId.getPath();

        if (path.startsWith(PREFIX_MUSIC_DISC)) {
            return capitalizeFirst(path.substring(PREFIX_MUSIC_DISC.length()).replace("_", " "));
        }

        if (path.startsWith(PREFIX_RECORDS)) {
            return capitalizeFirst(path.substring(PREFIX_RECORDS.length()).replace("_", " "));
        }

        return capitalizeFirst(path.replace("_", " ").replace("/", " - "));
    }

    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    // ==================== Color Utilities ====================

    private static int generateRandomVibrantColor() {
        return hsvToRgb(RANDOM.nextFloat(), VIBRANT_SATURATION, 1.0f);
    }

    private static int getRGBColor(long elapsed) {
        if (isChristmasTime()) {
            return getChristmasColor(elapsed);
        }
        float phase = (elapsed % RGB_CYCLE_MS) / (float) RGB_CYCLE_MS;
        return hsvToRgb(phase, 1.0f, 1.0f);
    }

    private static int getChristmasColor(long elapsed) {
        float phase = (elapsed % CHRISTMAS_CYCLE_MS) / (float) CHRISTMAS_CYCLE_MS;
        if (phase < 0.5f) {
            return interpolateColor(COLOR_CHRISTMAS_RED, COLOR_CHRISTMAS_GREEN, phase * 2.0f);
        } else {
            return interpolateColor(COLOR_CHRISTMAS_GREEN, COLOR_CHRISTMAS_RED, (phase - 0.5f) * 2.0f);
        }
    }

    private static boolean isChristmasTime() {
        LocalDate today = LocalDate.now();
        return today.getMonth() == Month.DECEMBER &&
               (today.getDayOfMonth() == 24 || today.getDayOfMonth() == 25);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;

        float r, g, b;
        int hSection = (int) (h * 6) % 6;

        switch (hSection) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }

        int ri = (int) ((r + m) * 255);
        int gi = (int) ((g + m) * 255);
        int bi = (int) ((b + m) * 255);

        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static int interpolateColor(int color1, int color2, float factor) {
        factor = Math.max(0, Math.min(1, factor));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int modulateBrightness(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==================== Loading Info ====================

    private record LoadingInfo(ResourceLocation soundId, String songTitle, float progress, long startTime, int songNameColor) {}
}

