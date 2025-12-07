package com.rhythm.client.gui.widget;

import com.rhythm.client.gui.RGBAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Custom styled text input field with RGB animated border.
 * <p>
 * Features:
 * <ul>
 *   <li>Dark transparent background</li>
 *   <li>RGB animated border when focused</li>
 *   <li>Full keyboard support (copy, paste, cut, select all)</li>
 *   <li>Text selection with Shift+Arrow keys</li>
 *   <li>Cursor navigation with arrow keys, Home, End</li>
 * </ul>
 */
public class StyledEditBox extends AbstractWidget {

    // ==================== Colors ====================

    private static final int BG_COLOR = 0xDD000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFF666666;
    private static final int UNFOCUSED_BORDER_COLOR = 0xFF444444;
    private static final int SELECTION_COLOR = 0x880088FF;

    // ==================== Animation ====================

    private static final float RGB_SPEED = 0.5f;
    private static final int CURSOR_BLINK_RATE = 10;

    // ==================== Layout ====================

    private static final int TEXT_PADDING_X = 4;
    private static final int TEXT_PADDING_TOTAL = 10;
    private static final int FONT_HEIGHT = 8;
    private static final int CURSOR_WIDTH = 1;
    private static final int CURSOR_PADDING_Y = 1;
    private static final int CURSOR_HEIGHT = 9;
    private static final int BORDER_WIDTH = 1;
    private static final int DEFAULT_MAX_LENGTH = 256;

    // ==================== State ====================

    private String value = "";
    private String hint = "";
    private int maxLength = DEFAULT_MAX_LENGTH;
    private boolean isFocused = false;
    private int cursorBlinkCounter = 0;

    /** Cursor position (index in value string) */
    private int cursorPos = 0;
    /** Selection anchor position (-1 if no selection) */
    private int selectionAnchor = -1;
    /** Scroll offset for long text */
    private int scrollOffset = 0;

    private Consumer<String> onChanged;
    private Predicate<String> filter = s -> true;

    // ==================== Constructor ====================

    public StyledEditBox(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    // ==================== Selection Helpers ====================

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursorPos;
    }

    private int getSelectionStart() {
        return Math.min(cursorPos, selectionAnchor);
    }

    private int getSelectionEnd() {
        return Math.max(cursorPos, selectionAnchor);
    }

    private String getSelectedText() {
        if (!hasSelection()) {
            return "";
        }
        return value.substring(getSelectionStart(), getSelectionEnd());
    }

    private void clearSelection() {
        selectionAnchor = -1;
    }

    private void selectAll() {
        selectionAnchor = 0;
        cursorPos = value.length();
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        value = value.substring(0, start) + value.substring(end);
        cursorPos = start;
        clearSelection();
        notifyChanged();
    }

