package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ServoMotorBlock extends KineticBlock implements IBE<ServoMotorBlockEntity>, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape SHAPE = Shapes.block();

    public ServoMotorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isSteppingCarefully()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    public Class<ServoMotorBlockEntity> getBlockEntityClass() {
        return ServoMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ServoMotorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SERVO_MOTOR.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (WrenchHelper.isWrench(stack)) {
            return WrenchHelper.handleWrench(state, level, pos, player, stack);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BaseModuleBlock.rotateShape(SHAPE, state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player) {
            BlockPos computerPos = com.radiologistics.create.network.PlayerLinkManager.getPendingLink(player.getUUID());
            if (computerPos != null) {
                BlockEntity be = BaseModuleBlock.resolveBlockEntity(level, computerPos);
                if (be instanceof MainComputerBlockEntity computer) {
                    net.minecraft.world.phys.Vec3 p1 = MainComputerBlockEntity.getWorldPos(level, pos);
                    net.minecraft.world.phys.Vec3 p2 = MainComputerBlockEntity.getWorldPos(level, computerPos);
                    double distSq = p1.distanceToSqr(p2);
                    if (distSq > 100.0) {
                        player.displayClientMessage(Component.literal("too far").withStyle(ChatFormatting.RED), true);
                    } else {
                        boolean success = computer.linkModule("servo_motor", pos);
                        if (success) {
                            BlockEntity moduleBE = level.getBlockEntity(pos);
                            if (moduleBE instanceof ServoMotorBlockEntity module) {
                                module.setComputerPos(computerPos);
                                module.setChanged();
                            }
                            player.displayClientMessage(Component.literal("success").withStyle(ChatFormatting.GREEN), true);
                            com.radiologistics.create.network.PlayerLinkManager.setPendingLink(player, null);
                            return;
                        } else {
                            player.displayClientMessage(Component.literal("module already connected").withStyle(ChatFormatting.RED), true);
                        }
                    }
                } else {
                    player.displayClientMessage(Component.literal("no computer assigned").withStyle(ChatFormatting.RED), true);
                }
            } else {
                player.displayClientMessage(Component.literal("no computer assigned").withStyle(ChatFormatting.RED), true);
            }
            if (!player.isCreative()) {
                Block.popResource(level, pos, new ItemStack(this));
            }
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ServoMotorBlockEntity module) {
                BlockPos computerPos = module.getComputerPos();
                if (computerPos != null && !level.isClientSide()) {
                    if (!isMoving && !com.radiologistics.create.Radiologistics.isServerStopping) {
                        BlockEntity compBE = BaseModuleBlock.resolveBlockEntity(level, computerPos);
                        if (compBE instanceof MainComputerBlockEntity computer) {
                            computer.unlinkModule("servo_motor", pos);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
