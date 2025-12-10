package com.rhythm.block.lamps;

import com.rhythm.audio.FrequencyChannel;
import com.rhythm.block.bulbs.RhythmBulbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ultra Rhythm Lamp - Pre-tuned to ULTRA frequency (12000-16000 Hz)
 * Color: Magenta (#FF00FF)
 */
public class UltraRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public UltraRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setChannel(FrequencyChannel.ULTRA);
        this.setColor(FrequencyChannel.ULTRA.getDefaultColor());
    }
}

