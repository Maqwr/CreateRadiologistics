package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class RedstoneLinkModuleBlockEntity extends BaseModuleBlockEntity {
    public RedstoneLinkModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_LINK_MODULE.get(), pos, state);
    }
}
