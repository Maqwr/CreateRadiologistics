package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import com.radiologistics.create.network.OpenComputerScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

public class MainComputerBlock extends Block implements EntityBlock, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1.0, 0.0, 1.0, 15.0, 6.0, 15.0),
        Block.box(3.0, 5.0, 3.0, 13.0, 9.0, 13.0)
    );
    public MainComputerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MainComputerBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static boolean isNetworkFiltererItem(net.minecraft.world.item.Item item) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("create_radar") && id.getPath().equals("network_filterer");
    }

    private static boolean isVistaItem(net.minecraft.world.item.Item item) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("vista");
    }

    private boolean isModuleItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == com.radiologistics.create.registry.ModItems.REDSTONE_LINK_MODULE.get()
            || item == com.radiologistics.create.registry.ModItems.MEMORY_MODULE.get()
            || item == com.radiologistics.create.registry.ModItems.GYROSCOPE_SENSOR.get()
            || item == com.radiologistics.create.registry.ModItems.ANTENNA.get()
            || item == com.radiologistics.create.registry.ModItems.JAMMER.get()
            || item == com.radiologistics.create.registry.ModItems.AUDIO_MODULE.get()
            || item == com.radiologistics.create.registry.ModItems.TRANSPARENT_SCREEN.get()
            || isNetworkFiltererItem(item);
    }

    @Override
    protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (WrenchHelper.isWrench(stack)) {
            return WrenchHelper.handleWrench(state, level, pos, player, stack);
        }
        if (player.isSecondaryUseActive() && stack.isEmpty()) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MainComputerBlockEntity computer) {
                    net.minecraft.world.item.ItemStack oldCassette = computer.getCassette();
                    if (!oldCassette.isEmpty()) {
                        computer.setCassette(net.minecraft.world.item.ItemStack.EMPTY);
                        if (!player.getInventory().add(oldCassette)) {
                            player.drop(oldCassette, false);
                        }
                        player.displayClientMessage(Component.translatable("message.radiologistics.cassette_extracted").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                        return ItemInteractionResult.sidedSuccess(level.isClientSide());
                    }
                }
            }
        }
        if (isVistaItem(stack.getItem())) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MainComputerBlockEntity computer) {
                    net.minecraft.world.item.ItemStack oldCassette = computer.getCassette();
                    net.minecraft.world.item.ItemStack newCassette = stack.copy();
                    newCassette.setCount(1);
                    computer.setCassette(newCassette);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    if (!oldCassette.isEmpty()) {
                        if (!player.getInventory().add(oldCassette)) {
                            player.drop(oldCassette, false);
                        }
                    }
                    player.displayClientMessage(Component.translatable("message.radiologistics.cassette_inserted").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (isModuleItem(stack)) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                com.radiologistics.create.network.PlayerLinkManager.setPendingLink(serverPlayer, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (stack.getItem() == com.radiologistics.create.registry.ModItems.PILOT_HELMET.get()) {
            if (!level.isClientSide()) {
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putLong("LinkedComputer", pos.asLong());
                });
                player.displayClientMessage(Component.translatable("tooltip.radiologistics.helmet_linked", pos.toShortString()).withStyle(ChatFormatting.GREEN), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MainComputerBlockEntity computer) {
                net.minecraft.nbt.CompoundTag nbt = computer.getGraph().toNBT();
                net.minecraft.nbt.ListTag modulesList = new net.minecraft.nbt.ListTag();
                for (String type : computer.getConnectedModuleTypes()) {
                    modulesList.add(net.minecraft.nbt.StringTag.valueOf(type));
                }
                nbt.put("connectedModules", modulesList);
                nbt.putInt("jammerCount", computer.getJammers().size());
                // Send current graph NBT to the client to open the canvas
                PacketDistributor.sendToPlayer(serverPlayer, new OpenComputerScreenPacket(pos, nbt));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MainComputerBlockEntity computer) {
            // 'side' is direction pointing out of neighbor to computer, so we query the opposite side
            return computer.getRedstoneOutput(side.getOpposite());
        }
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MainComputerBlockEntity computer) {
                computer.evaluateGraph();
            }
        }
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof MainComputerBlockEntity computer) {
                computer.tick();
            }
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BaseModuleBlock.rotateShape(SHAPE, state.getValue(FACING));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MainComputerBlockEntity computer) {
                if (!computer.getCassette().isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), computer.getCassette());
                }
                BlockPos audioPos = computer.getModulePos("audio");
                if (audioPos != null && !level.isClientSide()) {
                    com.radiologistics.create.node.nodes.AudioPlayNode.stopPlaying(audioPos);
                    for (net.minecraft.world.entity.player.Player player : level.players()) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            PacketDistributor.sendToPlayer(serverPlayer, new com.radiologistics.create.network.PlayAudioModulePacket(audioPos, false, "url", "", 1.0, 1.0, 0.0));
                            PacketDistributor.sendToPlayer(serverPlayer, new com.radiologistics.create.network.PlayAudioModulePacket(audioPos, false, "tts", "", 1.0, 1.0, 0.0));
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
