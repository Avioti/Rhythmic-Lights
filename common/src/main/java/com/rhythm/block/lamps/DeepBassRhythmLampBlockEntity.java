package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deep Bass Rhythm Lamp - Pre-tuned to DEEP_BASS frequency (60-100 Hz)
 * Color: Orange (#FF4400)
 */
public class DeepBassRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public DeepBassRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.DEEP_BASS);
        this.setColor(FrequencyChannel.DEEP_BASS.getDefaultColor());
    }
}

