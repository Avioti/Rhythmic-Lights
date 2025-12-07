package com.rhythm.fabric.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.rhythm.audio.executable.RhythmExecutable;
import com.rhythm.client.gui.DownloadProgressOverlay;
import com.rhythm.client.gui.LoadingOverlay;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/**
 * Client-side commands for RhythmMod.
 * <p>
 * Available commands:
 * <ul>
 *   <li>/rhythmmod cancel - Cancel all downloads and clear overlays</li>
 *   <li>/rhythmmod clearoverlay - Force clear stuck overlays only</li>
 * </ul>
 */
public final class RhythmModClientCommands {

    // ==================== Constants ====================

    private static final String COMMAND_ROOT = "rhythmmod";
    private static final String COMMAND_ROOT_SHORT = "rm";
    private static final String COMMAND_CANCEL = "cancel";
    private static final String COMMAND_CLEAR_OVERLAY = "clearoverlay";

    private static final String MSG_CANCELLED = "§a[RhythmMod] §fCancelled all downloads and cleared overlays.";
    private static final String MSG_CLEARED = "§a[RhythmMod] §fCleared all stuck overlays.";
    private static final String MSG_NO_ACTIVE = "§e[RhythmMod] §fNo active downloads or overlays to clear.";

    // ==================== Registration ====================

    private RhythmModClientCommands() {}

    /**
     * Registers all client commands.
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(RhythmModClientCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                          CommandBuildContext context) {
        // Main command: /rhythmmod
        dispatcher.register(
            ClientCommandManager.literal(COMMAND_ROOT)
                .then(ClientCommandManager.literal(COMMAND_CANCEL)
                    .executes(RhythmModClientCommands::executeCancelCommand))
                .then(ClientCommandManager.literal(COMMAND_CLEAR_OVERLAY)
                    .executes(RhythmModClientCommands::executeClearOverlayCommand))
        );

        // Short alias: /rm
        dispatcher.register(
            ClientCommandManager.literal(COMMAND_ROOT_SHORT)
                .then(ClientCommandManager.literal(COMMAND_CANCEL)
                    .executes(RhythmModClientCommands::executeCancelCommand))
                .then(ClientCommandManager.literal(COMMAND_CLEAR_OVERLAY)
                    .executes(RhythmModClientCommands::executeClearOverlayCommand))
        );
    }

    // ==================== Command Executors ====================

    /**
     * Cancels all active downloads and clears overlays.
     */
    private static int executeCancelCommand(CommandContext<FabricClientCommandSource> context) {
        boolean hadActiveProcesses = cancelAllDownloads();
        boolean hadActiveOverlays = clearAllOverlays();

        String message = (hadActiveProcesses || hadActiveOverlays) ? MSG_CANCELLED : MSG_NO_ACTIVE;
        context.getSource().sendFeedback(Component.literal(message));

        return 1;
    }

    /**
     * Only clears stuck overlays without cancelling processes.
     */
    private static int executeClearOverlayCommand(CommandContext<FabricClientCommandSource> context) {
        boolean hadActiveOverlays = clearAllOverlays();

        String message = hadActiveOverlays ? MSG_CLEARED : MSG_NO_ACTIVE;
        context.getSource().sendFeedback(Component.literal(message));

        return 1;
    }

    // ==================== Helper Methods ====================

    private static boolean cancelAllDownloads() {
        boolean hadProcesses = false;

        for (RhythmExecutable executable : RhythmExecutable.values()) {
            // Check if any processes are running before killing
            // killAllProcesses handles empty case gracefully
            executable.killAllProcesses();
            hadProcesses = true;
        }

        return hadProcesses;
    }

    private static boolean clearAllOverlays() {
        boolean hadOverlays = DownloadProgressOverlay.isActive() || LoadingOverlay.isActive();

        DownloadProgressOverlay.clearAll();
        LoadingOverlay.clearAll();

        return hadOverlays;
    }
}

