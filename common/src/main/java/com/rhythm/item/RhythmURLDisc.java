package com.rhythm.item;

import com.rhythm.client.gui.RGBText;
import com.rhythm.network.OpenURLScreenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

import static com.rhythm.util.RhythmConstants.*;

/**
 * Custom URL Music Disc - plays audio from YouTube, SoundCloud, etc.
 */
public class RhythmURLDisc extends Item {

    // ==================== Constants ====================

    private static final int URL_DISPLAY_MAX_LENGTH = 35;
    private static final int URL_TRUNCATE_LENGTH = 32;
    private static final String URL_ELLIPSIS = "...";
    private static final int SECONDS_PER_MINUTE = 60;

    private static final String HEADER_ICON = "♪ ";
    private static final String HEADER_TEXT = "Custom Music Disc";
    private static final String ICON_LOCKED = "🔒 ";
    private static final String ICON_LOOP = "🔁 ";

    private static final String LABEL_TITLE = "Title: ";
    private static final String LABEL_URL = "URL: ";
    private static final String LABEL_DURATION = "Duration: ";

    private static final String MSG_LOCKED = "🔒 Locked";
    private static final String MSG_NO_URL = "No URL configured";
    private static final String MSG_RIGHT_CLICK = "Right-click to configure";
    private static final String MSG_SUPPORTS = "Supports: ";
    private static final String MSG_DIRECT_AUDIO = "and direct audio URLs";
    private static final String TEXT_LOCKED = "Locked";
    private static final String TEXT_LOOP = "Loop Enabled";
    private static final String TEXT_YOUTUBE = "YouTube";
    private static final String TEXT_SOUNDCLOUD = "SoundCloud";

    private static final float LOOP_GRADIENT_START = 0.25f;
    private static final float LOOP_GRADIENT_END = 0.35f;
    private static final float YOUTUBE_GRADIENT_START = 0.0f;
    private static final float YOUTUBE_GRADIENT_END = 0.05f;
    private static final float SOUNDCLOUD_GRADIENT_START = 0.05f;
    private static final float SOUNDCLOUD_GRADIENT_END = 0.1f;

    // ==================== Constructor ====================

    public RhythmURLDisc() {
        super(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
            .jukeboxPlayable(JUKEBOX_SONG));
    }

