package com.rhythm.block.bulbs;

import com.rhythm.RhythmMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Light block that reacts to music by changing brightness.
 * <p>
 * Features:
 * <ul>
 *   <li>Dynamic light levels based on beat detection</li>
 *   <li>Custom rendering with colored light overlay</li>
 *   <li>Client-side ticking for particle synchronization</li>
 * </ul>
 */
public class RhythmBulbBlock extends Block implements EntityBlock {

    // ==================== Block State Properties ====================

    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    // ==================== Constructor ====================

    public RhythmBulbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    // ==================== Block State ====================

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (isNewPlacement(oldState)) {
            initializeBlockEntity(level, pos);
        }
    }

    private boolean isNewPlacement(BlockState oldState) {
        return !oldState.is(this);
    }

    private void initializeBlockEntity(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RhythmBulbBlockEntity bulb && level.isClientSide) {
            bulb.registerColoredLight();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ==================== Block Entity ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RhythmBulbBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return null;
        }
        return type == RhythmMod.RHYTHM_BULB_BLOCK_ENTITY.get()
            ? (BlockEntityTicker<T>) (BlockEntityTicker<RhythmBulbBlockEntity>) RhythmBulbBlockEntity::clientTick
            : null;
    }

    // ==================== Rendering ====================

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

