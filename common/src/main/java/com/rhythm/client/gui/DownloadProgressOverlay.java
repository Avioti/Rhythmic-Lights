package com.rhythm.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalDate;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD overlay showing download progress.
 * <p>
 * Styled to match LoadingOverlay with RGB cycling colors and centered position.
 */
public class DownloadProgressOverlay {

    // ==================== Animation Timing ====================

    private static final long RGB_CYCLE_MS = 3000;
    private static final long PULSE_CYCLE_MS = 800;
    private static final long DOT_DELAY_MS = 200;
    private static final long FADE_CYCLE_MS = 1500;
    private static final long FLASH_CYCLE_MS = 500;
    private static final long WAVE_CYCLE_MS = 1000;
    private static final long CHRISTMAS_CYCLE_MS = 2000;

    // ==================== Layout ====================

    private static final int CENTER_Y_OFFSET = 40;
    private static final int PROGRESS_BAR_WIDTH = 100;
    private static final int PROGRESS_BAR_HEIGHT = 6;
    private static final int TEXT_LINE_SPACING = 4;
    private static final int BATCH_TEXT_SPACING = 8;
    private static final int DOT_SPACING = 8;
    private static final int DOT_COUNT = 5;
    private static final int DOT_BOUNCE_AMPLITUDE = 3;
    private static final int GLOW_EDGE_WIDTH = 2;

    // ==================== Colors ====================

    private static final int COLOR_BAR_OUTLINE = 0xFF222222;
    private static final int COLOR_BAR_BACKGROUND = 0xFF444444;
    private static final int COLOR_FAILED = 0xFFFF4444;
    private static final int COLOR_BATCH_TEXT = 0xFFAAAAAA;
    private static final int COLOR_GLOW = 0x80FFFFFF;

    private static final int COLOR_CHRISTMAS_RED = 0xFFFF2222;
    private static final int COLOR_CHRISTMAS_GREEN = 0xFF22DD22;

    // ==================== Animation Factors ====================

    private static final float PULSE_MIN = 0.7f;
    private static final float PULSE_AMPLITUDE = 0.3f;
    private static final float DOT_FADE_IN_END = 0.3f;
    private static final float DOT_FADE_OUT_START = 0.7f;
    private static final float DOT_GAMMA = 0.7f;
    private static final float FLASH_MIN_ALPHA = 0.5f;
    private static final float DOT_ALPHA_MIN = 0.6f;
    private static final float DOT_ALPHA_AMPLITUDE = 0.4f;

    // ==================== Text Constants ====================

    private static final String TEXT_DOWNLOADING = "Downloading";
    private static final String TEXT_CONVERTING = "Converting";
    private static final String TEXT_FAILED = "Download Failed";
    private static final String CHAR_DOT = ".";
    private static final String CHAR_BULLET = "●";
    private static final int DOT_TEXT_SPACING = 2;
    private static final int ANIMATED_DOT_COUNT = 3;

    // ==================== State ====================

    private static int batchSize = 0;
    private static final LinkedHashMap<String, ProgressEntry> progressQueue = new LinkedHashMap<>();
    private static long animationStartTime = 0;

    // ==================== Public API ====================

    public static void set(String id, int progressPercent) {
        if (progressQueue.put(id, new ProgressEntry(progressPercent)) == null) {
            batchSize++;
            if (animationStartTime == 0) {
                animationStartTime = System.currentTimeMillis();
            }
        }
    }

    public static void stop(String id) {
        if (progressQueue.remove(id) != null && progressQueue.isEmpty()) {
            batchSize = 0;
            animationStartTime = 0;
        }
    }

    public static void stopFailed(String id) {
        progressQueue.put(id, new ProgressEntry(ProgressEntry.ERROR));
    }

    /**
     * Force clears all download/conversion progress overlays.
     * Used when cancelling stuck downloads.
     */
    public static void clearAll() {
        progressQueue.clear();
        batchSize = 0;
        animationStartTime = 0;
    }

    public static boolean isActive() {
        return !progressQueue.isEmpty();
    }

    // ==================== Rendering ====================

