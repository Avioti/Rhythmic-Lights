package com.rhythm.block;

import com.rhythm.audio.FrequencyChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Highs Rhythm Lamp - Pre-tuned to HIGHS frequency (4000-8000 Hz)
 * Color: Yellow (#FFFF00)
 */
public class HighsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public HighsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.HIGHS);
        this.setColor(FrequencyChannel.HIGHS.getDefaultColor());
    }
}

