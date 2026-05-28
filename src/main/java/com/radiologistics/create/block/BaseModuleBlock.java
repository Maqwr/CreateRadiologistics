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
import net.minecraft.ChatFormatting;

public abstract class BaseModuleBlock extends Block implements EntityBlock {
    protected final String moduleType;

    public BaseModuleBlock(Properties properties, String moduleType) {
        super(properties);
        this.moduleType = moduleType;
    }

    public String getModuleType() {
        return moduleType;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player) {
            BlockPos computerPos = PlayerLinkManager.getPendingLink(player.getUUID());
            if (computerPos != null) {
                BlockEntity be = resolveBlockEntity(level, computerPos);
                if (be instanceof MainComputerBlockEntity computer) {
                    // Check distance (max 10 blocks)
                    double distSq = pos.distSqr(computerPos);
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
            
            // Pop the block back as an item if linking is required but failed
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BaseModuleBlockEntity module) {
                BlockPos computerPos = module.getComputerPos();
                if (computerPos != null && !level.isClientSide()) {
                    BlockEntity compBE = resolveBlockEntity(level, computerPos);
                    if (compBE instanceof MainComputerBlockEntity computer) {
                        if (moduleType.equals("jammer")) {
                            computer.unlinkModule("jammer", pos);
                        } else if (pos.equals(computer.getModulePos(moduleType))) {
                            computer.unlinkModule(moduleType);
                        }
                    }
                }
                if (!level.isClientSide() && moduleType.equals("audio")) {
                    com.radiologistics.create.node.nodes.SoundPlayNode.stopPlaying(pos);
                    com.radiologistics.create.node.nodes.TextSpeakNode.stopPlaying(pos);
                    for (net.minecraft.world.entity.player.Player player : level.players()) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer, 
                                new com.radiologistics.create.network.PlayAudioModulePacket(pos, false, "url", "", 1.0, 1.0)
                            );
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer, 
                                new com.radiologistics.create.network.PlayAudioModulePacket(pos, false, "tts", "", 1.0, 1.0)
                            );
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public static BlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) return be;
        try {
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            if (subLevelClass.isInstance(level)) {
                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                Level parentLevel = (Level) getLevelMethod.invoke(level);
                if (parentLevel != null) {
                    be = parentLevel.getBlockEntity(pos);
                    if (be != null) return be;
                }
            }
        } catch (Exception ignored) {}
        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Object companion = companionClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);
            Object subLevelAccess = getContainingMethod.invoke(companion, level, pos);
            if (subLevelAccess instanceof Level subLevel) {
                be = subLevel.getBlockEntity(pos);
                if (be != null) return be;
            }
        } catch (Exception ignored) {}
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