    public static void render(GuiGraphics graphics) {
        if (progressQueue.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Map.Entry<String, ProgressEntry> firstEntry = progressQueue.entrySet().iterator().next();
        String currentId = firstEntry.getKey();
        ProgressEntry entry = firstEntry.getValue();

        long now = System.currentTimeMillis();
        if (entry.shouldRemove(now)) {
            stop(currentId);
            return;
        }

        long elapsed = now - animationStartTime;
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2 + CENTER_Y_OFFSET;
        int rgbColor = getRGBColor(elapsed);

        renderProgressState(graphics, mc, centerX, centerY, elapsed, entry, rgbColor);
        renderBatchInfo(graphics, mc, centerX, centerY);
    }

    private static void renderProgressState(GuiGraphics graphics, Minecraft mc, int centerX, int centerY,
                                             long elapsed, ProgressEntry entry, int rgbColor) {
        switch (entry.state) {
            case DOWNLOADING -> renderDownloading(graphics, mc, centerX, centerY, elapsed, entry.progress, rgbColor);
            case TRANSCODING -> renderTranscoding(graphics, mc, centerX, centerY, elapsed, rgbColor);
            case INTERRUPTED -> renderInterrupted(graphics, mc, centerX, centerY, elapsed);
        }
    }

    private static void renderBatchInfo(GuiGraphics graphics, Minecraft mc, int centerX, int centerY) {
        if (batchSize <= 1) {
            return;
        }

        String batchText = String.format("(%d/%d)", batchSize - (progressQueue.size() - 1), batchSize);
        int batchWidth = mc.font.width(batchText);
        int batchY = centerY + mc.font.lineHeight + BATCH_TEXT_SPACING;
        graphics.drawString(mc.font, batchText, centerX - batchWidth / 2, batchY, COLOR_BATCH_TEXT, false);
    }

    // ==================== Download State Rendering ====================

    private static void renderDownloading(GuiGraphics graphics, Minecraft mc, int centerX, int y,
                                          long elapsed, int progress, int rgbColor) {
        renderAnimatedText(graphics, mc, centerX, y, elapsed, TEXT_DOWNLOADING, rgbColor);

        int barY = y + mc.font.lineHeight + TEXT_LINE_SPACING;
        renderProgressBar(graphics, centerX, barY, progress, rgbColor);

        renderPercentage(graphics, mc, centerX, barY, elapsed, progress, rgbColor);
    }

    private static void renderPercentage(GuiGraphics graphics, Minecraft mc, int centerX, int barY,
                                          long elapsed, int progress, int rgbColor) {
        String percentText = progress + "%";
        int percentWidth = mc.font.width(percentText);
        int percentY = barY + PROGRESS_BAR_HEIGHT + DOT_TEXT_SPACING;

        float pulseFactor = PULSE_MIN + PULSE_AMPLITUDE * (float) Math.sin(getPulsePhase(elapsed));
        int pulsedColor = modulateBrightness(rgbColor, pulseFactor);
        graphics.drawString(mc.font, percentText, centerX - percentWidth / 2, percentY, pulsedColor, false);
    }

    private static double getPulsePhase(long elapsed) {
        return (elapsed % PULSE_CYCLE_MS) / (float) PULSE_CYCLE_MS * Math.PI * 2;
    }

    private static void renderTranscoding(GuiGraphics graphics, Minecraft mc, int centerX, int y,
                                          long elapsed, int rgbColor) {
        renderAnimatedText(graphics, mc, centerX, y, elapsed, TEXT_CONVERTING, rgbColor);

        int dotsY = y + mc.font.lineHeight + TEXT_LINE_SPACING;
        renderAnimatedDots(graphics, mc, centerX, dotsY, elapsed, rgbColor);
    }

    private static void renderInterrupted(GuiGraphics graphics, Minecraft mc, int centerX, int y, long elapsed) {
        int textWidth = mc.font.width(TEXT_FAILED);

        float flashPhase = (elapsed % FLASH_CYCLE_MS) / (float) FLASH_CYCLE_MS;
        float alpha = FLASH_MIN_ALPHA + FLASH_MIN_ALPHA * (float) Math.sin(flashPhase * Math.PI * 2);
        int flashColor = applyAlpha(COLOR_FAILED, alpha);

        graphics.drawString(mc.font, TEXT_FAILED, centerX - textWidth / 2, y, flashColor, false);
    }

    // ==================== Animated Text ====================

    private static void renderAnimatedText(GuiGraphics graphics, Minecraft mc, int centerX, int y,
                                           long elapsed, String text, int rgbColor) {
        int textWidth = mc.font.width(text);
        int dotWidth = mc.font.width(CHAR_DOT);
        int totalWidth = textWidth + DOT_TEXT_SPACING + (dotWidth + DOT_TEXT_SPACING) * ANIMATED_DOT_COUNT;
        int startX = centerX - totalWidth / 2;

        graphics.drawString(mc.font, text, startX, y, rgbColor, false);
        renderAnimatedTextDots(graphics, mc, startX + textWidth + DOT_TEXT_SPACING, y, elapsed, dotWidth);
    }

    private static void renderAnimatedTextDots(GuiGraphics graphics, Minecraft mc, int dotX, int y,
                                                long elapsed, int dotWidth) {
        for (int i = 0; i < ANIMATED_DOT_COUNT; i++) {
            long dotElapsed = elapsed - (i * DOT_DELAY_MS);
            float dotAlpha = calculateDotAlpha(dotElapsed);

            int dotRgbColor = getRGBColor(elapsed + i * DOT_DELAY_MS);
            int dotColor = applyAlpha(dotRgbColor, dotAlpha);
            graphics.drawString(mc.font, CHAR_DOT, dotX + i * (dotWidth + DOT_TEXT_SPACING), y, dotColor, false);
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

    // ==================== Progress Bar ====================

    private static void renderProgressBar(GuiGraphics graphics, int centerX, int y,
                                          int progress, int rgbColor) {
        int startX = centerX - PROGRESS_BAR_WIDTH / 2;

        graphics.fill(startX - 1, y - 1, startX + PROGRESS_BAR_WIDTH + 1, y + PROGRESS_BAR_HEIGHT + 1, COLOR_BAR_OUTLINE);
        graphics.fill(startX, y, startX + PROGRESS_BAR_WIDTH, y + PROGRESS_BAR_HEIGHT, COLOR_BAR_BACKGROUND);

        int fillWidth = (PROGRESS_BAR_WIDTH * progress) / 100;
        if (fillWidth > 0) {
            graphics.fill(startX, y, startX + fillWidth, y + PROGRESS_BAR_HEIGHT, rgbColor);

            if (fillWidth < PROGRESS_BAR_WIDTH) {
                int glowColor = applyAlpha(COLOR_GLOW, FLASH_MIN_ALPHA);
                graphics.fill(startX + fillWidth - GLOW_EDGE_WIDTH, y, startX + fillWidth, y + PROGRESS_BAR_HEIGHT, glowColor);
            }
        }
    }

    // ==================== Animated Dots ====================

    // ==================== Animated Dots ====================

    private static void renderAnimatedDots(GuiGraphics graphics, Minecraft mc, int centerX, int y,
                                           long elapsed, int rgbColor) {
        int startX = centerX - (DOT_COUNT * DOT_SPACING) / 2;

        for (int i = 0; i < DOT_COUNT; i++) {
            float phase = ((elapsed + i * 100) % WAVE_CYCLE_MS) / (float) WAVE_CYCLE_MS;
            float bounce = (float) Math.sin(phase * Math.PI * 2);
            int offsetY = (int) (bounce * DOT_BOUNCE_AMPLITUDE);

            int dotColor = getRGBColor(elapsed + i * 150);
            float alpha = DOT_ALPHA_MIN + DOT_ALPHA_AMPLITUDE * (0.5f + 0.5f * bounce);
            dotColor = applyAlpha(dotColor, alpha);

            graphics.drawString(mc.font, CHAR_BULLET, startX + i * DOT_SPACING, y + offsetY, dotColor, false);
        }
    }

    // ==================== Holiday Detection ====================

    private static boolean isChristmasTime() {
        LocalDate today = LocalDate.now();
        return today.getMonth() == Month.DECEMBER &&
               (today.getDayOfMonth() == 24 || today.getDayOfMonth() == 25);
    }

    // ==================== Color Utilities ====================

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

    /**
     * Interpolate between two ARGB colors.
     */
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

    /**
     * Apply an alpha multiplier to an ARGB color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Modulate the brightness of a color.
     */
    private static int modulateBrightness(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==================== Progress Entry ====================

    /**
     * Represents a download progress entry with state tracking.
     */
    public static class ProgressEntry {
        public static final int MIN = 0;
        public static final int MAX = 100;
        public static final int ERROR = -1;
        public static final long ERROR_TIMEOUT_MILLIS = 2000;

        public int progress;
        public long stateChangeTime;
        public ProgressState state;

        public ProgressEntry(int progress) {
            this.updateProgress(progress);
        }

        void updateProgress(int progress) {
            this.progress = (progress >= MIN && progress <= MAX) ? progress : ERROR;
            this.stateChangeTime = System.currentTimeMillis();
            this.state = switch (this.progress) {
                case ERROR -> ProgressState.INTERRUPTED;
                case MAX -> ProgressState.TRANSCODING;
                default -> ProgressState.DOWNLOADING;
            };
        }

        boolean shouldRemove(long now) {
            return state == ProgressState.INTERRUPTED && now - stateChangeTime >= ERROR_TIMEOUT_MILLIS;
        }        public enum ProgressState {
            DOWNLOADING,
            TRANSCODING,
            INTERRUPTED
        }
    }
}

