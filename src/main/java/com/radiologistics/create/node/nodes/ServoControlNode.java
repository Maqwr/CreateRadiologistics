package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.ServoMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

public class ServoControlNode extends AlgoNode {
    private int servoIndex = 0;

    public ServoControlNode(String id, double x, double y) {
        super(id, x, y);
    }

    public int getServoIndex() { return servoIndex; }
    public void setServoIndex(int index) { this.servoIndex = index; }

    @Override
    public String getType() { return "servo_control"; }

    @Override
    public List<String> getInputPorts() { return List.of("angle", "ticks_per_degree"); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("servoIndex", servoIndex);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("servoIndex")) {
            servoIndex = tag.getInt("servoIndex");
        } else {
            servoIndex = 0;
        }
    }

    private double toDouble(Object v, double defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() == null || context.getLevel() == null) {
            return null;
        }
        double angle = toDouble(inputValues.get("angle"), 0.0);
        double ticksPerDegree = toDouble(inputValues.get("ticks_per_degree"), 1.0);
        if (ticksPerDegree <= 0.0) {
            ticksPerDegree = 1.0;
        }
        BlockPos pos = context.getComputer().getModulePos("servo_motor_" + servoIndex);
        if (pos != null) {
            BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
            if (be instanceof ServoMotorBlockEntity servo) {
                servo.setTicksPerDegree((float) ticksPerDegree);
                servo.setTargetAngle((float) angle);
            }
        }
        return null;
    }
}
