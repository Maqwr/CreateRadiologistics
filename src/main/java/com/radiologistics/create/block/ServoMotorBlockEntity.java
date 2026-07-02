package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public class ServoMotorBlockEntity extends com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity implements IComputerLinkable {
    private BlockPos computerPos = null;
    private float targetAngle = 0.0f;
    private float currentAngle = 0.0f;
    private float lastAngle = 0.0f;
    private float ticksPerDegree = 1.0f;
    private float lastReportedModifier = 0.0f;

    public ServoMotorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVO_MOTOR.get(), pos, state);
    }

    @Override
    public BlockPos getComputerPos() {
        return computerPos;
    }

    @Override
    public void setComputerPos(BlockPos pos) {
        this.computerPos = pos;
    }

    private float getServoOutputSpeed() {
        if (Math.abs(getSpeed()) < 0.001f || isOverStressed()) {
            return 0.0f;
        }
        float diff = targetAngle - currentAngle;
        if (Math.abs(diff) > 0.01f) {
            float degPerTick = Math.abs(getSpeed()) / (32.0f * Math.max(0.01f, ticksPerDegree));
            float dir = Math.signum(diff);
            return dir * degPerTick * 3.3333333f;
        }
        return 0.0f;
    }

    @Override
    public float getRotationSpeedModifier(net.minecraft.core.Direction dir) {
        BlockState state = getBlockState();
        if (!state.hasProperty(ServoMotorBlock.FACING)) return 1.0f;
        net.minecraft.core.Direction facing = state.getValue(ServoMotorBlock.FACING);
        if (dir == facing) {
            float inputSpeed = getSpeed();
            if (Math.abs(inputSpeed) < 0.001f) {
                return 0.0f;
            }
            return getServoOutputSpeed() / inputSpeed;
        }
        return 1.0f;
    }

    @Override
    public void tick() {
        super.tick();

        lastAngle = currentAngle;

        boolean hasPower = Math.abs(speed) > 0.001f && !isOverStressed();
        if (hasPower) {
            float diff = targetAngle - currentAngle;
            if (Math.abs(diff) > 0.01f) {
                float degPerTick = Math.abs(getSpeed()) / (32.0f * Math.max(0.01f, ticksPerDegree));
                if (diff > 0) {
                    currentAngle += Math.min(diff, degPerTick);
                } else {
                    currentAngle += Math.max(diff, -degPerTick);
                }
            } else {
                currentAngle = targetAngle;
            }
        }

        float currentModifier = 0.0f;
        BlockState state = getBlockState();
        if (state.hasProperty(ServoMotorBlock.FACING)) {
            net.minecraft.core.Direction facing = state.getValue(ServoMotorBlock.FACING);
            currentModifier = getRotationSpeedModifier(facing);
        }
        if (Math.abs(currentModifier - lastReportedModifier) > 0.001f) {
            lastReportedModifier = currentModifier;
            if (level != null && !level.isClientSide()) {
                com.simibubi.create.content.kinetics.RotationPropagator.handleRemoved(level, worldPosition, this);
                com.simibubi.create.content.kinetics.RotationPropagator.handleAdded(level, worldPosition, this);
            }
        }
    }

    public float getTargetAngle() {
        return targetAngle;
    }

    public void setTargetAngle(float angle) {
        angle = Math.max(-180.0f, Math.min(180.0f, angle));
        if (this.targetAngle != angle) {
            this.targetAngle = angle;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public float getCurrentAngle() {
        return currentAngle;
    }

    public float getLastAngle() {
        return lastAngle;
    }

    public float getTicksPerDegree() {
        return ticksPerDegree;
    }

    public void setTicksPerDegree(float ticksPerDegree) {
        ticksPerDegree = Math.max(0.01f, ticksPerDegree);
        if (this.ticksPerDegree != ticksPerDegree) {
            this.ticksPerDegree = ticksPerDegree;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("targetAngle", targetAngle);
        tag.putFloat("currentAngle", currentAngle);
        tag.putFloat("ticksPerDegree", ticksPerDegree);

        if (computerPos != null) {
            CompoundTag offsetTag = new CompoundTag();
            offsetTag.putInt("dx", computerPos.getX() - worldPosition.getX());
            offsetTag.putInt("dy", computerPos.getY() - worldPosition.getY());
            offsetTag.putInt("dz", computerPos.getZ() - worldPosition.getZ());
            tag.put("computerPosRelative", offsetTag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targetAngle = tag.getFloat("targetAngle");
        currentAngle = tag.getFloat("currentAngle");
        lastAngle = currentAngle;
        if (tag.contains("ticksPerDegree")) {
            ticksPerDegree = tag.getFloat("ticksPerDegree");
        } else {
            ticksPerDegree = 1.0f;
        }

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
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, registries, true);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            read(tag, lookupProvider, true);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        read(tag, lookupProvider, true);
    }
}
