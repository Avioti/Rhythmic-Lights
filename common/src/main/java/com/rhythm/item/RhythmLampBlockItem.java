package com.rhythm.item;

import com.rhythm.client.gui.RGBText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Custom BlockItem for Rhythm Lamps that displays frequency information in tooltips.
 * Pre-tuned lamps that react to specific frequency ranges with animated RGB effects.
 */
public class RhythmLampBlockItem extends BlockItem {

    private static final String HEADER = "♪ Pre-Tuned Audio Light";
    private static final String TOOLTIP_SUFFIX = ".tooltip";

    private final String translationKey;

    public RhythmLampBlockItem(Block block, Properties properties, String translationKey) {
        super(block, properties);
        this.translationKey = translationKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        addHeader(tooltip);
        addFrequencyInfo(tooltip);
        addDescription(tooltip);
        addUsageHint(tooltip);
    }

    private void addHeader(List<Component> tooltip) {
        tooltip.add(RGBText.rainbow(HEADER));
        tooltip.add(Component.empty());
    }

    private void addFrequencyInfo(List<Component> tooltip) {
        String frequencyText = I18n.get(translationKey + TOOLTIP_SUFFIX);
        tooltip.add(Component.literal("Frequency: ").withStyle(ChatFormatting.GRAY)
            .append(RGBText.gold(frequencyText)));
        tooltip.add(Component.empty());
    }

    private void addDescription(List<Component> tooltip) {
        tooltip.add(Component.literal("Pre-tuned to a specific").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("frequency range. No tuning").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("needed - just place & link!").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
    }

    private void addUsageHint(List<Component> tooltip) {
        tooltip.add(Component.literal("Use ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Tuning Wand").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" to link").withStyle(ChatFormatting.DARK_GRAY)));
    }
}

