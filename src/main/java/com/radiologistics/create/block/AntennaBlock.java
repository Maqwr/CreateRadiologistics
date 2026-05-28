package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class AntennaBlock extends BaseModuleBlock {
    private static final VoxelShape BASE_SHAPE = Shapes.or(
        Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0),
        Block.box(1.0, 4.0, 7.0, 15.0, 16.0, 9.0)
    );
    private static final VoxelShape SEGMENT_SHAPE = Block.box(1.0, 0.0, 7.0, 15.0, 16.0, 9.0);
    public static final EnumProperty<AntennaSegmentType> TYPE = EnumProperty.create("type", AntennaSegmentType.class);

    public AntennaBlock(Properties properties) {
        super(properties, "antenna");
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, AntennaSegmentType.BASE));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AntennaBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateSegmentType(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            BlockState newState = updateSegmentType(level, pos, state);
            if (newState != state) {
                level.setBlock(pos, newState, 3);
            }
        }
    }

    private BlockState updateSegmentType(Level level, BlockPos pos, BlockState state) {
        boolean hasBelow = level.getBlockState(pos.below()).getBlock() instanceof AntennaBlock;
        boolean hasAbove = level.getBlockState(pos.above()).getBlock() instanceof AntennaBlock;
        
        AntennaSegmentType type;
        if (!hasBelow) {
            type = AntennaSegmentType.BASE;
        } else if (hasAbove) {
            type = AntennaSegmentType.MIDDLE;
        } else {
            type = AntennaSegmentType.TOP;
        }
        return state.setValue(TYPE, type);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide()) {
            boolean stackedOnAntenna = level.getBlockState(pos.below()).getBlock() instanceof AntennaBlock;
            if (stackedOnAntenna) {
                BlockPos targetPos = null;
                BlockEntity belowBE = level.getBlockEntity(pos.below());
                if (belowBE instanceof AntennaBlockEntity belowAntenna) {
                    targetPos = belowAntenna.getComputerPos();
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AntennaBlockEntity thisAntenna) {
                    thisAntenna.setComputerPos(targetPos);
                    thisAntenna.setChanged();
                }
                return;
            }

            if (placer instanceof net.minecraft.server.level.ServerPlayer player) {
                BlockPos targetPos = com.radiologistics.create.network.PlayerLinkManager.getPendingLink(player.getUUID());
                if (targetPos != null) {
                    double distSq = pos.distSqr(targetPos);
                    if (distSq > 100.0) {
                        player.displayClientMessage(Component.literal("too far").withStyle(ChatFormatting.RED), true);
                    } else {
                        BlockEntity targetBE = level.getBlockEntity(targetPos);
                        if (targetBE instanceof MainComputerBlockEntity computer) {
                            boolean success = computer.linkModule("antenna", pos);
                            if (success) {
                                BlockEntity be = level.getBlockEntity(pos);
                                if (be instanceof AntennaBlockEntity thisAntenna) {
                                    thisAntenna.setComputerPos(targetPos);
                                    thisAntenna.setChanged();
                                }
                                player.displayClientMessage(Component.literal("success").withStyle(ChatFormatting.GREEN), true);
                                com.radiologistics.create.network.PlayerLinkManager.setPendingLink(player, null);
                                return;
                            } else {
                                player.displayClientMessage(Component.literal("module already connected").withStyle(ChatFormatting.RED), true);
                            }
                        } else if (targetBE instanceof RadioTransmitterBlockEntity transmitter) {
                            transmitter.setLinkedAntennaPos(pos);
                            BlockEntity be = level.getBlockEntity(pos);
                            if (be instanceof AntennaBlockEntity thisAntenna) {
                                thisAntenna.setComputerPos(targetPos);
                                thisAntenna.setChanged();
                            }
                            player.displayClientMessage(Component.literal("success").withStyle(ChatFormatting.GREEN), true);
                            com.radiologistics.create.network.PlayerLinkManager.setPendingLink(player, null);
                            return;
                        } else {
                            player.displayClientMessage(Component.literal("no transmitter or computer assigned").withStyle(ChatFormatting.RED), true);
                        }
                    }
                } else {
                    player.displayClientMessage(Component.literal("no transmitter or computer assigned").withStyle(ChatFormatting.RED), true);
                }
            }
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AntennaBlockEntity antenna) {
                BlockPos targetPos = antenna.getComputerPos();
                if (targetPos != null && !level.isClientSide()) {
                    BlockEntity targetBE = level.getBlockEntity(targetPos);
                    if (targetBE instanceof RadioTransmitterBlockEntity transmitter) {
                        if (pos.equals(transmitter.getLinkedAntennaPos())) {
                            transmitter.setLinkedAntennaPos(null);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TYPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(TYPE) == AntennaSegmentType.BASE) {
            return BASE_SHAPE;
        }
        return SEGMENT_SHAPE;
    }

    public enum AntennaSegmentType implements StringRepresentable {
        BASE("base"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        AntennaSegmentType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
