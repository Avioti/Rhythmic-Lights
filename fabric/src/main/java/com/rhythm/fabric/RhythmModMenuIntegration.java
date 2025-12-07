package com.rhythm.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.rhythm.config.screen.RhythmConfigScreen;

/**
 * Mod Menu integration for Fabric.
 * Registers the RhythmMod config screen in Mod Menu.
 */
public class RhythmModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return RhythmConfigScreen::create;
    }
}

