package com.rhythm.block;

import com.rhythm.audio.FrequencyChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sub-Bass Rhythm Lamp - Pre-tuned to SUB_BASS frequency (20-60 Hz)
 * Color: Deep Red (#FF0000)
 */
public class SubBassRhythmLampBlockEntity extends RhythmBulbBlockEntity {

    public SubBassRhythmLampBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        // Pre-configure for SUB_BASS channel
        // Note: setChannel() will automatically set the color, but we set it explicitly
        // to ensure it's correct even if the default color changes
        this.setChannel(FrequencyChannel.SUB_BASS);
        this.setColor(FrequencyChannel.SUB_BASS.getDefaultColor());
    }
}

