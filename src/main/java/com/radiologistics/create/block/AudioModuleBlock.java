package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AudioModuleBlock extends BaseModuleBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0),
        Block.box(3.5, 4.0, 3.5, 12.5, 5.0, 12.5)
    );

    public AudioModuleBlock(Properties properties) {
        super(properties, "audio");
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AudioModuleBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BaseModuleBlock.rotateShape(SHAPE, state.getValue(FACING));
    }
}
