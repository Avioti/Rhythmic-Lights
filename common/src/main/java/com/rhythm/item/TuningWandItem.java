package com.rhythm.item;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import com.rhythm.block.controller.RhythmControllerBlockEntity;
import com.rhythm.client.gui.RGBText;
import com.rhythm.network.OpenDJControllerPacket;
import com.rhythm.network.PacketSender;
import com.rhythm.sync.VanillaLightSyncManager;
import com.rhythm.util.RhythmConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * Tuning Wand - Links bulbs, configures channels & colors, and acts as remote DJ control.
 */
public class TuningWandItem extends Item {

    // ==================== Constants ====================

    private static final String NBT_CONTROLLER_POS = "ControllerPos";
    private static final String COPPER_BULB_ID = "copper_bulb";

    private static final int PARTICLE_COUNT_CLAIM = 20;
    private static final int PARTICLE_COUNT_STORE = 10;
    private static final int PARTICLE_COUNT_LINK = 15;
    private static final int PARTICLE_COUNT_NOTE = 5;
    private static final int PARTICLE_COUNT_GLOW = 12;
    private static final int PARTICLE_COUNT_BULB = 8;
    private static final int PARTICLE_COUNT_UNLINK = 8;
    private static final int PARTICLE_LINE_STEPS = 8;
    private static final double PARTICLE_LINK_DISTANCE_SQ = 256;
    private static final double PARTICLE_SPREAD = 0.3;
    private static final double PARTICLE_SPEED = 0.05;

    private static final float GRADIENT_LINK_START = 0.25f;
    private static final float GRADIENT_LINK_END = 0.35f;

    // ==================== Tooltip Text ====================

    private static final String HEADER_ICON = "♪ ";
    private static final String HEADER_TEXT = "Rhythm Configuration Tool";
    private static final String ICON_LINKED = "✓ Linked: ";
    private static final String ICON_NOT_LINKED = "✗ Not linked";
    private static final String LABEL_ACTIONS = "Actions:";

    // ==================== Constructor ====================

    public TuningWandItem(Properties properties) {
        super(properties);
    }

