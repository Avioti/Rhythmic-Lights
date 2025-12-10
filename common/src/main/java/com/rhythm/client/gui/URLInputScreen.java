package com.rhythm.client.gui;

import com.rhythm.audio.io.VideoMetadata;
import com.rhythm.client.gui.widget.StyledEditBox;
import com.rhythm.client.gui.widget.TextButton;
import com.rhythm.client.gui.widget.ToggleButton;
import com.rhythm.network.SetURLDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * GUI Screen for entering URL data for a RhythmURL disc.
 * <p>
 * Features:
 * <ul>
 *   <li>Black transparent background with RGB swirling border</li>
 *   <li>Auto-fetch metadata from YouTube URLs</li>
 *   <li>Loop and lock toggle options</li>
 * </ul>
 */
public class URLInputScreen extends Screen {

    // ==================== Dimensions ====================

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 200;
    private static final int BORDER_WIDTH = 2;

    private static final int INPUT_HEIGHT = 22;
    private static final int PADDING = 15;
    private static final int URL_INPUT_WIDTH = PANEL_WIDTH - 85;
    private static final int TITLE_INPUT_WIDTH = PANEL_WIDTH - 30;
    private static final int DURATION_INPUT_WIDTH = 70;
    private static final int TOGGLE_WIDTH = 70;
    private static final int BUTTON_WIDTH = 90;
    private static final int FETCH_BUTTON_WIDTH = 50;

    private static final int URL_ROW_Y = 40;
    private static final int TITLE_ROW_Y = 80;
    private static final int DURATION_ROW_Y = 120;
    private static final int BUTTONS_ROW_Y = 160;
    private static final int TITLE_Y = 12;
    private static final int LABEL_OFFSET_Y = -10;

    private static final int LOOP_TOGGLE_X_OFFSET = 100;
    private static final int LOCK_TOGGLE_X_OFFSET = 180;
    private static final int SAVE_BUTTON_X_OFFSET = 60;
    private static final int CANCEL_BUTTON_X_OFFSET = 170;
    private static final int FETCH_BUTTON_X_OFFSET = PANEL_WIDTH - 65;

    // ==================== Colors ====================

    private static final int BG_COLOR = 0xBB000000;
    private static final int OVERLAY_COLOR = 0x88000000;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_LABEL_DIM = 0xFF888888;
    private static final int COLOR_FETCHING = 0xFFFFFF00;
    private static final int COLOR_SUCCESS = 0xFF00FF88;
    private static final int COLOR_ERROR = 0xFFFF5555;

    // ==================== Animation ====================

    private static final float RGB_SPEED = 0.5f;

    // ==================== Input Limits ====================

    private static final int URL_MAX_LENGTH = 400;
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int DURATION_MAX_LENGTH = 6;
    private static final int SECONDS_PER_MINUTE = 60;

    // ==================== Text Constants ====================

    private static final String TITLE_TEXT = "URL Disc Setup";
    private static final String LABEL_URL = "URL:";
    private static final String LABEL_TITLE = "Title:";
    private static final String LABEL_DURATION = "Duration:";
    private static final String HINT_URL = "https://youtube.com/watch?v=...";
    private static final String HINT_TITLE = "Auto-fetched or enter manually";
    private static final String HINT_DURATION = "m:ss";
    private static final String BTN_FETCH = "Fetch";
    private static final String BTN_SAVE = "Save";
    private static final String BTN_CANCEL = "Cancel";
    private static final String TOGGLE_LOOP = "Loop";
    private static final String TOGGLE_LOCK = "Lock";

    private static final String STATUS_FETCHING = "Fetching...";
    private static final String STATUS_SUCCESS = "Fetched!";
    private static final String STATUS_FAILED = "Failed";
    private static final String STATUS_ERROR = "Error";
    private static final String STATUS_ENTER_URL = "Enter URL";
    private static final String STATUS_PREFIX_SUCCESS = "✓ ";
    private static final String STATUS_PREFIX_ERROR = "✗ ";

    private static final String DURATION_REGEX = "[0-9:]*";

    // ==================== State ====================

    private static Consumer<SetURLDataPacket> packetSender = null;

    private String url;
    private String title;
    private int duration;
    private boolean isLoopEnabled;
    private boolean isLockEnabled;

    private boolean isFetching = false;
    private String fetchStatus = "";

    private StyledEditBox urlInput;
    private StyledEditBox titleInput;
    private StyledEditBox durationInput;
    private ToggleButton loopToggle;
    private ToggleButton lockToggle;

    // ==================== Constructor ====================

