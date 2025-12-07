package com.rhythm.mixin;

import com.rhythm.util.RhythmLampCraftingHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept crafting result slot interactions
 * This allows us to detect when a rhythm lamp is crafted and return the tuning wand
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    /**
     * Thread-local storage for whether the tuning wand was found before crafting
     * We use this to pass information from HEAD to TAIL injection
     */
    @Unique
    private boolean rhythmmod$hadTuningWand = false;

    /**
     * Inject at the beginning of onTake to capture ingredients BEFORE they are consumed
     * This is critical because by TAIL, the crafting slots are already cleared
     */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeHead(Player player, ItemStack stack, CallbackInfo ci) {
        // Check if this is a rhythm lamp craft with tuning wand BEFORE ingredients are consumed
        rhythmmod$hadTuningWand = RhythmLampCraftingHandler.isRhythmLamp(stack)
            && RhythmLampCraftingHandler.containsTuningWand(this.craftSlots);
    }

    /**
     * Inject after the player takes an item from the crafting result slot
     * At this point the crafting is complete, so we return the tuning wand
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void onTakeTail(Player player, ItemStack stack, CallbackInfo ci) {

        if (rhythmmod$hadTuningWand) {
            RhythmLampCraftingHandler.returnTuningWand(player);
            rhythmmod$hadTuningWand = false;
        }
    }
}

