package com.rhythm.block;

import com.rhythm.audio.FrequencyChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Base class for pre-tuned rhythm lamps.
 * These are redstone lamp-style blocks that are automatically synced to specific frequency channels.
 * They extend RhythmBulbBlock but are locked to their designated frequency.
 * Can be powered by redstone OR synced to music.
 */
public class RhythmLampBlock extends RhythmBulbBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private final FrequencyChannel lockedChannel;

    public RhythmLampBlock(BlockBehaviour.Properties properties, FrequencyChannel channel) {
        super(properties);
        this.lockedChannel = channel;
        registerDefaultState(stateDefinition.any()
            .setValue(LIT, false)
            .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(POWERED, powered).setValue(LIT, false);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {

                level.setBlock(pos, state.setValue(POWERED, powered), 3);


                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof RhythmBulbBlockEntity bulb) {
                    bulb.setRedstonePowered(powered);
                }
            }
        }
    }

    /**
     * Get the frequency channel this lamp is locked to
     */
    public FrequencyChannel getLockedChannel() {
        return lockedChannel;
    }

    /**
     * Override to create the correct block entity type based on channel
     */
    @Override
    public RhythmBulbBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (lockedChannel) {
            case SUB_BASS -> new SubBassRhythmLampBlockEntity(pos, state);
            case DEEP_BASS -> new DeepBassRhythmLampBlockEntity(pos, state);
            case BASS -> new BassRhythmLampBlockEntity(pos, state);
            case LOW_MIDS -> new LowMidsRhythmLampBlockEntity(pos, state);
            case MID_LOWS -> new MidLowsRhythmLampBlockEntity(pos, state);
            case MIDS -> new MidsRhythmLampBlockEntity(pos, state);
            case MID_HIGHS -> new MidHighsRhythmLampBlockEntity(pos, state);
            case HIGH_MIDS -> new HighMidsRhythmLampBlockEntity(pos, state);
            case HIGHS -> new HighsRhythmLampBlockEntity(pos, state);
            case VERY_HIGHS -> new VeryHighsRhythmLampBlockEntity(pos, state);
            case ULTRA -> new UltraRhythmLampBlockEntity(pos, state);
            case TOP -> new TopRhythmLampBlockEntity(pos, state);
            default -> new RhythmBulbBlockEntity(pos, state);
        };
    }
}