    // ==================== Rendering ====================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        renderBackground(graphics);
        renderBorder(graphics);
        renderTextWithSelection(graphics, font);
    }

    private void renderBackground(GuiGraphics graphics) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, BG_COLOR);
    }

    private void renderBorder(GuiGraphics graphics) {
        if (isFocused) {
            RGBAnimator.renderSimpleRGBBorder(graphics, getX(), getY(), width, height, BORDER_WIDTH, RGB_SPEED);
        } else {
            graphics.renderOutline(getX(), getY(), width, height, UNFOCUSED_BORDER_COLOR);
        }
    }

    private void renderTextWithSelection(GuiGraphics graphics, Font font) {
        int textX = getX() + TEXT_PADDING_X;
        int textY = getY() + (height - FONT_HEIGHT) / 2;
        int maxWidth = width - TEXT_PADDING_TOTAL;

        if (value.isEmpty() && !isFocused) {
            graphics.drawString(font, hint, textX, textY, HINT_COLOR, false);
            return;
        }

        // Calculate visible portion
        updateScrollOffset(font, maxWidth);
        String visibleText = getVisibleText(font, maxWidth);
        int visibleStart = scrollOffset;

        // Render selection highlight
        if (hasSelection() && isFocused) {
            renderSelectionHighlight(graphics, font, textX, textY, visibleStart, visibleText);
        }

        // Render text
        graphics.drawString(font, visibleText, textX, textY, TEXT_COLOR, false);

        // Render cursor
        renderCursor(graphics, font, textX, textY);
    }

    private void updateScrollOffset(Font font, int maxWidth) {
        // Ensure cursor is visible
        if (cursorPos < scrollOffset) {
            scrollOffset = cursorPos;
        }

        String textToCursor = value.substring(scrollOffset, cursorPos);
        while (font.width(textToCursor) > maxWidth - CURSOR_WIDTH && scrollOffset < cursorPos) {
            scrollOffset++;
            textToCursor = value.substring(scrollOffset, cursorPos);
        }
    }

    private String getVisibleText(Font font, int maxWidth) {
        if (scrollOffset >= value.length()) {
            scrollOffset = Math.max(0, value.length() - 1);
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        String text = scrollOffset < value.length() ? value.substring(scrollOffset) : "";
        while (font.width(text) > maxWidth && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void renderSelectionHighlight(GuiGraphics graphics, Font font, int textX, int textY,
                                           int visibleStart, String visibleText) {
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();

        // Clamp selection to visible area
        int visibleEnd = visibleStart + visibleText.length();
        int drawStart = Math.max(selStart, visibleStart) - visibleStart;
        int drawEnd = Math.min(selEnd, visibleEnd) - visibleStart;

        if (drawStart >= drawEnd || drawStart >= visibleText.length()) {
            return;
        }

        drawStart = Math.max(0, drawStart);
        drawEnd = Math.min(visibleText.length(), drawEnd);

        String beforeSel = visibleText.substring(0, drawStart);
        String selection = visibleText.substring(drawStart, drawEnd);

        int selX1 = textX + font.width(beforeSel);
        int selX2 = selX1 + font.width(selection);

        graphics.fill(selX1, textY - 1, selX2, textY + CURSOR_HEIGHT, SELECTION_COLOR);
    }

    private void renderCursor(GuiGraphics graphics, Font font, int textX, int textY) {
        if (!isFocused) {
            return;
        }

        cursorBlinkCounter++;
        if (!shouldShowCursor()) {
            return;
        }

        int cursorX = calculateCursorX(font, textX);
        int cursorColor = RGBAnimator.getRGBColor(RGB_SPEED);
        graphics.fill(cursorX, textY - CURSOR_PADDING_Y, cursorX + CURSOR_WIDTH, textY + CURSOR_HEIGHT, cursorColor);
    }

    private boolean shouldShowCursor() {
        return (cursorBlinkCounter / CURSOR_BLINK_RATE) % 2 == 0;
    }

    private int calculateCursorX(Font font, int textX) {
        int cursorDrawPos = Math.max(0, Math.min(cursorPos, value.length()));
        int scrollDrawOffset = Math.max(0, Math.min(scrollOffset, cursorDrawPos));
        String textBeforeCursor = value.substring(scrollDrawOffset, cursorDrawPos);
        return textX + font.width(textBeforeCursor);
    }

    // ==================== Mouse Interaction ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasClicked = isMouseOver(mouseX, mouseY);
        isFocused = wasClicked;

        if (wasClicked && button == 0) {
            // Position cursor based on click
            Font font = Minecraft.getInstance().font;
            int textX = getX() + TEXT_PADDING_X;
            int clickX = (int) mouseX - textX;

            String visibleText = scrollOffset < value.length() ? value.substring(scrollOffset) : "";
            int newPos = scrollOffset;

            for (int i = 0; i <= visibleText.length(); i++) {
                int charWidth = font.width(visibleText.substring(0, i));
                if (charWidth > clickX) {
                    break;
                }
                newPos = scrollOffset + i;
            }

            cursorPos = Math.min(newPos, value.length());
            clearSelection();
            cursorBlinkCounter = 0;
        }

        return wasClicked;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isFocused || button != 0) {
            return false;
        }

        // Start selection if not already selecting
        if (selectionAnchor < 0) {
            selectionAnchor = cursorPos;
        }

        // Update cursor position based on drag
        Font font = Minecraft.getInstance().font;
        int textX = getX() + TEXT_PADDING_X;
        int clickX = (int) mouseX - textX;

        String visibleText = scrollOffset < value.length() ? value.substring(scrollOffset) : "";
        int newPos = scrollOffset;

        for (int i = 0; i <= visibleText.length(); i++) {
            int charWidth = font.width(visibleText.substring(0, i));
            if (charWidth > clickX) {
                break;
            }
            newPos = scrollOffset + i;
        }

        cursorPos = Math.max(0, Math.min(newPos, value.length()));
        return true;
    }

    // ==================== Keyboard Interaction ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused) {
            return false;
        }

        boolean isCtrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean isShiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (isCtrlHeld) {
            return handleCtrlShortcut(keyCode, isShiftHeld);
        }

        return handleNavigationKey(keyCode, isShiftHeld);
    }

    private boolean handleCtrlShortcut(int keyCode, boolean isShiftHeld) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_V -> handlePaste();
            case GLFW.GLFW_KEY_C -> handleCopy();
            case GLFW.GLFW_KEY_X -> handleCut();
            case GLFW.GLFW_KEY_A -> {
                selectAll();
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursorToPreviousWord(isShiftHeld);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursorToNextWord(isShiftHeld);
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleNavigationKey(int keyCode, boolean isShiftHeld) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> handleBackspace();
            case GLFW.GLFW_KEY_DELETE -> handleDelete();
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(-1, isShiftHeld);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(1, isShiftHeld);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursorTo(0, isShiftHeld);
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursorTo(value.length(), isShiftHeld);
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE -> handleUnfocus();
            default -> false;
        };
    }

    private void moveCursor(int delta, boolean selecting) {
        if (selecting) {
            if (selectionAnchor < 0) {
                selectionAnchor = cursorPos;
            }
        } else if (hasSelection()) {
            // Move to edge of selection
            cursorPos = delta < 0 ? getSelectionStart() : getSelectionEnd();
            clearSelection();
            return;
        }

        cursorPos = Math.max(0, Math.min(cursorPos + delta, value.length()));

        if (!selecting) {
            clearSelection();
        }
        cursorBlinkCounter = 0;
    }

    private void moveCursorTo(int position, boolean selecting) {
        if (selecting && selectionAnchor < 0) {
            selectionAnchor = cursorPos;
        }

        cursorPos = Math.max(0, Math.min(position, value.length()));

        if (!selecting) {
            clearSelection();
        }
        cursorBlinkCounter = 0;
    }

    private void moveCursorToPreviousWord(boolean selecting) {
        if (selecting && selectionAnchor < 0) {
            selectionAnchor = cursorPos;
        }

        // Skip whitespace, then skip word characters
        int pos = cursorPos;
        while (pos > 0 && Character.isWhitespace(value.charAt(pos - 1))) {
            pos--;
        }
        while (pos > 0 && !Character.isWhitespace(value.charAt(pos - 1))) {
            pos--;
        }
        cursorPos = pos;

        if (!selecting) {
            clearSelection();
        }
    }

    private void moveCursorToNextWord(boolean selecting) {
        if (selecting && selectionAnchor < 0) {
            selectionAnchor = cursorPos;
        }

        // Skip word characters, then skip whitespace
        int pos = cursorPos;
        while (pos < value.length() && !Character.isWhitespace(value.charAt(pos))) {
            pos++;
        }
        while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) {
            pos++;
        }
        cursorPos = pos;

        if (!selecting) {
            clearSelection();
        }
    }

    private boolean handlePaste() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard.isEmpty()) {
            return true;
        }

        if (hasSelection()) {
            deleteSelection();
        }

        String filtered = filterClipboardContent(clipboard);
        if (!filtered.isEmpty()) {
            insertTextAtCursor(filtered);
        }
        return true;
    }

    private void insertTextAtCursor(String text) {
        String newValue = value.substring(0, cursorPos) + text + value.substring(cursorPos);
        if (newValue.length() <= maxLength && filter.test(newValue)) {
            value = newValue;
            cursorPos += text.length();
            notifyChanged();
        }
    }

    private String filterClipboardContent(String clipboard) {
        StringBuilder filtered = new StringBuilder();
        for (char c : clipboard.toCharArray()) {
            if (Character.isISOControl(c) && c != '\t') {
                continue;
            }
            if (c == '\t') {
                c = ' ';
            }
            String test = value.substring(0, cursorPos) + filtered + c + value.substring(cursorPos);
            if (test.length() <= maxLength && filter.test(test)) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    private boolean handleCopy() {
        String textToCopy = hasSelection() ? getSelectedText() : value;
        Minecraft.getInstance().keyboardHandler.setClipboard(textToCopy);
        return true;
    }

    private boolean handleCut() {
        if (hasSelection()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
            deleteSelection();
        } else {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            value = "";
            cursorPos = 0;
            notifyChanged();
        }
        return true;
    }

    private boolean handleBackspace() {
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (cursorPos > 0) {
            value = value.substring(0, cursorPos - 1) + value.substring(cursorPos);
            cursorPos--;
            notifyChanged();
        }
        return true;
    }

    private boolean handleDelete() {
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (cursorPos < value.length()) {
            value = value.substring(0, cursorPos) + value.substring(cursorPos + 1);
            notifyChanged();
        }
        return true;
    }

    private boolean handleUnfocus() {
        isFocused = false;
        clearSelection();
        return true;
    }

    // ==================== Character Input ====================

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused || Character.isISOControl(chr)) {
            return false;
        }

        // Delete selection first if any
        if (hasSelection()) {
            deleteSelection();
        }

        if (value.length() < maxLength) {
            String newValue = value.substring(0, cursorPos) + chr + value.substring(cursorPos);
            if (filter.test(newValue)) {
                value = newValue;
                cursorPos++;
                notifyChanged();
            }
        }
        return true;
    }

    // ==================== Change Notification ====================

    private void notifyChanged() {
        if (onChanged != null) {
            onChanged.accept(value);
        }
    }

    // ==================== Getters & Setters ====================

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value != null ? value : "";
        this.cursorPos = this.value.length();
        this.scrollOffset = 0;
        clearSelection();
    }

    public void setHint(String hint) {
        this.hint = hint != null ? hint : "";
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    public void setOnChanged(Consumer<String> onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public boolean isFocused() {
        return isFocused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.isFocused = focused;
        if (!focused) {
            clearSelection();
        }
    }

    // ==================== Narration ====================

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // No narration needed for this widget
    }
}

