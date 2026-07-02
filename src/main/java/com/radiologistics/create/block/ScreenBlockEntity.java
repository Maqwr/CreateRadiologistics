package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ScreenBlockEntity extends BaseModuleBlockEntity {
    private String gizmosJson = "[]";
    private int tickCount = 0;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSPARENT_SCREEN.get(), pos, state);
    }

    public void tick() {
        if (level != null && !level.isClientSide()) {
            if (com.radiologistics.create.Radiologistics.isServerStopping) {
                return;
            }
            tickCount++;
            if (tickCount % 20 == 0) {
                if (computerPos != null) {
                    if (level.hasChunkAt(computerPos)) {
                        BlockEntity be = BaseModuleBlock.resolveBlockEntity(level, computerPos);
                        if (!(be instanceof MainComputerBlockEntity)) {
                            computerPos = null;
                            setGizmosJson("[]");
                            setChanged();
                            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    public String getGizmosJson() {
        return gizmosJson;
    }

    public void setGizmosJson(String gizmosJson) {
        if (gizmosJson == null) gizmosJson = "[]";
        if (!this.gizmosJson.equals(gizmosJson)) {
            this.gizmosJson = gizmosJson;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("gizmosJson", gizmosJson);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gizmosJson")) {
            gizmosJson = tag.getString("gizmosJson");
        } else {
            gizmosJson = "[]";
        }
    }
}
