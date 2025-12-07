package com.rhythm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rhythm.client.light.ColoredLightPipeline;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject colored light rendering into the world rendering pipeline
 * Based on Shimmer mod's post-processing architecture
 * <p>
 * This hooks into LevelRenderer.renderLevel to add colored lighting effects
 * after world geometry is rendered but before debug overlays.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * Inject at the end of renderLevel to render colored lights as post-processing
     * Injects before weather rendering to ensure lights appear behind rain/snow
     */
    @Inject(
        method = "renderLevel",
        at = @At(value = "TAIL")
    )
    private void onRenderLevel(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f projectionMatrix,
        Matrix4f frustumMatrix,
        CallbackInfo ci
    ) {
        try {

            PoseStack poseStack = new PoseStack();


            poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));


            ColoredLightPipeline.getInstance().render(poseStack, projectionMatrix, camera);
        } catch (Exception e) {
            System.err.println("[RhythmMod LevelRenderer HOOK] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