    // ==================== Use Action ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            handleServerUse(serverPlayer, stack);
        }

        return InteractionResultHolder.success(stack);
    }

    private void handleServerUse(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = getDiscTag(stack);

        if (tag.getBoolean(NBT_LOCK)) {
            player.displayClientMessage(Component.literal(MSG_LOCKED).withStyle(ChatFormatting.RED), true);
        } else {
            OpenURLScreenPacket.sendToPlayer(
                player,
                tag.getString(NBT_URL),
                tag.getString(NBT_TITLE),
                tag.getInt(NBT_DURATION),
                tag.getBoolean(NBT_LOOP)
            );
        }
    }

    // ==================== Tooltip ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = getDiscTag(stack);
        DiscInfo info = new DiscInfo(tag);

        addHeader(tooltip);

        if (!info.url.isEmpty()) {
            addConfiguredDiscTooltip(tooltip, info);
        } else {
            addEmptyDiscTooltip(tooltip);
        }
    }

    private record DiscInfo(String url, String title, boolean isLocked, boolean isLoop, int duration) {
        DiscInfo(CompoundTag tag) {
            this(
                tag.getString(NBT_URL),
                tag.getString(NBT_TITLE),
                tag.getBoolean(NBT_LOCK),
                tag.getBoolean(NBT_LOOP),
                tag.getInt(NBT_DURATION)
            );
        }
    }

    private void addHeader(List<Component> tooltip) {
        tooltip.add(RGBText.rainbow(HEADER_ICON + HEADER_TEXT));
        tooltip.add(Component.literal(""));
    }

    private void addConfiguredDiscTooltip(List<Component> tooltip, DiscInfo info) {
        addTitleLine(tooltip, info.title);
        addUrlLine(tooltip, info.url);
        addDurationLine(tooltip, info.duration);
        addStatusIcons(tooltip, info.isLocked, info.isLoop);
    }

    private void addTitleLine(List<Component> tooltip, String title) {
        if (!title.isEmpty()) {
            tooltip.add(Component.literal(LABEL_TITLE)
                .withStyle(ChatFormatting.GRAY)
                .append(RGBText.aqua(title)));
        }
    }

    private void addUrlLine(List<Component> tooltip, String url) {
        String displayUrl = truncateUrl(url);
        tooltip.add(Component.literal(LABEL_URL)
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(displayUrl).withStyle(ChatFormatting.DARK_GRAY)));
    }

    private String truncateUrl(String url) {
        return url.length() > URL_DISPLAY_MAX_LENGTH
            ? url.substring(0, URL_TRUNCATE_LENGTH) + URL_ELLIPSIS
            : url;
    }

    private void addDurationLine(List<Component> tooltip, int duration) {
        if (duration > 0) {
            String formatted = formatDuration(duration);
            tooltip.add(Component.literal(LABEL_DURATION)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatted).withStyle(ChatFormatting.WHITE)));
        }
    }

    private String formatDuration(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE);
    }

    private void addStatusIcons(List<Component> tooltip, boolean isLocked, boolean isLoop) {
        if (!isLocked && !isLoop) {
            return;
        }

        tooltip.add(Component.literal(""));

        if (isLocked) {
            tooltip.add(Component.literal(ICON_LOCKED)
                .withStyle(ChatFormatting.WHITE)
                .append(RGBText.gold(TEXT_LOCKED)));
        }
        if (isLoop) {
            tooltip.add(Component.literal(ICON_LOOP)
                .withStyle(ChatFormatting.WHITE)
                .append(RGBText.gradient(TEXT_LOOP, LOOP_GRADIENT_START, LOOP_GRADIENT_END)));
        }
    }

    private void addEmptyDiscTooltip(List<Component> tooltip) {
        tooltip.add(Component.literal(MSG_NO_URL).withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(MSG_RIGHT_CLICK).withStyle(ChatFormatting.GRAY));
        addSupportedPlatforms(tooltip);
    }

    private void addSupportedPlatforms(List<Component> tooltip) {
        tooltip.add(Component.literal(MSG_SUPPORTS)
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(RGBText.gradient(TEXT_YOUTUBE, YOUTUBE_GRADIENT_START, YOUTUBE_GRADIENT_END))
            .append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY))
            .append(RGBText.gradient(TEXT_SOUNDCLOUD, SOUNDCLOUD_GRADIENT_START, SOUNDCLOUD_GRADIENT_END))
            .append(Component.literal(",").withStyle(ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.literal(MSG_DIRECT_AUDIO).withStyle(ChatFormatting.DARK_GRAY));
    }

    // ==================== Static Data Access ====================

    private static CompoundTag getDiscTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /**
     * Configure a disc with URL data.
     */
    public static void setDiscData(ItemStack stack, String url, String title, int duration, boolean loop, boolean lock) {
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_URL, url);
        tag.putString(NBT_TITLE, title != null ? title : "");
        tag.putInt(NBT_DURATION, duration);
        tag.putBoolean(NBT_LOOP, loop);
        tag.putBoolean(NBT_LOCK, lock);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Configure a disc with URL data (without title - backward compatibility).
     */
    public static void setDiscData(ItemStack stack, String url, int duration, boolean loop, boolean lock) {
        setDiscData(stack, url, "", duration, loop, lock);
    }

    public static String getUrl(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag().getString(NBT_URL) : "";
    }

    public static String getTitle(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag().getString(NBT_TITLE) : "";
    }

    public static void setTitle(ItemStack stack, String title) {
        CompoundTag tag = getDiscTag(stack);
        tag.putString(NBT_TITLE, title);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isValidUrlDisc(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof RhythmURLDisc)) {
            return false;
        }
        String url = getUrl(stack);
        return url != null && !url.isEmpty();
    }
}

