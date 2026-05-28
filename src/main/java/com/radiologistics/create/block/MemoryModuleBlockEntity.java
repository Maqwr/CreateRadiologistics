package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MemoryModuleBlockEntity extends BaseModuleBlockEntity {
    public MemoryModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MEMORY_MODULE.get(), pos, state);
    }
}
