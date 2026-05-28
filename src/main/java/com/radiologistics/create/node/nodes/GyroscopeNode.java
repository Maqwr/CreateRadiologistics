package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.GyroscopeSensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class GyroscopeNode extends AlgoNode {
    public GyroscopeNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "gyroscope";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("pitch_x", "pitch_z", "yaw");
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
                if (be instanceof GyroscopeSensorBlockEntity gyro) {
                    float[] rot = gyro.getContraptionRotation();
                    if (outputPort.equals("pitch_x")) return String.valueOf(rot[0]);
                    if (outputPort.equals("pitch_z")) return String.valueOf(rot[1]);
                    if (outputPort.equals("yaw")) return String.valueOf(rot[2]);
                }
            }
        }
        return "0";
    }
}
