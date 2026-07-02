package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.GyroscopeSensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class GyroscopeNode extends AlgoNode {
    private int gyroIndex = 0;

    public GyroscopeNode(String id, double x, double y) {
        super(id, x, y);
    }

    public int getGyroIndex() { return gyroIndex; }
    public void setGyroIndex(int index) { this.gyroIndex = index; }

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
        return List.of("yaw", "pitch_x", "pitch_z");
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("gyroIndex", gyroIndex);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("gyroIndex")) {
            gyroIndex = tag.getInt("gyroIndex");
        } else {
            gyroIndex = 0;
        }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() != null) {
            BlockPos pos = context.getComputer().getModulePos("gyroscope_" + gyroIndex);
            if (pos != null && context.getLevel() != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                if (be instanceof GyroscopeSensorBlockEntity gyro) {
                    float[] rot = gyro.getContraptionRotation(context.getLevel());
                    if (outputPort.equals("yaw")) return String.valueOf(rot[2]);
                    if (outputPort.equals("pitch_x")) return String.valueOf(rot[0]);
                    if (outputPort.equals("pitch_z")) return String.valueOf(rot[1]);
                }
            }
        }
        return "0";
    }
}
