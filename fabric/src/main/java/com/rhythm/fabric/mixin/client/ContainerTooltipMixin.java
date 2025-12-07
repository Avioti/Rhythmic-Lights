package com.rhythm.fabric.mixin.client;

import com.rhythm.fabric.client.TooltipState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to detect RhythmMod items for custom tooltip styling.
 * Sets tooltip state flag before/after tooltip rendering.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTooltipMixin {

    @Unique
    private static final String RHYTHMMOD_NAMESPACE = "rhythmmod";

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void rhythmmod$beforeTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        ItemStack hoveredStack = getHoveredItemStack();
        TooltipState.setRhythmModTooltip(rhythmmod$isRhythmItem(hoveredStack));
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void rhythmmod$afterTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        TooltipState.setRhythmModTooltip(false);
    }

    @Unique
    private ItemStack getHoveredItemStack() {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return this.hoveredSlot.getItem();
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private static boolean rhythmmod$isRhythmItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
            .getNamespace()
            .equals(RHYTHMMOD_NAMESPACE);
    }
}
