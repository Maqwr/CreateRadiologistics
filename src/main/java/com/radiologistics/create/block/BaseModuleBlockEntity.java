package com.radiologistics.create.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BaseModuleBlockEntity extends BlockEntity {
    protected BlockPos computerPos = null;

    public BaseModuleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public BlockPos getComputerPos() {
        return computerPos;
    }

    public void setComputerPos(BlockPos pos) {
        this.computerPos = pos;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (computerPos != null) {
            CompoundTag offsetTag = new CompoundTag();
            offsetTag.putInt("dx", computerPos.getX() - worldPosition.getX());
            offsetTag.putInt("dy", computerPos.getY() - worldPosition.getY());
            offsetTag.putInt("dz", computerPos.getZ() - worldPosition.getZ());
            tag.put("computerPosRelative", offsetTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("computerPosRelative")) {
            CompoundTag offsetTag = tag.getCompound("computerPosRelative");
            int dx = offsetTag.getInt("dx");
            int dy = offsetTag.getInt("dy");
            int dz = offsetTag.getInt("dz");
            computerPos = new BlockPos(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
        } else if (tag.contains("computerPos")) {
            computerPos = BlockPos.of(tag.getLong("computerPos"));
        } else {
            computerPos = null;
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
}
