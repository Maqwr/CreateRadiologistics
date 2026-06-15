package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import com.radiologistics.create.network.OpenTransmitterScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;

public class RadioTransmitterBlock extends Block implements EntityBlock, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1.0, 0.0, 1.0, 15.0, 6.0, 15.0),
        Block.box(3.0, 5.0, 3.0, 13.0, 9.0, 13.0)
    );
    public RadioTransmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.UP));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioTransmitterBlockEntity(pos, state);
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
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (WrenchHelper.isWrench(stack)) {
            return WrenchHelper.handleWrench(state, level, pos, player, stack);
        }
        if (stack.is(com.radiologistics.create.registry.ModItems.ANTENNA.get())) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                com.radiologistics.create.network.PlayerLinkManager.setPendingLink(serverPlayer, pos);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(com.radiologistics.create.registry.ModItems.ANTENNA.get()) ||
            player.getItemInHand(InteractionHand.OFF_HAND).is(com.radiologistics.create.registry.ModItems.ANTENNA.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RadioTransmitterBlockEntity transmitter) {
                // Send packet to open GUI configuration
                int range = Math.min(3000, 100 + transmitter.getAttachedAntennaHeight() * 150);
                PacketDistributor.sendToPlayer(serverPlayer, new OpenTransmitterScreenPacket(pos, transmitter.getChannel(), transmitter.getMessage(), range));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RadioTransmitterBlockEntity transmitter) {
                boolean hasSignal = level.hasNeighborSignal(pos);
                transmitter.updateRedstone(hasSignal);
            }
        }
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof RadioTransmitterBlockEntity transmitter) {
                    transmitter.clientTick(lvl, pos, st);
                }
            };
        }
        return null;
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BaseModuleBlock.rotateShape(SHAPE, state.getValue(FACING));
    }
}
