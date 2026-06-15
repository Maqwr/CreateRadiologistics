package com.radiologistics.create.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;

public class WrenchHelper {

    public static boolean isWrench(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().contains("wrench") || (id.getNamespace().equals("create") && id.getPath().equals("wrench"));
    }

    public static ItemInteractionResult handleWrench(BlockState state, Level level, BlockPos pos, Player player, ItemStack stack) {
        if (player.isSecondaryUseActive()) {
            dismantleBlock(state, level, pos, player);
        } else {
            rotateBlock(state, level, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    public static void dismantleBlock(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            Block.popResource(level, pos, new ItemStack(state.getBlock()));
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    15, 0.2, 0.2, 0.2, 0.1
                );
            }
            level.removeBlock(pos, false);
        }
    }

    public static void rotateBlock(BlockState state, Level level, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction current = state.getValue(BlockStateProperties.FACING);
            Direction next = getNextFacing(current);
            level.setBlock(pos, state.setValue(BlockStateProperties.FACING, next), 3);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private static Direction getNextFacing(Direction current) {
        return switch (current) {
            case UP -> Direction.NORTH;
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.DOWN;
            case DOWN -> Direction.UP;
        };
    }
}
