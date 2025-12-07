package com.rhythm.block;

import com.rhythm.audio.FrequencyChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Very Highs Rhythm Lamp - Pre-tuned to VERY_HIGHS frequency (8000-12000 Hz)
 * Color: Light Blue (#ADD8E6)
 */
public class VeryHighsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public VeryHighsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.VERY_HIGHS);
        this.setColor(FrequencyChannel.VERY_HIGHS.getDefaultColor());
    }
}

