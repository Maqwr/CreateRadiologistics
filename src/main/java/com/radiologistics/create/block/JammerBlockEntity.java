package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class JammerBlockEntity extends BaseModuleBlockEntity {
    public JammerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JAMMER.get(), pos, state);
    }
}
