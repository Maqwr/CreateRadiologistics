package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class AudioModuleBlockEntity extends BaseModuleBlockEntity {
    public AudioModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUDIO_MODULE.get(), pos, state);
    }
}
