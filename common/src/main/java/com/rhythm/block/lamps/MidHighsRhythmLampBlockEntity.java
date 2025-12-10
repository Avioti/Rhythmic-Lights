package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mid Highs Rhythm Lamp - Pre-tuned to MID_HIGHS frequency (1000-2000 Hz)
 * Color: Pink (#FF1493)
 */
public class MidHighsRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public MidHighsRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.MID_HIGHS);
        this.setColor(FrequencyChannel.MID_HIGHS.getDefaultColor());
    }
}

