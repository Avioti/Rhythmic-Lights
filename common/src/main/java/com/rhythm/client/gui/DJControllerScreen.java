package com.rhythm.client.gui;

import com.rhythm.audio.AudioSettings;
import com.rhythm.audio.state.ClientSongManager;
import com.rhythm.audio.state.PlaybackState;
import com.rhythm.client.gui.widget.ImageButton;
import com.rhythm.client.gui.widget.TextButton;
import com.rhythm.client.gui.widget.ToggleButton;
import com.rhythm.network.DJSettingsPacket;
import com.rhythm.util.RhythmConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * DJ Controller GUI Screen for the DJ Station.
 * <p>
 * Features:
 * <ul>
 *   <li>Black transparent background with RGB swirling border</li>
 *   <li>White sliders with RGB fill</li>
 *   <li>Well-spaced EQ bands</li>
 * </ul>
 */
public class DJControllerScreen extends Screen {

    // ==================== Dimensions ====================

    private static final int GUI_WIDTH = 380;
    private static final int GUI_HEIGHT = 240;

    // ==================== Colors ====================

    private static final int BG_COLOR = 0xBB000000;
    private static final int OVERLAY_COLOR = 0x88000000;
    private static final int SLIDER_OUTLINE = 0xFFFFFFFF;
    private static final int SLIDER_BG = 0xAA111111;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int CENTER_LINE_COLOR = 0x88FFFFFF;

    private static final int STATE_PLAYING_COLOR = 0xFF00FF88;
    private static final int STATE_LOADING_COLOR = 0xFFFFFF00;
    private static final int STATE_STOPPED_COLOR = 0xFFFF8800;

    // ==================== Animation ====================

    private static final float RGB_SPEED = 0.5f;
    private static final float RGB_HANDLE_SPEED = 1.0f;

    // ==================== Layout ====================

    private static final int BORDER_WIDTH = 2;
    private static final int TITLE_Y_OFFSET = 10;
    private static final int STATUS_Y_OFFSET = 30;
    private static final int SLIDER_AREA_Y_OFFSET = 50;
    private static final int SLIDER_HEIGHT = 110;
    private static final int BUTTON_Y_OFFSET = 40;
    private static final int BUTTON_SIZE = 28;
    private static final int BUTTON_SPACING = 15;
    private static final int LOOP_BUTTON_WIDTH = 70;
    private static final int RESET_BUTTON_WIDTH = 65;
    private static final int RESET_BUTTON_HEIGHT = 16;

    private static final int VOL_BASS_X_OFFSET = 25;
    private static final int VOL_BASS_WIDTH = 30;
    private static final int VOL_BASS_SPACING = 50;

    private static final int EQ_START_X_OFFSET = 120;
    private static final int EQ_BAND_WIDTH = 22;
    private static final int EQ_BAND_SPACING = 4;
    private static final int EQ_BAND_COUNT = 10;
    private static final int PACKET_EQ_BAND_COUNT = 12;

    // ==================== Audio Constants ====================

    private static final float MAX_VOLUME = 2.0f;
    private static final float EQ_MIN_DB = -12.0f;
    private static final float EQ_MAX_DB = 12.0f;
    private static final float EQ_RANGE_DB = 24.0f;

    private static final int SLIDER_ID_VOLUME = 0;
    private static final int SLIDER_ID_BASS = 1;
    private static final int SLIDER_ID_EQ_START = 2;

    private static final String[] EQ_BAND_LABELS = {"32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"};

    // ==================== State ====================

    private final BlockPos controllerPos;
    private final BlockPos jukeboxPos;

    private int guiLeft;
    private int guiTop;

    private float masterVolume = 1.0f;
    private float bassBoost = 0.0f;
    private float[] eqBands = new float[EQ_BAND_COUNT];
    private boolean loopEnabled = false;

    private int draggingSlider = -1;

    private static Consumer<DJSettingsPacket> packetSender = null;

