package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Low Mids Rhythm Lamp - Pre-tuned to LOW_MIDS frequency (200-400 Hz)
 * Color: Blue (#0000FF)
 */
public class LowMidsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public LowMidsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.LOW_MIDS);
        this.setColor(FrequencyChannel.LOW_MIDS.getDefaultColor());
    }
}

