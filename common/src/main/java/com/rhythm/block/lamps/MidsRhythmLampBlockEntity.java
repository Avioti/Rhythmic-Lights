package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mids Rhythm Lamp - Pre-tuned to MIDS frequency (600-1000 Hz)
 * Color: Purple (#8B00FF)
 */
public class MidsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public MidsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.MIDS);
        this.setColor(FrequencyChannel.MIDS.getDefaultColor());
    }
}

