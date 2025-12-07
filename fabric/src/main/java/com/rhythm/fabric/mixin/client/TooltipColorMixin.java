package com.rhythm.fabric.mixin.client;

import com.rhythm.fabric.client.TooltipState;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Mixin to intercept tooltip gradient rendering and replace colors for RhythmMod items.
 *
 * Minecraft 1.21.1 tooltip uses fillGradient for borders and background.
 * We intercept the fillGradient calls and modify both color arguments.
 */
@Mixin(GuiGraphics.class)
public abstract class TooltipColorMixin {

    // Default vanilla tooltip colors
    @Unique
    private static final int VANILLA_BORDER_START = 0x505000FF;
    @Unique
    private static final int VANILLA_BORDER_END = 0x5028007F;
    @Unique
    private static final int VANILLA_BACKGROUND = 0xF0100010;

    // Our custom background color (nearly black)
    @Unique
    private static final int RHYTHM_BACKGROUND = 0xF0080808;

    /**
     * Modify both color arguments in the fillGradient call at once using ModifyArgs.
     * This ensures we catch all color modifications together.
     */
    @ModifyArgs(
        method = "fillGradient(IIIIIII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V")
    )
    private void rhythmmod$modifyGradientColors(Args args) {
        if (!TooltipState.isRhythmModTooltip()) {
            return;
        }

        // Args: RenderType, x1, y1, x2, y2, z, colorA, colorB
        // Indices: 0,        1,  2,  3,  4,  5, 6,      7
        int colorA = args.get(6);
        int colorB = args.get(7);

        // Check if either color is a tooltip color and replace both
        boolean isTooltipColorA = rhythmmod$isTooltipColor(colorA);
        boolean isTooltipColorB = rhythmmod$isTooltipColor(colorB);

        if (isTooltipColorA || isTooltipColorB) {
            // Use the same animated color for both to ensure uniform border
            int animatedColor = TooltipState.getAnimatedBorderColor(true);

            // But keep background separate
            if (colorA == VANILLA_BACKGROUND) {
                args.set(6, RHYTHM_BACKGROUND);
            } else if (isTooltipColorA) {
                args.set(6, animatedColor);
            }

            if (colorB == VANILLA_BACKGROUND) {
                args.set(7, RHYTHM_BACKGROUND);
            } else if (isTooltipColorB) {
                args.set(7, animatedColor);
            }
        }
    }

    /**
     * Check if a color is one of the vanilla tooltip colors.
     */
    @Unique
    private boolean rhythmmod$isTooltipColor(int color) {
        return color == VANILLA_BORDER_START ||
               color == VANILLA_BORDER_END ||
               color == VANILLA_BACKGROUND;
    }
}