    // ==================== Tooltip ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        addHeader(tooltip);
        addActionsList(tooltip);
        addStoredControllerInfo(tooltip, getStoredControllerPos(stack));
    }

    private void addHeader(List<Component> tooltip) {
        tooltip.add(RGBText.rainbow(HEADER_ICON + HEADER_TEXT));
        tooltip.add(Component.literal(""));
    }

    private void addActionsList(List<Component> tooltip) {
        tooltip.add(Component.literal(LABEL_ACTIONS).withStyle(ChatFormatting.GRAY));
        addActionLine(tooltip, "Shift+Click Station → ", "Store", RGBText::gold);
        addActionLine(tooltip, "Click Jukebox → ", "Link", text -> RGBText.gradient(text, GRADIENT_LINK_START, GRADIENT_LINK_END));
        addActionLine(tooltip, "Click Bulb → ", "Link", RGBText::aqua);
        addActionLine(tooltip, "Shift+Click Bulb → ", "Tune Channel", RGBText::purple);
        addActionLine(tooltip, "Click Air → ", "Remote DJ", RGBText::purple);
    }

    private void addActionLine(List<Component> tooltip, String prefix, String action,
                                java.util.function.Function<String, Component> colorizer) {
        tooltip.add(Component.literal("  " + prefix)
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(colorizer.apply(action)));
    }

    private void addStoredControllerInfo(List<Component> tooltip, BlockPos storedPos) {
        tooltip.add(Component.literal(""));
        if (storedPos != null) {
            tooltip.add(Component.literal(ICON_LINKED)
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(storedPos.toShortString()).withStyle(ChatFormatting.WHITE)));
        } else {
            tooltip.add(Component.literal(ICON_NOT_LINKED).withStyle(ChatFormatting.RED));
        }
    }

    // ==================== Use In Air ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown() && tryOpenRemoteDJController(level, player, stack)) {
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    // ==================== Use On Block ====================

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState blockState = level.getBlockState(pos);

        // Shift+Click Controller: Store position & claim ownership
        if (player.isShiftKeyDown() && blockEntity instanceof RhythmControllerBlockEntity controller) {
            return handleControllerShiftClick(level, pos, player, stack, controller);
        }

        // Click Jukebox: Link to stored controller
        if (!player.isShiftKeyDown() && blockEntity instanceof JukeboxBlockEntity) {
            return handleJukeboxClick(level, pos, player, stack);
        }

        // Check if this is a vanilla controllable light
        VanillaLightInfo lightInfo = getVanillaLightInfo(blockState, blockEntity);

        // Shift+Click Vanilla Light: Cycle channel
        if (player.isShiftKeyDown() && lightInfo.isControllable()) {
            return handleVanillaLightShiftClick(level, pos, player, blockState);
        }

        // Click Vanilla Light: Link to controller
        if (!player.isShiftKeyDown() && lightInfo.isControllable()) {
            return handleVanillaLightClick(level, pos, player, stack, blockState);
        }

        // Warn if light block can't be controlled
        if (!player.isShiftKeyDown() && lightInfo.isLightButNotControllable()) {
            return handleUncontrollableLightClick(level, player, blockState);
        }

        // Ctrl+Click Vanilla Light: Unlink
        if (player.isCrouching() && !player.isShiftKeyDown() && lightInfo.isControllable()) {
            return handleVanillaLightUnlink(level, pos, player);
        }

        // Ctrl+Click Bulb: Unlink
        if (player.isCrouching() && !player.isShiftKeyDown() && blockEntity instanceof RhythmBulbBlockEntity bulb) {
            return handleBulbUnlink(level, pos, player, bulb);
        }

        // Shift+Click Bulb: Cycle channel
        if (player.isShiftKeyDown() && blockEntity instanceof RhythmBulbBlockEntity bulb) {
            return handleBulbShiftClick(level, pos, player, bulb);
        }

        // Click Bulb: Link to controller
        if (!player.isShiftKeyDown() && blockEntity instanceof RhythmBulbBlockEntity bulb) {
            return handleBulbClick(level, pos, player, stack, bulb);
        }

        // Fallback: Try to open remote DJ
        if (!player.isShiftKeyDown() && !player.isCrouching() && tryOpenRemoteDJController(level, player, stack)) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ==================== Vanilla Light Detection ====================

    private record VanillaLightInfo(boolean hasLightEmission, boolean hasLitProperty, boolean isSpecialBlock) {
        boolean isControllable() {
            return (hasLightEmission || isSpecialBlock) && hasLitProperty && !isSpecialBlock;
        }

        boolean isLightButNotControllable() {
            return hasLightEmission && !hasLitProperty && !isSpecialBlock;
        }
    }

    private VanillaLightInfo getVanillaLightInfo(BlockState state, BlockEntity blockEntity) {
        Block block = state.getBlock();
        int lightLevel = state.getLightEmission();

        boolean isRedstoneLamp = block instanceof RedstoneLampBlock;
        boolean isCampfire = block instanceof CampfireBlock;
        boolean isCandle = block instanceof CandleBlock || block instanceof CandleCakeBlock;
        boolean isCopperBulb = block.getDescriptionId().contains(COPPER_BULB_ID);

        boolean hasLightEmission = lightLevel > 0 || isRedstoneLamp || isCampfire || isCandle || isCopperBulb;
        boolean hasLitProperty = state.hasProperty(BlockStateProperties.LIT);
        boolean isSpecialBlock = blockEntity instanceof RhythmBulbBlockEntity ||
                                 blockEntity instanceof RhythmControllerBlockEntity ||
                                 blockEntity instanceof JukeboxBlockEntity;

        return new VanillaLightInfo(hasLightEmission, hasLitProperty, isSpecialBlock);
    }

    // ==================== Controller Handling ====================

    private InteractionResult handleControllerShiftClick(Level level, BlockPos pos, Player player,
                                                          ItemStack stack, RhythmControllerBlockEntity controller) {
        if (controller.hasOwner() && !controller.isOwner(player.getUUID())) {
            sendOwnershipError(level, player, controller.getOwnerName());
            return InteractionResult.FAIL;
        }

        boolean justClaimed = claimControllerIfUnowned(level, pos, player, controller);
        storeControllerPos(stack, pos);
        sendControllerStoredFeedback(level, pos, player, controller, justClaimed);

        return InteractionResult.SUCCESS;
    }

    private boolean claimControllerIfUnowned(Level level, BlockPos pos, Player player,
                                              RhythmControllerBlockEntity controller) {
        if (!controller.hasOwner() && !level.isClientSide) {
            controller.setOwner(player.getUUID(), player.getName().getString());
            if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
                RhythmConstants.LOGGER.debug("DJ Station at {} claimed by {}", pos, player.getName().getString());
            }
            return true;
        }
        return false;
    }

    private void sendControllerStoredFeedback(Level level, BlockPos pos, Player player,
                                               RhythmControllerBlockEntity controller, boolean justClaimed) {
        if (level.isClientSide) {
            String msg = !controller.hasOwner()
                ? "✓ DJ Station claimed & stored! "
                : "✓ DJ Station stored! ";
            ChatFormatting color = !controller.hasOwner() ? ChatFormatting.GOLD : ChatFormatting.GREEN;

            player.displayClientMessage(
                Component.literal(msg).withStyle(color)
                    .append(Component.literal("Now right-click a jukebox.").withStyle(ChatFormatting.GRAY)),
                true
            );
        } else {
            spawnParticles((ServerLevel) level, pos, ParticleTypes.HAPPY_VILLAGER, PARTICLE_COUNT_STORE);
            if (justClaimed) {
                spawnParticles((ServerLevel) level, pos, ParticleTypes.TOTEM_OF_UNDYING, PARTICLE_COUNT_CLAIM);
            }
            if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
                RhythmConstants.LOGGER.debug("DJ Station stored at {}", pos);
            }
        }
    }

    // ==================== Jukebox Handling ====================

    private InteractionResult handleJukeboxClick(Level level, BlockPos pos, Player player, ItemStack stack) {
        BlockPos controllerPos = getStoredControllerPos(stack);
        if (controllerPos == null) {
            sendNoControllerStoredError(level, player);
            return InteractionResult.FAIL;
        }

        RhythmControllerBlockEntity controller = getAndValidateController(level, controllerPos, player);
        if (controller == null) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            controller.setJukeboxPos(pos);
            spawnParticles((ServerLevel) level, pos, ParticleTypes.NOTE, PARTICLE_COUNT_LINK);
            if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
                RhythmConstants.LOGGER.debug("Jukebox at {} linked to controller at {}", pos, controllerPos);
            }
        } else {
            player.displayClientMessage(
                Component.literal("✓ Jukebox linked! ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("(" + pos.toShortString() + " → " + controllerPos.toShortString() + ")")
                        .withStyle(ChatFormatting.GRAY)),
                true
            );
        }

        return InteractionResult.SUCCESS;
    }

    // ==================== Vanilla Light Handling ====================

    private InteractionResult handleVanillaLightShiftClick(Level level, BlockPos pos, Player player, BlockState blockState) {
        var syncData = VanillaLightSyncManager.getInstance().getSyncData(level, pos);

        if (syncData == null) {
            sendNotSyncedError(level, player);
            return InteractionResult.FAIL;
        }

        FrequencyChannel currentChannel = syncData.getChannel();
        FrequencyChannel nextChannel = currentChannel.cycle();
        int nextColor = nextChannel.getDefaultColor();

        if (level.isClientSide) {
            VanillaLightSyncManager.getInstance().updateSyncData(level, pos, nextChannel, nextColor);
            sendChannelChangeFeedback(player, nextChannel);
        } else {
            spawnParticles((ServerLevel) level, pos, ParticleTypes.NOTE, PARTICLE_COUNT_NOTE);
            if (RhythmConstants.DEBUG_LIGHTS) {
                RhythmConstants.LOGGER.debug("Vanilla light at {} channel changed: {} → {}", pos, currentChannel, nextChannel);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleVanillaLightClick(Level level, BlockPos pos, Player player,
                                                       ItemStack stack, BlockState blockState) {
        BlockPos controllerPos = getStoredControllerPos(stack);
        if (controllerPos == null) {
            sendNoControllerStoredError(level, player);
            return InteractionResult.FAIL;
        }

        RhythmControllerBlockEntity controller = getAndValidateController(level, controllerPos, player);
        if (controller == null) {
            return InteractionResult.FAIL;
        }

        boolean alreadySynced = VanillaLightSyncManager.getInstance().isSynced(level, pos);
        FrequencyChannel defaultChannel = FrequencyChannel.BASS;
        int defaultColor = defaultChannel.getDefaultColor();

        if (level.isClientSide) {
            VanillaLightSyncManager.getInstance().registerLight(level, pos, controllerPos, defaultChannel, defaultColor);
            sendVanillaLightLinkedFeedback(player, blockState, alreadySynced);
        } else {
            spawnParticles((ServerLevel) level, pos, ParticleTypes.GLOW, PARTICLE_COUNT_GLOW);
            if (RhythmConstants.DEBUG_LIGHTS) {
                RhythmConstants.LOGGER.debug("Vanilla light at {} linked to controller at {}", pos, controllerPos);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleUncontrollableLightClick(Level level, Player player, BlockState blockState) {
        if (level.isClientSide) {
            String blockName = blockState.getBlock().getName().getString();
            player.displayClientMessage(
                Component.literal("✗ Cannot Link This Block!")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("\n" + blockName + " cannot be controlled").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n\nThis block has no ON/OFF state.").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n\n✓ Use instead:").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("\n  • Redstone Lamps\n  • Campfires\n  • Candles").withStyle(ChatFormatting.WHITE)),
                true
            );
        }
        return InteractionResult.FAIL;
    }

    private InteractionResult handleVanillaLightUnlink(Level level, BlockPos pos, Player player) {
        var syncData = VanillaLightSyncManager.getInstance().getSyncData(level, pos);

        if (syncData == null) {
            sendNotSyncedError(level, player);
            return InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            VanillaLightSyncManager.getInstance().unregisterLight(level, pos);
            player.displayClientMessage(
                Component.literal("✗ Vanilla light unlinked!").withStyle(ChatFormatting.YELLOW),
                true
            );
        } else {
            spawnParticles((ServerLevel) level, pos, ParticleTypes.SMOKE, PARTICLE_COUNT_UNLINK);
            if (RhythmConstants.DEBUG_LIGHTS) {
                RhythmConstants.LOGGER.debug("Vanilla light at {} unlinked", pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ==================== Bulb Handling ====================

    private InteractionResult handleBulbUnlink(Level level, BlockPos pos, Player player, RhythmBulbBlockEntity bulb) {
        BlockPos linkedController = bulb.getControllerPos();

        if (!level.isClientSide) {
            if (linkedController != null) {
                bulb.setControllerPos(null);
                spawnParticles((ServerLevel) level, pos, ParticleTypes.SMOKE, PARTICLE_COUNT_UNLINK);
                if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
                    RhythmConstants.LOGGER.debug("Bulb at {} unlinked from controller at {}", pos, linkedController);
                }
            }
        } else {
            String msg = linkedController != null ? "✗ Bulb unlinked from controller!" : "✗ Bulb has no controller linked!";
            ChatFormatting color = linkedController != null ? ChatFormatting.YELLOW : ChatFormatting.RED;
            player.displayClientMessage(Component.literal(msg).withStyle(color), true);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleBulbShiftClick(Level level, BlockPos pos, Player player, RhythmBulbBlockEntity bulb) {
        if (!level.isClientSide) {
            FrequencyChannel currentChannel = bulb.getChannel();
            FrequencyChannel nextChannel = currentChannel.cycle();
            bulb.setChannel(nextChannel);
            bulb.setColor(nextChannel.getDefaultColor());

            spawnParticles((ServerLevel) level, pos, ParticleTypes.NOTE, PARTICLE_COUNT_NOTE);
            if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
                RhythmConstants.LOGGER.debug("Bulb at {} channel changed: {} → {}", pos, currentChannel, nextChannel);
            }
        } else {
            FrequencyChannel nextChannel = bulb.getChannel().cycle();
            sendChannelChangeFeedback(player, nextChannel);
        }

        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleBulbClick(Level level, BlockPos pos, Player player,
                                               ItemStack stack, RhythmBulbBlockEntity bulb) {
        BlockPos controllerPos = getStoredControllerPos(stack);
        if (controllerPos == null) {
            sendNoControllerStoredError(level, player);
            return InteractionResult.FAIL;
        }

        RhythmControllerBlockEntity controller = getAndValidateController(level, controllerPos, player);
        if (controller == null) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            bulb.setControllerPos(controllerPos);
            spawnParticles((ServerLevel) level, pos, ParticleTypes.END_ROD, PARTICLE_COUNT_BULB);
        } else {
            player.displayClientMessage(
                Component.literal("✓ Bulb linked! ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("(" + controllerPos.toShortString() + ")").withStyle(ChatFormatting.GRAY)),
                true
            );
        }

        return InteractionResult.SUCCESS;
    }

    // ==================== Remote DJ Controller ====================

    private boolean tryOpenRemoteDJController(Level level, Player player, ItemStack stack) {
        BlockPos controllerPos = getStoredControllerPos(stack);

        if (controllerPos == null) {
            sendNoControllerStoredError(level, player);
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (!(blockEntity instanceof RhythmControllerBlockEntity controller)) {
            sendControllerNotFoundError(level, player);
            return false;
        }

        if (!controller.isOwner(player.getUUID())) {
            sendOwnershipError(level, player, controller.getOwnerName());
            return false;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openDJControllerRemotely(level, serverPlayer, controller, controllerPos);
        } else if (level.isClientSide) {
            player.displayClientMessage(
                Component.literal("🎛 Opening DJ Controller remotely...").withStyle(ChatFormatting.LIGHT_PURPLE),
                true
            );
        }

        return true;
    }

    private void openDJControllerRemotely(Level level, ServerPlayer player,
                                           RhythmControllerBlockEntity controller, BlockPos controllerPos) {
        BlockPos jukeboxPos = controller.getJukeboxPos();
        OpenDJControllerPacket packet = new OpenDJControllerPacket(controllerPos, jukeboxPos, 1.0f, 0.0f, new float[12], false);
        PacketSender.getInstance().sendToPlayer(player, packet);

        if (level instanceof ServerLevel serverLevel) {
            spawnParticles(serverLevel, controllerPos, ParticleTypes.NOTE, PARTICLE_COUNT_BULB);
        }

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("Remote DJ Controller opened for {} at {}", player.getName().getString(), controllerPos);
        }
    }

    // ==================== Error Messages ====================

    private void sendNoControllerStoredError(Level level, Player player) {
        if (level.isClientSide) {
            player.displayClientMessage(
                Component.literal("✗ No DJ Station stored!")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("\nShift+Right-Click a station first.").withStyle(ChatFormatting.GRAY)),
                true
            );
        }
    }

    private void sendControllerNotFoundError(Level level, Player player) {
        if (level.isClientSide) {
            player.displayClientMessage(
                Component.literal("✗ DJ Station not found!")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("\nIt may have been destroyed.").withStyle(ChatFormatting.GRAY)),
                true
            );
        }
    }

    private void sendOwnershipError(Level level, Player player, String ownerName) {
        if (level.isClientSide) {
            player.displayClientMessage(
                Component.literal("✗ You don't own this DJ Station!")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("\nOwner: " + (ownerName != null ? ownerName : "Unknown"))
                        .withStyle(ChatFormatting.GOLD)),
                true
            );
        }
    }

    private void sendNotSyncedError(Level level, Player player) {
        if (level.isClientSide) {
            player.displayClientMessage(
                Component.literal("✗ This vanilla light is not synced yet!")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("\nRight-click (without shift) to link it first.").withStyle(ChatFormatting.GRAY)),
                true
            );
        }
    }

    // ==================== Feedback Messages ====================

    private void sendChannelChangeFeedback(Player player, FrequencyChannel channel) {
        player.displayClientMessage(
            Component.literal("♪ Channel: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(channel.getDisplayName()).withStyle(ChatFormatting.YELLOW)),
            true
        );
    }

    private void sendVanillaLightLinkedFeedback(Player player, BlockState blockState, boolean wasAlreadySynced) {
        String blockName = blockState.getBlock().getName().getString();
        int lightLevel = blockState.getLightEmission();

        player.displayClientMessage(
            Component.literal(wasAlreadySynced ? "✓ Vanilla Light Re-linked! " : "✓ Vanilla Light Linked! ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("\n" + blockName + " (Light Level " + lightLevel + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nShift+Right-Click to change channel!").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nChannel: BASS").withStyle(ChatFormatting.YELLOW)),
            true
        );
    }

    // ==================== Validation Helpers ====================

    private RhythmControllerBlockEntity getAndValidateController(Level level, BlockPos controllerPos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);

        if (!(blockEntity instanceof RhythmControllerBlockEntity controller)) {
            sendControllerNotFoundError(level, player);
            return null;
        }

        if (!controller.isOwner(player.getUUID())) {
            sendOwnershipError(level, player, controller.getOwnerName());
            return null;
        }

        return controller;
    }

    // ==================== Inventory Tick ====================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slot, isSelected);

        if (!level.isClientSide || !isSelected || !(entity instanceof Player player)) {
            return;
        }

        BlockPos controllerPos = getStoredControllerPos(stack);
        if (controllerPos != null && level.getGameTime() % 20 == 0) {
            trySpawnLinkParticles(level, player, controllerPos);
        }
    }

    private void trySpawnLinkParticles(Level level, Player player, BlockPos controllerPos) {
        double cx = controllerPos.getX() + 0.5;
        double cy = controllerPos.getY() + 0.5;
        double cz = controllerPos.getZ() + 0.5;

        if (player.position().distanceToSqr(cx, cy, cz) < PARTICLE_LINK_DISTANCE_SQ) {
            spawnParticleLine(level, player.getX(), player.getY() + 1.0, player.getZ(), cx, cy, cz);
        }
    }

    // ==================== Particle Helpers ====================

    private void spawnParticleLine(Level level, double x1, double y1, double z1,
                                   double x2, double y2, double z2) {
        for (int i = 0; i < PARTICLE_LINE_STEPS; i++) {
            double t = i / (double) PARTICLE_LINE_STEPS;
            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            double z = z1 + (z2 - z1) * t;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
        }
    }

    private void spawnParticles(ServerLevel level, BlockPos pos, ParticleOptions particle, int count) {
        level.sendParticles(
            particle,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            count, PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPEED
        );
    }

    // ==================== NBT Helpers ====================

    private void storeControllerPos(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(NBT_CONTROLLER_POS, pos.asLong()));
    }

    private BlockPos getStoredControllerPos(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains(NBT_CONTROLLER_POS)) {
            return BlockPos.of(customData.copyTag().getLong(NBT_CONTROLLER_POS));
        }
        return null;
    }
}

