package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.ServoMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

public class ServoAngleNode extends AlgoNode {
    private int servoIndex = 0;

    public ServoAngleNode(String id, double x, double y) {
        super(id, x, y);
    }

    public int getServoIndex() { return servoIndex; }
    public void setServoIndex(int index) { this.servoIndex = index; }

    @Override
    public String getType() { return "servo_angle"; }

    @Override
    public List<String> getInputPorts() { return Collections.emptyList(); }

    @Override
    public List<String> getOutputPorts() { return List.of("angle"); }

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

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() == null || context.getLevel() == null) {
            return 0.0;
        }
        BlockPos pos = context.getComputer().getModulePos("servo_motor_" + servoIndex);
        if (pos != null) {
            BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
            if (be instanceof ServoMotorBlockEntity servo) {
                double angle = servo.getCurrentAngle();
                angle = Math.round(angle * 1000000.0) / 1000000.0;
                if (angle == Math.floor(angle) && !Double.isInfinite(angle)) {
                    return (long) angle;
                }
                return angle;
            }
        }
        return 0.0;
    }
}
