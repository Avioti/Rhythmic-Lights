package com.rhythm.item;

import com.rhythm.client.gui.RGBText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Block item for Rhythm Bulb with themed tooltip.
 * Displays usage instructions and features with animated RGB effects.
 */
public class RhythmBulbBlockItem extends BlockItem {

    private static final String HEADER = "♪ Audio-Reactive Light";
    private static final String BULLET = "✦ ";

    public RhythmBulbBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        addHeader(tooltip);
        addDescription(tooltip);
        addFeatures(tooltip);
        addUsageHint(tooltip);
    }

    private void addHeader(List<Component> tooltip) {
        tooltip.add(RGBText.rainbow(HEADER));
        tooltip.add(Component.empty());
    }

    private void addDescription(List<Component> tooltip) {
        tooltip.add(Component.literal("Syncs with music playing").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("from a linked jukebox.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
    }

    private void addFeatures(List<Component> tooltip) {
        tooltip.add(createFeatureLine(RGBText.aqua("Tunable Frequency Channel")));
        tooltip.add(createFeatureLine(RGBText.purple("RGB Color Modes")));
        tooltip.add(createFeatureLine(RGBText.gold("Particle Effects")));
        tooltip.add(Component.empty());
    }

    private Component createFeatureLine(Component feature) {
        return Component.literal(BULLET).withStyle(ChatFormatting.WHITE).append(feature);
    }

    private void addUsageHint(List<Component> tooltip) {
        tooltip.add(Component.literal("Use ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Tuning Wand").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" to link & tune").withStyle(ChatFormatting.DARK_GRAY)));
    }
}