    // ==================== Static Setup ====================

    public static void setPacketSender(Consumer<DJSettingsPacket> sender) {
        packetSender = sender;
    }

    // ==================== Constructor ====================

    public DJControllerScreen(BlockPos controllerPos, BlockPos jukeboxPos) {
        super(Component.literal("DJ Controller"));
        this.controllerPos = controllerPos;
        this.jukeboxPos = jukeboxPos;
        loadCurrentSettings();
    }

    private void loadCurrentSettings() {
        AudioSettings settings = AudioSettings.getInstance();
        this.masterVolume = settings.getMasterVolume();
        this.bassBoost = settings.getBassBoost();

        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            this.eqBands[i] = settings.getEqualizerBandDb(i);
        }

        if (jukeboxPos != null) {
            this.loopEnabled = ClientSongManager.getInstance().isLoopEnabled(jukeboxPos);
        }
    }

    // ==================== Initialization ====================

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        initTransportControls();
        initResetButton();
    }

    private void initTransportControls() {
        int buttonY = guiTop + GUI_HEIGHT - BUTTON_Y_OFFSET;
        int totalWidth = BUTTON_SIZE * 3 + LOOP_BUTTON_WIDTH + BUTTON_SPACING * 3;
        int startX = guiLeft + (GUI_WIDTH - totalWidth) / 2;

        addRenderableWidget(ImageButton.playButton(startX, buttonY, BUTTON_SIZE, this::onPlay));
        addRenderableWidget(ImageButton.pauseButton(startX + BUTTON_SIZE + BUTTON_SPACING, buttonY, BUTTON_SIZE, this::onPause));
        addRenderableWidget(ImageButton.stopButton(startX + (BUTTON_SIZE + BUTTON_SPACING) * 2, buttonY, BUTTON_SIZE, this::onStop));

        addRenderableWidget(new ToggleButton(
            startX + (BUTTON_SIZE + BUTTON_SPACING) * 3, buttonY, LOOP_BUTTON_WIDTH, BUTTON_SIZE,
            "Loop", loopEnabled, ToggleButton.ToggleType.LOOP,
            this::onLoopToggled
        ));
    }

    private void initResetButton() {
        addRenderableWidget(new TextButton(
            guiLeft + GUI_WIDTH - 75, guiTop + 8, RESET_BUTTON_WIDTH, RESET_BUTTON_HEIGHT,
            "Reset EQ", this::onResetEQ
        ));
    }

    private void onLoopToggled(boolean toggled) {
        loopEnabled = toggled;
        syncSettings();
    }

    private void onResetEQ() {
        for (int i = 0; i < eqBands.length; i++) {
            eqBands[i] = 0.0f;
        }
        bassBoost = 0.0f;
        syncSettings();
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderOverlay(graphics);
        renderMainPanel(graphics);
        renderTitle(graphics);
        renderStatusBar(graphics);
        renderSliders(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverlay(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, OVERLAY_COLOR);
    }

    private void renderMainPanel(GuiGraphics graphics) {
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);
        RGBAnimator.renderSimpleRGBBorder(graphics, guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, BORDER_WIDTH, RGB_SPEED);
    }

    private void renderTitle(GuiGraphics graphics) {
        graphics.drawCenteredString(font, "DJ Controller", guiLeft + GUI_WIDTH / 2, guiTop + TITLE_Y_OFFSET, TEXT_COLOR);
    }

    private void renderSliders(GuiGraphics graphics, int mouseX, int mouseY) {
        int sliderAreaY = guiTop + SLIDER_AREA_Y_OFFSET;

        renderSlider(graphics, guiLeft + VOL_BASS_X_OFFSET, sliderAreaY, VOL_BASS_WIDTH, SLIDER_HEIGHT,
            "VOL", masterVolume / MAX_VOLUME, mouseX, mouseY, SLIDER_ID_VOLUME);
        renderSlider(graphics, guiLeft + VOL_BASS_X_OFFSET + VOL_BASS_SPACING, sliderAreaY, VOL_BASS_WIDTH, SLIDER_HEIGHT,
            "BASS", bassBoost, mouseX, mouseY, SLIDER_ID_BASS);

        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            float normalized = (eqBands[i] - EQ_MIN_DB) / EQ_RANGE_DB;
            int sliderX = guiLeft + EQ_START_X_OFFSET + i * (EQ_BAND_WIDTH + EQ_BAND_SPACING);
            renderSlider(graphics, sliderX, sliderAreaY, EQ_BAND_WIDTH, SLIDER_HEIGHT,
                EQ_BAND_LABELS[i], normalized, mouseX, mouseY, SLIDER_ID_EQ_START + i);
        }
    }

    private void renderStatusBar(GuiGraphics graphics) {
        int statusY = guiTop + STATUS_Y_OFFSET;

        PlaybackState state = getPlaybackState();
        String stateText = getStateDisplayText(state);
        int stateColor = getStateColor(state);

        graphics.drawString(font, stateText, guiLeft + 15, statusY, stateColor);

        String volText = String.format("Vol: %d%%", (int)(masterVolume * 100));
        graphics.drawString(font, volText, guiLeft + 120, statusY, TEXT_DIM);

        if (loopEnabled) {
            graphics.drawString(font, "Loop", guiLeft + 200, statusY, RGBAnimator.getRGBColor(RGB_SPEED));
        }
    }

    private PlaybackState getPlaybackState() {
        if (jukeboxPos == null) {
            return PlaybackState.EMPTY;
        }
        return ClientSongManager.getInstance().getState(jukeboxPos);
    }

    private String getStateDisplayText(PlaybackState state) {
        return switch (state) {
            case EMPTY -> "No Disc";
            case LOADING -> "Loading...";
            case READY -> "Ready";
            case PLAYING -> "Playing";
            case STOPPED -> "Paused";
        };
    }

    private int getStateColor(PlaybackState state) {
        return switch (state) {
            case PLAYING -> STATE_PLAYING_COLOR;
            case LOADING -> STATE_LOADING_COLOR;
            case STOPPED -> STATE_STOPPED_COLOR;
            default -> TEXT_DIM;
        };
    }

    // ==================== Slider Rendering ====================

    private void renderSlider(GuiGraphics graphics, int x, int y, int sliderWidth, int sliderHeight,
                              String label, float value, int mouseX, int mouseY, int sliderId) {
        renderSliderBackground(graphics, x, y, sliderWidth, sliderHeight);
        renderSliderFill(graphics, x, y, sliderWidth, sliderHeight, value, sliderId);
        renderSliderHandle(graphics, x, y, sliderWidth, sliderHeight, value, sliderId);
        renderSliderLabel(graphics, x, y, sliderWidth, sliderHeight, label);
        renderSliderHoverValue(graphics, x, y, sliderWidth, sliderHeight, mouseX, mouseY, sliderId);
    }

    private void renderSliderBackground(GuiGraphics graphics, int x, int y, int sliderWidth, int sliderHeight) {
        graphics.fill(x, y, x + sliderWidth, y + sliderHeight, SLIDER_BG);
        graphics.renderOutline(x, y, sliderWidth, sliderHeight, SLIDER_OUTLINE);
    }

    private void renderSliderFill(GuiGraphics graphics, int x, int y, int sliderWidth, int sliderHeight,
                                   float value, int sliderId) {
        int fillHeight = (int)(sliderHeight * value);
        int fillY = y + sliderHeight - fillHeight;
        float hueOffset = (float)sliderId / 12.0f;
        int fillColor = RGBAnimator.getRGBColor(RGB_SPEED, hueOffset);
        graphics.fill(x + 1, fillY, x + sliderWidth - 1, y + sliderHeight - 1, fillColor);

        if (sliderId >= SLIDER_ID_EQ_START) {
            int centerY = y + sliderHeight / 2;
            graphics.fill(x, centerY, x + sliderWidth, centerY + 1, CENTER_LINE_COLOR);
        }
    }

    private void renderSliderHandle(GuiGraphics graphics, int x, int y, int sliderWidth, int sliderHeight,
                                     float value, int sliderId) {
        int fillHeight = (int)(sliderHeight * value);
        int fillY = y + sliderHeight - fillHeight;
        int handleY = Mth.clamp(fillY - 2, y - 2, y + sliderHeight - 4);

        float hueOffset = (float)sliderId / 12.0f;
        int handleColor = RGBAnimator.getRGBColor(RGB_HANDLE_SPEED, hueOffset);
        int glowColor = 0x44000000 | (handleColor & 0x00FFFFFF);

        graphics.fill(x - 2, handleY - 1, x + sliderWidth + 2, handleY + 5, glowColor);
        graphics.fill(x - 1, handleY, x + sliderWidth + 1, handleY + 4, SLIDER_OUTLINE);
    }

    private void renderSliderLabel(GuiGraphics graphics, int x, int y, int sliderWidth, int sliderHeight, String label) {
        int labelWidth = font.width(label);
        graphics.drawString(font, label, x + (sliderWidth - labelWidth) / 2, y + sliderHeight + 4, TEXT_DIM, false);
    }

    private void renderSliderHoverValue(GuiGraphics graphics, int x, int y, int sliderWidth, int sliderHeight,
                                         int mouseX, int mouseY, int sliderId) {
        if (!isMouseOverSlider(mouseX, mouseY, x, y, sliderWidth, sliderHeight)) {
            return;
        }

        String valueStr = getSliderValueText(sliderId);
        graphics.drawString(font, valueStr, x, y - 12, TEXT_COLOR);
    }

    private boolean isMouseOverSlider(int mouseX, int mouseY, int x, int y, int sliderWidth, int sliderHeight) {
        return mouseX >= x && mouseX <= x + sliderWidth && mouseY >= y && mouseY <= y + sliderHeight;
    }

    private String getSliderValueText(int sliderId) {
        if (sliderId == SLIDER_ID_VOLUME) {
            return String.format("%d%%", (int)(masterVolume * 100));
        } else if (sliderId == SLIDER_ID_BASS) {
            return String.format("%d%%", (int)(bassBoost * 100));
        } else {
            return String.format("%+.1fdB", eqBands[sliderId - SLIDER_ID_EQ_START]);
        }
    }

    // ==================== Mouse Interaction ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int sliderAreaY = guiTop + SLIDER_AREA_Y_OFFSET;

        int volumeSlider = checkSliderClick(mouseX, mouseY, guiLeft + VOL_BASS_X_OFFSET, sliderAreaY, VOL_BASS_WIDTH, SLIDER_ID_VOLUME);
        if (volumeSlider >= 0) return true;

        int bassSlider = checkSliderClick(mouseX, mouseY, guiLeft + VOL_BASS_X_OFFSET + VOL_BASS_SPACING, sliderAreaY, VOL_BASS_WIDTH, SLIDER_ID_BASS);
        if (bassSlider >= 0) return true;

        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            int sliderX = guiLeft + EQ_START_X_OFFSET + i * (EQ_BAND_WIDTH + EQ_BAND_SPACING);
            int eqSlider = checkSliderClick(mouseX, mouseY, sliderX, sliderAreaY, EQ_BAND_WIDTH, SLIDER_ID_EQ_START + i);
            if (eqSlider >= 0) return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int checkSliderClick(double mouseX, double mouseY, int x, int sliderY, int sliderWidth, int sliderId) {
        if (isInSlider(mouseX, mouseY, x, sliderY, sliderWidth, SLIDER_HEIGHT)) {
            draggingSlider = sliderId;
            updateSliderValue(mouseY, sliderY, SLIDER_HEIGHT, sliderId);
            return sliderId;
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider >= 0 && button == 0) {
            updateSliderValue(mouseY, guiTop + SLIDER_AREA_Y_OFFSET, SLIDER_HEIGHT, draggingSlider);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider >= 0 && button == 0) {
            draggingSlider = -1;
            syncSettings();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInSlider(double mouseX, double mouseY, int x, int y, int sliderWidth, int sliderHeight) {
        return mouseX >= x - 2 && mouseX <= x + sliderWidth + 2 && mouseY >= y - 5 && mouseY <= y + sliderHeight + 5;
    }

    // ==================== Slider Value Updates ====================

    private void updateSliderValue(double mouseY, int sliderY, int sliderHeight, int sliderId) {
        float value = 1.0f - (float)((mouseY - sliderY) / sliderHeight);
        value = Mth.clamp(value, 0.0f, 1.0f);

        AudioSettings settings = AudioSettings.getInstance();

        if (sliderId == SLIDER_ID_VOLUME) {
            updateVolumeSlider(value, settings);
        } else if (sliderId == SLIDER_ID_BASS) {
            updateBassSlider(value, settings);
        } else {
            updateEQSlider(value, settings, sliderId - SLIDER_ID_EQ_START);
        }
    }

    private void updateVolumeSlider(float value, AudioSettings settings) {
        masterVolume = value * MAX_VOLUME;
        settings.setMasterVolume(masterVolume);
        if (RhythmConstants.DEBUG_GUI) {
            RhythmConstants.LOGGER.debug("Master Volume set to: {}%", (int)(masterVolume * 100));
        }
    }

    private void updateBassSlider(float value, AudioSettings settings) {
        bassBoost = value;
        settings.setBassBoost(bassBoost);
        if (RhythmConstants.DEBUG_GUI) {
            RhythmConstants.LOGGER.debug("Bass Boost set to: {}%", (int)(bassBoost * 100));
        }
    }

    private void updateEQSlider(float value, AudioSettings settings, int bandIndex) {
        float dbValue = (value * EQ_RANGE_DB) + EQ_MIN_DB;
        eqBands[bandIndex] = dbValue;
        if (bandIndex < EQ_BAND_COUNT) {
            settings.setEqualizerBandDb(bandIndex, dbValue);
            if (RhythmConstants.DEBUG_GUI) {
                RhythmConstants.LOGGER.debug("EQ Band {} set to: {} dB", bandIndex, dbValue);
            }
        }
    }

    // ==================== Settings Sync ====================

    private void syncSettings() {
        AudioSettings settings = AudioSettings.getInstance();
        settings.setMasterVolume(masterVolume);
        settings.setBassBoost(bassBoost);
        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            settings.setEqualizerBandDb(i, eqBands[i]);
        }

        if (jukeboxPos != null) {
            ClientSongManager.getInstance().setLoopEnabled(jukeboxPos, loopEnabled);
        }

        sendSettingsPacket();
    }

    private void sendSettingsPacket() {
        if (packetSender == null) {
            return;
        }

        float[] paddedBands = new float[PACKET_EQ_BAND_COUNT];
        System.arraycopy(eqBands, 0, paddedBands, 0, EQ_BAND_COUNT);
        DJSettingsPacket packet = new DJSettingsPacket(controllerPos, masterVolume, bassBoost, paddedBands, loopEnabled);
        packetSender.accept(packet);
    }

    // ==================== Transport Controls ====================

    private void onPlay() {
        if (packetSender != null) {
            packetSender.accept(DJSettingsPacket.createPlayCommand(controllerPos));
        }
    }

    private void onPause() {
        if (packetSender != null) {
            packetSender.accept(DJSettingsPacket.createPauseCommand(controllerPos));
        }
    }

    private void onStop() {
        if (packetSender != null) {
            packetSender.accept(DJSettingsPacket.createStopCommand(controllerPos));
        }
    }

    // ==================== Screen Properties ====================

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

