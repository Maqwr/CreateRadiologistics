package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.Level;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

public class SmartOpticalSensorBlockEntity extends SmartBlockEntity {
    private String lookedAtName = "";
    private float targetDistance = 150.0f;
    protected ScrollValueBehaviour range;

    private final net.neoforged.neoforge.items.ItemStackHandler itemHandler = new net.neoforged.neoforge.items.ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return false;
        }
        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return stack;
        }
    };

    public net.neoforged.neoforge.items.IItemHandler getItemHandler() {
        return itemHandler;
    }

    public SmartOpticalSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_OPTICAL_SENSOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(
            net.minecraft.network.chat.Component.literal("Range"),
            this,
            new RangeValueBoxTransform()
        );
        range.between(1, 150);
        range.setValue(150);
        behaviours.add(range);
    }

    public static class RangeValueBoxTransform extends ValueBoxTransform {
        @Override
        public net.minecraft.world.phys.Vec3 getLocalOffset(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.hasProperty(SmartOpticalSensorBlock.FACING) ? state.getValue(SmartOpticalSensorBlock.FACING) : Direction.UP;
            Direction opposite = facing.getOpposite();
            double x = 0.5 + opposite.getStepX() * 0.505;
            double y = 0.5 + opposite.getStepY() * 0.505;
            double z = 0.5 + opposite.getStepZ() * 0.505;
            return new net.minecraft.world.phys.Vec3(x, y, z);
        }

        @Override
        public void rotate(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state, com.mojang.blaze3d.vertex.PoseStack ms) {
            Direction facing = state.hasProperty(SmartOpticalSensorBlock.FACING) ? state.getValue(SmartOpticalSensorBlock.FACING) : Direction.UP;
            Direction opposite = facing.getOpposite();
            switch (opposite) {
                case DOWN:
                    ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
                    break;
                case UP:
                    ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                    break;
                case NORTH:
                    break;
                case SOUTH:
                    ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                    break;
                case EAST:
                    ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90f));
                    break;
                case WEST:
                    ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
                    break;
            }
        }
    }

    public String getLookedAtName() {
        return lookedAtName;
    }

    public float getTargetDistance() {
        return targetDistance;
    }

    public void setTargetDistance(float targetDistance) {
        this.targetDistance = targetDistance;
    }

    public void tickServer() {
        if (level == null || level.isClientSide()) return;

        BlockState state = getBlockState();
        if (!state.hasProperty(SmartOpticalSensorBlock.FACING)) return;

        Direction facing = state.getValue(SmartOpticalSensorBlock.FACING);

        Vec3 start = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        start = start.add(new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()).scale(0.51));

        int maxRange = range != null ? range.getValue() : 150;
        Vec3 dirVec = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vec3 end = start.add(dirVec.scale(maxRange));

        Level parentLevel = level;
        if (com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(level)) {
            try {
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                Level parent = (Level) getLevelMethod.invoke(level);
                if (parent != null) {
                    parentLevel = parent;
                }
            } catch (Throwable ignored) {}
        }

        Vec3 startParent = start;
        Vec3 endParent = end;
        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Object companion = companionClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            Vec3 projectedStart = (Vec3) projectMethod.invoke(companion, level, start);
            Vec3 projectedEnd = (Vec3) projectMethod.invoke(companion, level, end);
            if (projectedStart != null) startParent = projectedStart;
            if (projectedEnd != null) endParent = projectedEnd;
        } catch (Throwable ignored) {}

        net.minecraft.world.level.ClipContext clipContext = new net.minecraft.world.level.ClipContext(
            startParent, endParent,
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            (net.minecraft.world.entity.Entity) null
        );
        BlockHitResult blockHit = parentLevel.clip(clipContext);
        double blockDist = maxRange;
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            Vec3 hitLoc = blockHit.getLocation();
            Vec3 globalHitPos = null;
            try {
                Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                Object companion = companionClass.getField("INSTANCE").get(null);
                java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
                globalHitPos = (Vec3) projectMethod.invoke(companion, parentLevel, hitLoc);
            } catch (Throwable ignored) {}
            if (globalHitPos == null) {
                globalHitPos = hitLoc;
            }
            blockDist = startParent.distanceTo(globalHitPos);
        }

        AABB searchBox = new AABB(startParent, endParent).inflate(1.0);
        List<net.minecraft.world.entity.Entity> entities = parentLevel.getEntities((net.minecraft.world.entity.Entity)null, searchBox);
        net.minecraft.world.entity.Entity closestEntity = null;
        double closestEntityDist = maxRange;

        for (net.minecraft.world.entity.Entity entity : entities) {
            if (entity.isSpectator() || !entity.isAlive()) continue;
            AABB entityAABB = entity.getBoundingBox();
            Optional<Vec3> hit = entityAABB.clip(startParent, endParent);
            if (hit.isPresent()) {
                Vec3 hitPos = hit.get();
                Vec3 globalHit = null;
                try {
                    Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                    Object companion = companionClass.getField("INSTANCE").get(null);
                    java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
                    globalHit = (Vec3) projectMethod.invoke(companion, parentLevel, hitPos);
                } catch (Throwable ignored) {}
                if (globalHit == null) {
                    globalHit = hitPos;
                }
                double dist = startParent.distanceTo(globalHit);
                if (dist < closestEntityDist) {
                    closestEntityDist = dist;
                    closestEntity = entity;
                }
            }
        }

        boolean hitSomething = false;
        String newName = "";
        net.minecraft.world.item.ItemStack blockStack = net.minecraft.world.item.ItemStack.EMPTY;

        if (closestEntity != null && closestEntityDist < blockDist) {
            hitSomething = true;
            newName = closestEntity.getName().getString();
            BlockPos entityPos = closestEntity.blockPosition();
            newName = newName + ";" + entityPos.getX() + ";" + entityPos.getY() + ";" + entityPos.getZ();
            net.minecraft.world.item.Item egg = net.minecraft.world.item.SpawnEggItem.byId(closestEntity.getType());
            if (egg != null) {
                blockStack = new net.minecraft.world.item.ItemStack(egg);
            } else {
                blockStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NAME_TAG);
                blockStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, closestEntity.getName());
            }
        } else if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            BlockPos hitPos = blockHit.getBlockPos();
            if (!hitPos.equals(worldPosition)) {
                BlockState hitState = resolveBlockState(parentLevel, hitPos);
                if (!hitState.isAir()) {
                    hitSomething = true;

                    Level correctLevel = resolveLevel(parentLevel, hitPos);
                    net.minecraft.world.item.ItemStack cloneStack = hitState.getBlock().getCloneItemStack(correctLevel, hitPos, hitState);
                    if (cloneStack != null && !cloneStack.isEmpty()) {
                        newName = cloneStack.getHoverName().getString();
                        blockStack = cloneStack;
                    } else {
                        newName = hitState.getBlock().getName().getString();
                        blockStack = new net.minecraft.world.item.ItemStack(hitState.getBlock().asItem());
                    }
                    newName = newName + ";" + hitPos.getX() + ";" + hitPos.getY() + ";" + hitPos.getZ();
                }
            }
        }

        boolean wasPowered = state.getValue(SmartOpticalSensorBlock.POWERED);
        if (wasPowered != hitSomething) {
            level.setBlock(worldPosition, state.setValue(SmartOpticalSensorBlock.POWERED, hitSomething), 3);
        }

        double finalDist = hitSomething ? (closestEntity != null && closestEntityDist < blockDist ? closestEntityDist : blockDist) : maxRange;
        float finalDistFloat = (float) (finalDist + 0.51);
        boolean distChanged = Math.abs(this.targetDistance - finalDistFloat) > 0.05f;

        boolean nameChanged = !this.lookedAtName.equals(newName);
        if (nameChanged) {
            this.lookedAtName = newName;
        }

        if (distChanged || nameChanged) {
            this.targetDistance = finalDistFloat;
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }

        net.minecraft.world.item.ItemStack currentStored = itemHandler.getStackInSlot(0);
        if (!net.minecraft.world.item.ItemStack.matches(currentStored, blockStack)) {
            itemHandler.setStackInSlot(0, blockStack);
            setChanged();
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("lookedAtName", lookedAtName);
        tag.putFloat("targetDistance", targetDistance);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        lookedAtName = tag.getString("lookedAtName");
        targetDistance = tag.contains("targetDistance") ? tag.getFloat("targetDistance") : 150.0f;
    }

    private static BlockState resolveBlockState(Level level, BlockPos pos) {
        if (level == null || pos == null) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        Level resolvedLvl = resolveLevel(level, pos);
        if (resolvedLvl != null && resolvedLvl.hasChunkAt(pos)) {
            return resolvedLvl.getBlockState(pos);
        }
        return level.getBlockState(pos);
    }

    private static Level resolveLevel(Level level, BlockPos pos) {
        if (level == null || pos == null) return level;

        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Object companion = companionClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);
            Object subLevel = getContainingMethod.invoke(companion, level, pos);
            if (subLevel != null) {
                if (subLevel instanceof Level) {
                    return (Level) subLevel;
                }
                try {
                    java.lang.reflect.Method getLvl = subLevel.getClass().getMethod("getLevel");
                    Level lvl = (Level) getLvl.invoke(subLevel);
                    if (lvl != null) {
                        return lvl;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        if (com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(level)) {
            try {
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                Level parentLevel = (Level) getLevelMethod.invoke(level);
                if (parentLevel != null) {
                    return parentLevel;
                }
            } catch (Throwable ignored) {}
        }

        return level;
    }
}
