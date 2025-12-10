package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bass Rhythm Lamp - Pre-tuned to BASS frequency (100-200 Hz)
 * Color: Green (#00FF00)
 */
public class BassRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public BassRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.BASS);
        this.setColor(FrequencyChannel.BASS.getDefaultColor());
    }
}