    public URLInputScreen(String currentUrl, String currentTitle, int currentDuration, boolean currentLoop) {
        super(Component.literal("Rhythm URL Disc"));
        this.url = currentUrl != null ? currentUrl : "";
        this.title = currentTitle != null ? currentTitle : "";
        this.duration = currentDuration;
        this.isLoopEnabled = currentLoop;
        this.isLockEnabled = false;
    }

    // ==================== Static API ====================

    public static void setPacketSender(Consumer<SetURLDataPacket> sender) {
        packetSender = sender;
    }

    public static void open(String currentUrl, String currentTitle, int currentDuration, boolean currentLoop) {
        Minecraft.getInstance().setScreen(new URLInputScreen(currentUrl, currentTitle, currentDuration, currentLoop));
    }

    // ==================== Initialization ====================

    @Override
    protected void init() {
        super.init();

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;

        initUrlRow(panelLeft, panelTop);
        initTitleRow(panelLeft, panelTop);
        initDurationRow(panelLeft, panelTop);
        initActionButtons(panelLeft, panelTop);

        this.setInitialFocus(urlInput);
    }

    private void initUrlRow(int panelLeft, int panelTop) {
        int rowY = panelTop + URL_ROW_Y;

        this.urlInput = new StyledEditBox(panelLeft + PADDING, rowY, URL_INPUT_WIDTH, INPUT_HEIGHT, Component.literal("URL"));
        this.urlInput.setMaxLength(URL_MAX_LENGTH);
        this.urlInput.setValue(url);
        this.urlInput.setHint(HINT_URL);
        this.addRenderableWidget(urlInput);

        this.addRenderableWidget(new TextButton(
            panelLeft + FETCH_BUTTON_X_OFFSET, rowY, FETCH_BUTTON_WIDTH, INPUT_HEIGHT, BTN_FETCH, this::fetchMetadata
        ));
    }

    private void initTitleRow(int panelLeft, int panelTop) {
        int rowY = panelTop + TITLE_ROW_Y;

        this.titleInput = new StyledEditBox(panelLeft + PADDING, rowY, TITLE_INPUT_WIDTH, INPUT_HEIGHT, Component.literal("Title"));
        this.titleInput.setMaxLength(TITLE_MAX_LENGTH);
        this.titleInput.setValue(title);
        this.titleInput.setHint(HINT_TITLE);
        this.addRenderableWidget(titleInput);
    }

    private void initDurationRow(int panelLeft, int panelTop) {
        int rowY = panelTop + DURATION_ROW_Y;

        this.durationInput = new StyledEditBox(panelLeft + PADDING, rowY, DURATION_INPUT_WIDTH, INPUT_HEIGHT, Component.literal("Duration"));
        this.durationInput.setMaxLength(DURATION_MAX_LENGTH);
        this.durationInput.setValue(formatDuration(duration));
        this.durationInput.setHint(HINT_DURATION);
        this.durationInput.setFilter(this::isDurationCharacter);
        this.addRenderableWidget(durationInput);

        this.loopToggle = new ToggleButton(
            panelLeft + LOOP_TOGGLE_X_OFFSET, rowY, TOGGLE_WIDTH, INPUT_HEIGHT,
            TOGGLE_LOOP, isLoopEnabled, ToggleButton.ToggleType.LOOP, t -> this.isLoopEnabled = t
        );
        this.addRenderableWidget(loopToggle);

        this.lockToggle = new ToggleButton(
            panelLeft + LOCK_TOGGLE_X_OFFSET, rowY, TOGGLE_WIDTH, INPUT_HEIGHT,
            TOGGLE_LOCK, isLockEnabled, ToggleButton.ToggleType.LOCK, t -> this.isLockEnabled = t
        );
        this.addRenderableWidget(lockToggle);
    }

    private void initActionButtons(int panelLeft, int panelTop) {
        int rowY = panelTop + BUTTONS_ROW_Y;

        this.addRenderableWidget(new TextButton(
            panelLeft + SAVE_BUTTON_X_OFFSET, rowY, BUTTON_WIDTH, INPUT_HEIGHT, BTN_SAVE, this::saveAndClose
        ));

        this.addRenderableWidget(new TextButton(
            panelLeft + CANCEL_BUTTON_X_OFFSET, rowY, BUTTON_WIDTH, INPUT_HEIGHT, BTN_CANCEL, this::onClose
        ));
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;

        renderBackground(graphics, panelLeft, panelTop);
        renderLabels(graphics, panelLeft, panelTop);
        renderFetchStatus(graphics, panelLeft, panelTop);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBackground(GuiGraphics graphics, int panelLeft, int panelTop) {
        graphics.fill(0, 0, this.width, this.height, OVERLAY_COLOR);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, BG_COLOR);
        RGBAnimator.renderSimpleRGBBorder(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, BORDER_WIDTH, RGB_SPEED);
    }

