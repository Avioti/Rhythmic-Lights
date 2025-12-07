package com.rhythm.mixin.client;


import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to apply RGB lighting to block rendering.
 * This intercepts the block rendering pipeline and applies our colored lighting.
 *
 * This works in conjunction with LightTextureMixin to provide two levels of colored lighting:
 * 1. Lightmap tinting (affects all blocks globally based on nearby lights)
 * 2. Per-block vertex tinting (more precise per-block color application)
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    // Store the current block position being rendered
    private static final ThreadLocal<BlockPos> currentBlockPos = ThreadLocal.withInitial(() -> BlockPos.ZERO);
    private static final ThreadLocal<BlockAndTintGetter> currentLevel = ThreadLocal.withInitial(() -> null);

    /**
     * Capture the block position and level when tesselation starts
     */
    @ModifyVariable(
        method = "tesselateBlock",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private BlockAndTintGetter rhythmmod$captureLevel(BlockAndTintGetter level) {
        currentLevel.set(level);
        return level;
    }

    @ModifyVariable(
        method = "tesselateBlock",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private BlockPos rhythmmod$capturePos(BlockPos pos) {
        currentBlockPos.set(pos);
        return pos;
    }

    // DISABLED: This causes global per-block tinting which affects all blocks
    // ColoredLightPipeline handles rendering instead (Shimmer-style approach)
    /*
    @ModifyVariable(
        method = "putQuadData",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int rhythmmod$modifyLightColor(int packedLight) {
        try {
            BlockPos pos = currentBlockPos.get();
            BlockAndTintGetter level = currentLevel.get();

            if (pos != null && level != null && level instanceof net.minecraft.world.level.Level worldLevel) {
                // Apply our colored light tinting
                return LightmapColorInjector.getInstance().getColorTintAt(
                    worldLevel,
                    pos,
                    packedLight
                );
            }
        } catch (Exception e) {
            // Don't crash if something goes wrong
        }

        return packedLight;
    }
    */
}


