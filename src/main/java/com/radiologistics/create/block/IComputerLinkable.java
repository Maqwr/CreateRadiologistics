package com.radiologistics.create.block;

import net.minecraft.core.BlockPos;

public interface IComputerLinkable {
    BlockPos getComputerPos();
    void setComputerPos(BlockPos pos);
    void setChanged();
}
