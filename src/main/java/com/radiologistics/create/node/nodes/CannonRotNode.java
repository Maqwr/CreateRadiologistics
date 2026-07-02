package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.*;

public class CannonRotNode extends AlgoNode {
    public CannonRotNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "cannon_rot";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("pitch", "yaw");
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
            BlockPos pos = context.getComputer().getModulePos("cannon_mount");
            if (pos != null && context.getLevel() != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                if (be != null && be.getClass().getName().equals("rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity")) {
                    try {
                        float pitch = 0;
                        float yaw = 0;
                        try {
                            java.lang.reflect.Field pitchField = be.getClass().getDeclaredField("cannonPitch");
                            java.lang.reflect.Field yawField = be.getClass().getDeclaredField("cannonYaw");
                            pitchField.setAccessible(true);
                            yawField.setAccessible(true);
                            pitch = pitchField.getFloat(be);
                            yaw = yawField.getFloat(be);
                        } catch (Exception e) {
                            try {
                                Method getPitchMethod = be.getClass().getMethod("getPitchOffset");
                                Method getYawMethod = be.getClass().getMethod("getYawOffset");
                                pitch = ((Number) getPitchMethod.invoke(be)).floatValue();
                                yaw = ((Number) getYawMethod.invoke(be)).floatValue();
                            } catch (Exception ignored) {}
                        }
                        if (outputPort.equals("pitch")) {
                            return String.valueOf(pitch);
                        }
                        if (outputPort.equals("yaw")) {
                            float adjustedYaw = net.minecraft.util.Mth.wrapDegrees(yaw);
                            return String.valueOf(adjustedYaw);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return "0";
    }
}