    private void renderLabels(GuiGraphics graphics, int panelLeft, int panelTop) {
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, TITLE_TEXT, centerX, panelTop + TITLE_Y, COLOR_WHITE);

        graphics.drawString(this.font, LABEL_URL, panelLeft + PADDING, panelTop + URL_ROW_Y + LABEL_OFFSET_Y, COLOR_LABEL);
        graphics.drawString(this.font, LABEL_TITLE, panelLeft + PADDING, panelTop + TITLE_ROW_Y + LABEL_OFFSET_Y, COLOR_LABEL);
        graphics.drawString(this.font, LABEL_DURATION, panelLeft + PADDING, panelTop + DURATION_ROW_Y + LABEL_OFFSET_Y, COLOR_LABEL_DIM);
    }

    private void renderFetchStatus(GuiGraphics graphics, int panelLeft, int panelTop) {
        if (fetchStatus.isEmpty()) {
            return;
        }

        int statusColor = getStatusColor();
        int statusX = panelLeft + SAVE_BUTTON_X_OFFSET;
        int statusY = panelTop + TITLE_ROW_Y + LABEL_OFFSET_Y;
        graphics.drawString(this.font, fetchStatus, statusX, statusY, statusColor);
    }

    private int getStatusColor() {
        if (isFetching) {
            return COLOR_FETCHING;
        }
        return fetchStatus.startsWith(STATUS_PREFIX_SUCCESS) ? COLOR_SUCCESS : COLOR_ERROR;
    }

    // ==================== Keyboard Input ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== Metadata Fetching ====================

    private void fetchMetadata() {
        String urlValue = urlInput.getValue().trim();
        if (urlValue.isEmpty()) {
            fetchStatus = STATUS_PREFIX_ERROR + STATUS_ENTER_URL;
            return;
        }
        if (isFetching) {
            return;
        }

        isFetching = true;
        fetchStatus = STATUS_FETCHING;

        VideoMetadata.fetchMetadata(urlValue).thenAccept(this::handleMetadataResult)
            .exceptionally(this::handleMetadataError);
    }

    private void handleMetadataResult(VideoMetadata metadata) {
        Minecraft.getInstance().execute(() -> {
            isFetching = false;
            if (metadata != null) {
                applyMetadata(metadata);
                fetchStatus = STATUS_PREFIX_SUCCESS + STATUS_SUCCESS;
            } else {
                fetchStatus = STATUS_PREFIX_ERROR + STATUS_FAILED;
            }
        });
    }

    private void applyMetadata(VideoMetadata metadata) {
        String displayName = metadata.getDisplayName();
        if (!displayName.isEmpty()) {
            titleInput.setValue(displayName);
        }
        if (metadata.getDurationSeconds() > 0) {
            durationInput.setValue(formatDuration(metadata.getDurationSeconds()));
        }
    }

    private Void handleMetadataError(Throwable error) {
        Minecraft.getInstance().execute(() -> {
            isFetching = false;
            fetchStatus = STATUS_PREFIX_ERROR + STATUS_ERROR;
        });
        return null;
    }

    // ==================== Save & Close ====================

    private void saveAndClose() {
        String urlValue = urlInput.getValue().trim();
        if (urlValue.isEmpty()) {
            return;
        }

        String titleValue = titleInput.getValue().trim();
        int durationValue = parseDuration(durationInput.getValue());
        boolean loopValue = loopToggle.isToggled();
        boolean lockValue = lockToggle.isToggled();

        sendPacket(urlValue, titleValue, durationValue, loopValue, lockValue);
        this.onClose();
    }

    private void sendPacket(String urlValue, String titleValue, int durationValue, boolean loopValue, boolean lockValue) {
        if (packetSender != null) {
            packetSender.accept(new SetURLDataPacket(urlValue, titleValue, durationValue, loopValue, lockValue));
        }
    }

    // ==================== Duration Formatting ====================

    private String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "";
        }
        return String.format("%d:%02d", totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE);
    }

    private int parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        try {
            return parseDurationInternal(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseDurationInternal(String input) {
        if (input.contains(":")) {
            String[] parts = input.split(":");
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            return minutes * SECONDS_PER_MINUTE + seconds;
        }
        return Integer.parseInt(input.trim());
    }

    private boolean isDurationCharacter(String s) {
        return s.matches(DURATION_REGEX);
    }

    // ==================== Screen Properties ====================

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

