package com.rhythm.util;

import com.rhythm.RhythmMod;
import com.rhythm.registry.RhythmModBlocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Handles returning the tuning wand when crafting rhythm lamps
 *
 * When a player crafts any rhythm lamp using a tuning wand, this handler
 * detects the craft and returns the tuning wand to the player's inventory.
 */
public class RhythmLampCraftingHandler {

    /**
     * Checks if the given item is a rhythm lamp
     */
    public static boolean isRhythmLamp(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Block block = Block.byItem(stack.getItem());

        // Check if it's one of the pre-tuned rhythm lamps
        return block == RhythmModBlocks.SUB_BASS_LAMP.get() ||
               block == RhythmModBlocks.DEEP_BASS_LAMP.get() ||
               block == RhythmModBlocks.BASS_LAMP.get() ||
               block == RhythmModBlocks.LOW_MIDS_LAMP.get() ||
               block == RhythmModBlocks.MID_LOWS_LAMP.get() ||
               block == RhythmModBlocks.MIDS_LAMP.get() ||
               block == RhythmModBlocks.MID_HIGHS_LAMP.get() ||
               block == RhythmModBlocks.HIGH_MIDS_LAMP.get() ||
               block == RhythmModBlocks.HIGHS_LAMP.get() ||
               block == RhythmModBlocks.VERY_HIGHS_LAMP.get() ||
               block == RhythmModBlocks.ULTRA_LAMP.get() ||
               block == RhythmModBlocks.TOP_LAMP.get();
    }

    /**
     * Checks if the crafting inventory contains a tuning wand
     * This must be called BEFORE the crafting operation consumes ingredients
     */
    public static boolean containsTuningWand(CraftingContainer inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(RhythmMod.TUNING_WAND_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a tuning wand to the player's inventory
     * Called after a rhythm lamp is crafted
     */
    public static void returnTuningWand(Player player) {
        // Only process on server side
        if (player.level().isClientSide) {
            return;
        }

        ItemStack tuningWand = new ItemStack(RhythmMod.TUNING_WAND_ITEM.get());

        // Try to add to inventory first
        if (!player.getInventory().add(tuningWand)) {
            // If inventory is full, drop it at the player's feet
            player.drop(tuningWand, false);
        }
    }
}

