package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mid Lows Rhythm Lamp - Pre-tuned to MID_LOWS frequency (400-600 Hz)
 * Color: White (#FFFFFF)
 */
public class MidLowsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public MidLowsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.MID_LOWS);
        this.setColor(FrequencyChannel.MID_LOWS.getDefaultColor());
    }
}

