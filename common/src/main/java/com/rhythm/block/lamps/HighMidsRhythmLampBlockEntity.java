package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High Mids Rhythm Lamp - Pre-tuned to HIGH_MIDS frequency (2000-4000 Hz)
 * Color: Cyan (#00FFFF)
 */
public class HighMidsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public HighMidsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.HIGH_MIDS);
        this.setColor(FrequencyChannel.HIGH_MIDS.getDefaultColor());
    }
}

