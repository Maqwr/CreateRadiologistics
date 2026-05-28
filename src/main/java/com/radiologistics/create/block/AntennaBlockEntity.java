package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class AntennaBlockEntity extends BaseModuleBlockEntity {
    public AntennaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANTENNA.get(), pos, state);
    }

    /**
     * Calculates the height of the vertical antenna stack by finding the bottom-most
     * segment and counting upwards to the top.
     */
    public int getAntennaHeight() {
        if (level == null) return 1;
        
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos().set(worldPosition);
        while (level.getBlockState(cursor.below()).getBlock() instanceof AntennaBlock) {
            cursor.move(0, -1, 0);
        }
        
        int height = 1;
        cursor.move(0, 1, 0);
        while (level.getBlockState(cursor).getBlock() instanceof AntennaBlock) {
            height++;
            cursor.move(0, 1, 0);
        }
        return height;
    }
}
