package com.rhythm.mixin;

import com.rhythm.client.gui.DJControllerScreen;
import com.rhythm.client.gui.URLInputScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable the background blur effect for RhythmMod screens.
 * In Minecraft 1.21+, renderBackground() applies a blur shader that affects
 * everything rendered after it. This mixin prevents that for our custom screens.
 */
@Mixin(Screen.class)
public class ScreenBlurMixin {

    /**
     * Cancel the blur rendering for our custom screens.
     * We handle our own background rendering without blur.
     */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void rhythmmod$cancelBlurForCustomScreens(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;


        if (self instanceof DJControllerScreen || self instanceof URLInputScreen) {

            ci.cancel();
        }
    }
}

