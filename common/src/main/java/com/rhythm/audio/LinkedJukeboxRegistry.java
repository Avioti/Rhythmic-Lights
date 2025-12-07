package com.rhythm.audio;

import com.rhythm.RhythmMod;
import com.rhythm.block.RhythmControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry to track which jukeboxes are linked to RhythmMod controllers.
 *
 * <p>Only jukeboxes that are linked to a controller should have RhythmMod's
 * special playback control features. Unlinked jukeboxes behave as vanilla.</p>
 *
 * <p>This is a server-side registry that tracks all controller→jukebox links.</p>
 */
public class LinkedJukeboxRegistry {

    private static final LinkedJukeboxRegistry INSTANCE = new LinkedJukeboxRegistry();
    private static final String LOG_PREFIX = "[RhythmMod Registry] ";

    private final Map<BlockPos, BlockPos> jukeboxToController = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockPos> controllerToJukebox = new ConcurrentHashMap<>();

    private LinkedJukeboxRegistry() {}

    /**
     * Gets the singleton instance of the registry.
     *
     * @return the registry instance
     */
    public static LinkedJukeboxRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a link between a controller and a jukebox.
     *
     * @param controllerPos the controller block position
     * @param jukeboxPos    the jukebox block position
     */
    public void linkJukeboxToController(BlockPos controllerPos, BlockPos jukeboxPos) {
        unlinkExistingConnections(controllerPos, jukeboxPos);

        jukeboxToController.put(jukeboxPos, controllerPos);
        controllerToJukebox.put(controllerPos, jukeboxPos);

        RhythmMod.LOGGER.info(LOG_PREFIX + "Linked jukebox at {} to controller at {}",
            jukeboxPos, controllerPos);
    }

    private void unlinkExistingConnections(BlockPos controllerPos, BlockPos jukeboxPos) {
        BlockPos oldJukebox = controllerToJukebox.get(controllerPos);
        if (oldJukebox != null) {
            jukeboxToController.remove(oldJukebox);
        }

        BlockPos oldController = jukeboxToController.get(jukeboxPos);
        if (oldController != null) {
            controllerToJukebox.remove(oldController);
        }
    }

    /**
     * Unlinks a jukebox from its controller.
     *
     * @param jukeboxPos the jukebox position to unlink
     */
    public void unlinkJukebox(BlockPos jukeboxPos) {
        BlockPos controllerPos = jukeboxToController.remove(jukeboxPos);
        if (controllerPos != null) {
            controllerToJukebox.remove(controllerPos);
            RhythmMod.LOGGER.info(LOG_PREFIX + "Unlinked jukebox at {}", jukeboxPos);
        }
    }

    /**
     * Unlinks a controller's jukebox.
     *
     * @param controllerPos the controller position
     */
    public void unlinkController(BlockPos controllerPos) {
        BlockPos jukeboxPos = controllerToJukebox.remove(controllerPos);
        if (jukeboxPos != null) {
            jukeboxToController.remove(jukeboxPos);
            RhythmMod.LOGGER.info(LOG_PREFIX + "Controller at {} removed, unlinked jukebox at {}",
                controllerPos, jukeboxPos);
        }
    }

    /**
     * Checks if a jukebox is linked to any RhythmMod controller.
     *
     * @param jukeboxPos the jukebox position to check
     * @return true if this jukebox is controlled by RhythmMod
     */
    public boolean isJukeboxLinked(BlockPos jukeboxPos) {
        return jukeboxToController.containsKey(jukeboxPos);
    }

    /**
     * Gets the controller position for a linked jukebox.
     *
     * @param jukeboxPos the jukebox position
     * @return the controller position, or null if not linked
     */
    public BlockPos getControllerForJukebox(BlockPos jukeboxPos) {
        return jukeboxToController.get(jukeboxPos);
    }

    /**
     * Gets the jukebox position for a controller.
     *
     * @param controllerPos the controller position
     * @return the jukebox position, or null if none linked
     */
    public BlockPos getJukeboxForController(BlockPos controllerPos) {
        return controllerToJukebox.get(controllerPos);
    }

    /**
     * Restores links from a controller block entity on level load.
     *
     * @param level         the level to scan
     * @param controllerPos a known controller position
     */
    public void restoreLinkFromController(Level level, BlockPos controllerPos) {
        BlockEntity be = level.getBlockEntity(controllerPos);
        if (be instanceof RhythmControllerBlockEntity controller) {
            BlockPos jukeboxPos = controller.getJukeboxPos();
            if (jukeboxPos != null) {
                linkJukeboxToController(controllerPos, jukeboxPos);
            }
        }
    }

    /**
     * Clears all registry data. Called on server shutdown.
     */
    public void clearAll() {
        jukeboxToController.clear();
        controllerToJukebox.clear();
        RhythmMod.LOGGER.info(LOG_PREFIX + "Cleared all jukebox links");
    }

    /**
     * Gets all linked jukebox positions.
     *
     * @return set of all jukebox positions that are linked to controllers
     */
    public Set<BlockPos> getAllLinkedJukeboxes() {
        return jukeboxToController.keySet();
    }
}

