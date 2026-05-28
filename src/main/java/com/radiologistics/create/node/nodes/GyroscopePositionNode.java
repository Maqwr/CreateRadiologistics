package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class GyroscopePositionNode extends AlgoNode {

    public GyroscopePositionNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "gyroscope_position";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("x", "y", "z");
    }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() != null) {
            BlockPos pos = context.getComputer().getModulePos("gyroscope");
            if (pos != null && context.getLevel() != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                if (be != null) {
                    BlockPos gyroPos = be.getBlockPos();
                    net.minecraft.world.level.Level level = be.getLevel() != null ? be.getLevel() : context.getLevel();
                    
                    try {
                        Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                        Object companion = companionClass.getField("INSTANCE").get(null);
                        java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
                        net.minecraft.world.phys.Vec3 projected = (net.minecraft.world.phys.Vec3) projectMethod.invoke(companion, level, new net.minecraft.world.phys.Vec3(gyroPos.getX(), gyroPos.getY(), gyroPos.getZ()));
                        if (projected != null) {
                            if (outputPort.equals("x")) return projected.x;
                            if (outputPort.equals("y")) return projected.y;
                            if (outputPort.equals("z")) return projected.z;
                        }
                    } catch (Exception ignored) {}

                    if (outputPort.equals("x")) return (double) gyroPos.getX();
                    if (outputPort.equals("y")) return (double) gyroPos.getY();
                    if (outputPort.equals("z")) return (double) gyroPos.getZ();
                }
            }
        }
        return 0.0;
    }
}
