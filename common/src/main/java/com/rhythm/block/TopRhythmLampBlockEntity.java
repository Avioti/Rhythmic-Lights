package com.rhythm.block;

import com.rhythm.audio.FrequencyChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Top Rhythm Lamp - Pre-tuned to TOP frequency (16000-20000 Hz)
 * Color: Lime (#00FF7F)
 */
public class TopRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public TopRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.TOP);
        this.setColor(FrequencyChannel.TOP.getDefaultColor());
    }
}

