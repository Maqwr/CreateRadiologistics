package com.radiologistics.create.block;

import com.radiologistics.create.network.PlayerLinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;

public abstract class BaseModuleBlock extends Block implements EntityBlock, com.simibubi.create.content.equipment.wrench.IWrenchable {
    protected final String moduleType;

    public BaseModuleBlock(Properties properties, String moduleType) {
        super(properties);
        this.moduleType = moduleType;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (WrenchHelper.isWrench(stack)) {
            return WrenchHelper.handleWrench(state, level, pos, player, stack);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public String getModuleType() {
        return moduleType;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player) {

            if (moduleType.equals("screen")) {
                BlockPos targetPos = null;
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    BlockPos neighborPos = pos.relative(dir);
                    BlockEntity neighborBE = resolveBlockEntity(level, neighborPos);
                    if (neighborBE instanceof ScreenBlockEntity neighborScreen) {
                        BlockPos compPos = neighborScreen.getComputerPos();
                        if (compPos != null) {
                            targetPos = compPos;
                            break;
                        }
                    }
                }
                if (targetPos != null) {
                    BlockEntity compBE = resolveBlockEntity(level, targetPos);
                    if (compBE instanceof MainComputerBlockEntity computer) {
                        boolean success = computer.linkModule("screen", pos);
                        if (success) {
                            BlockEntity moduleBE = level.getBlockEntity(pos);
                            if (moduleBE instanceof BaseModuleBlockEntity module) {
                                module.setComputerPos(targetPos);
                                module.setChanged();
                            }
                            player.displayClientMessage(Component.literal("success").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                            return;
                        }
                    }
                }
            }

            BlockPos computerPos = PlayerLinkManager.getPendingLink(player.getUUID());
            if (computerPos != null) {
                BlockEntity be = resolveBlockEntity(level, computerPos);
                if (be instanceof MainComputerBlockEntity computer) {

                    net.minecraft.world.phys.Vec3 p1 = MainComputerBlockEntity.getWorldPos(level, pos);
                    net.minecraft.world.phys.Vec3 p2 = MainComputerBlockEntity.getWorldPos(level, computerPos);
                    double distSq = p1.distanceToSqr(p2);
                    if (distSq > 100.0) {
                        player.displayClientMessage(Component.literal("too far").withStyle(ChatFormatting.RED), true);
                    } else {
                        boolean success = computer.linkModule(moduleType, pos);
                        if (success) {
                            BlockEntity moduleBE = level.getBlockEntity(pos);
                            if (moduleBE instanceof BaseModuleBlockEntity module) {
                                module.setComputerPos(computerPos);
                                module.setChanged();
                            }
                            player.displayClientMessage(Component.literal("success").withStyle(ChatFormatting.GREEN), true);
                            PlayerLinkManager.setPendingLink(player, null);
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
            if (be instanceof BaseModuleBlockEntity module) {
                module.setBroken(true);
                BlockPos computerPos = module.getComputerPos();
                if (computerPos != null && !level.isClientSide()) {
                    if (!isMoving && !com.radiologistics.create.Radiologistics.isServerStopping) {
                        BlockEntity compBE = resolveBlockEntity(level, computerPos);
                        if (compBE instanceof MainComputerBlockEntity computer) {
                            if (moduleType.equals("jammer") || moduleType.equals("gyroscope") || moduleType.equals("screen")) {
                                computer.unlinkModule(moduleType, pos);
                            } else if (pos.equals(computer.getModulePos(moduleType))) {
                                computer.unlinkModule(moduleType);
                            }
                        }
                    }
                }
                if (!level.isClientSide() && moduleType.equals("audio")) {
                    com.radiologistics.create.node.nodes.AudioPlayNode.stopPlaying(pos);
                    for (net.minecraft.world.entity.player.Player player : level.players()) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new com.radiologistics.create.network.PlayAudioModulePacket(pos, false, "url", "", 1.0, 1.0, 0.0)
                            );
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new com.radiologistics.create.network.PlayAudioModulePacket(pos, false, "tts", "", 1.0, 1.0, 0.0)
                            );
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    private static boolean reflectionChecked = false;
    private static Class<?> subLevelClass = null;
    private static java.lang.reflect.Method getLevelMethod = null;
    private static Class<?> companionClass = null;
    private static Object companionInstance = null;
    private static java.lang.reflect.Method getContainingMethod = null;

    private static void initReflection() {
        if (reflectionChecked) return;
        reflectionChecked = true;
        try {
            subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            getLevelMethod = subLevelClass.getMethod("getLevel");
        } catch (Throwable ignored) {}
        try {
            companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            companionInstance = companionClass.getField("INSTANCE").get(null);
            getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);
        } catch (Throwable ignored) {}
    }

    public static BlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;

        if (level.hasChunkAt(pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) return be;
        }

        if (com.radiologistics.create.Radiologistics.isServerStopping) {
            return null;
        }

        initReflection();

        if (getLevelMethod != null && com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(level)) {
            try {
                Level parentLevel = (Level) getLevelMethod.invoke(level);
                if (parentLevel != null && parentLevel.hasChunkAt(pos)) {
                    BlockEntity be = parentLevel.getBlockEntity(pos);
                    if (be != null) return be;
                }
            } catch (Throwable ignored) {}
        }

        if (companionInstance != null && getContainingMethod != null) {
            try {
                Object subLevel = getContainingMethod.invoke(companionInstance, level, pos);
                if (subLevel != null) {
                    try {
                        Object plot = subLevel.getClass().getMethod("getPlot").invoke(subLevel);
                        if (plot != null) {
                            net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(pos);
                            java.lang.reflect.Method getChunkMethod = plot.getClass().getMethod("getChunk", net.minecraft.world.level.ChunkPos.class);
                            net.minecraft.world.level.chunk.LevelChunk chunk = (net.minecraft.world.level.chunk.LevelChunk) getChunkMethod.invoke(plot, chunkPos);
                            if (chunk != null) {
                                BlockEntity be = chunk.getBlockEntity(pos);
                                if (be != null) return be;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static net.minecraft.world.phys.shapes.VoxelShape rotateShape(net.minecraft.world.phys.shapes.VoxelShape shape, net.minecraft.core.Direction facing) {
        if (facing == net.minecraft.core.Direction.UP) return shape;
        net.minecraft.world.phys.shapes.VoxelShape result = net.minecraft.world.phys.shapes.Shapes.empty();
        for (net.minecraft.world.phys.AABB box : shape.toAabbs()) {
            result = net.minecraft.world.phys.shapes.Shapes.or(result, net.minecraft.world.phys.shapes.Shapes.create(rotateAABB(box, facing)));
        }
        return result;
    }

    public static net.minecraft.world.phys.AABB rotateAABB(net.minecraft.world.phys.AABB box, net.minecraft.core.Direction facing) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        switch (facing) {
            case DOWN:
                return new net.minecraft.world.phys.AABB(minX, 1.0 - maxY, 1.0 - maxZ, maxX, 1.0 - minY, 1.0 - minZ);
            case NORTH:
                return new net.minecraft.world.phys.AABB(minX, minZ, 1.0 - maxY, maxX, maxZ, 1.0 - minY);
            case SOUTH:
                return new net.minecraft.world.phys.AABB(minX, 1.0 - maxZ, minY, maxX, 1.0 - minZ, maxY);
            case WEST:
                return new net.minecraft.world.phys.AABB(1.0 - maxY, 1.0 - maxX, minZ, 1.0 - minY, 1.0 - minX, maxZ);
            case EAST:
                return new net.minecraft.world.phys.AABB(minY, 1.0 - maxX, minZ, maxY, 1.0 - minX, maxZ);
            case UP:
            default:
                return box;
        }
    }
}
