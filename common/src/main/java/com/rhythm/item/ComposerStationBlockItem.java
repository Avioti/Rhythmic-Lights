package com.rhythm.item;

import com.rhythm.client.gui.RGBText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Custom BlockItem for the DJ Station.
 * Ownership is set when a player first links to it using the Tuning Wand.
 */
public class ComposerStationBlockItem extends BlockItem {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComposerStationBlockItem.class);
    private static final String HEADER = "♪ DJ Command Center";
    private static final String BULLET = "✦ ";

    private static final float BASS_VOLUME_MIN_HUE = 0.55f;
    private static final float BASS_VOLUME_MAX_HUE = 0.65f;

    public ComposerStationBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        if (!level.isClientSide && player != null) {
            LOGGER.debug("DJ Station placed at {} (unclaimed - use Tuning Wand to claim)", pos);
        }

        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        addHeader(tooltip);
        addDescription(tooltip);
        addFeatures(tooltip);
        addOwnershipHint(tooltip);
    }

    private void addHeader(List<Component> tooltip) {
        tooltip.add(RGBText.rainbow(HEADER));
        tooltip.add(Component.empty());
    }

    private void addDescription(List<Component> tooltip) {
        tooltip.add(Component.literal("Control linked jukeboxes").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("with play, pause, EQ & more.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
    }

    private void addFeatures(List<Component> tooltip) {
        tooltip.add(createFeatureLine(RGBText.aqua("Play/Pause/Stop Controls")));
        tooltip.add(createFeatureLine(RGBText.purple("10-Band Equalizer")));
        tooltip.add(createFeatureLine(RGBText.gradient("Bass Boost & Volume", BASS_VOLUME_MIN_HUE, BASS_VOLUME_MAX_HUE)));
        tooltip.add(createFeatureLine(RGBText.gold("Loop Mode")));
        tooltip.add(Component.empty());
    }

    private Component createFeatureLine(Component feature) {
        return Component.literal(BULLET).withStyle(ChatFormatting.WHITE).append(feature);
    }

    private void addOwnershipHint(List<Component> tooltip) {
        tooltip.add(Component.literal("First to claim owns it!")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}

