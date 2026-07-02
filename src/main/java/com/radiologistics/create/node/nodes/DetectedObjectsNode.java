package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class DetectedObjectsNode extends AlgoNode {

    public DetectedObjectsNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "detected_objects";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("index");
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("x", "y", "z", "name", "count");
    }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object indexObj = inputValues.get("index");
        int index = 0;
        if (indexObj instanceof Number num) {
            index = num.intValue();
        }

        if (context.getLevel() != null && context.getLevel().isClientSide()) {
            if (context.getComputer() != null) {
                if (outputPort.equals("count")) return (double) context.getComputer().getCachedTracksCount();
                List<com.radiologistics.create.compat.RadarIntegration.TargetInfo> targets = context.getComputer().getCachedTargets();
                if (index >= 0 && index < targets.size()) {
                    com.radiologistics.create.compat.RadarIntegration.TargetInfo info = targets.get(index);
                    if (outputPort.equals("x")) return info.x();
                    if (outputPort.equals("y")) return info.y();
                    if (outputPort.equals("z")) return info.z();
                    if (outputPort.equals("name")) return info.name();
                }
            }
            if (outputPort.equals("name")) return "";
            return 0.0;
        }

        if (context.getComputer() != null && context.getComputer().isModuleConnected("network_controller")) {
            BlockPos pos = context.getComputer().getModulePos("network_controller");
            if (pos != null && context.getLevel() != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                if (net.neoforged.fml.ModList.get().isLoaded("create_radar")) {
                    if (outputPort.equals("count")) {
                        return (double) com.radiologistics.create.compat.RadarIntegration.getTracksCount(be);
                    }
                    Object target = getRadarTargetAtIndex(be, index, outputPort);
                    if (target != null) {
                        return target;
                    }
                }
            }
        }

        if (outputPort.equals("count")) return 0.0;
        if (outputPort.equals("name")) return "";
        return 0.0;
    }

    private Object getRadarTargetAtIndex(BlockEntity be, int index, String outputPort) {
        com.radiologistics.create.compat.RadarIntegration.TargetInfo info =
            com.radiologistics.create.compat.RadarIntegration.getTargetInfoAtIndex(be, index);
        if (info != null) {
            if (outputPort.equals("x")) return info.x();
            if (outputPort.equals("y")) return info.y();
            if (outputPort.equals("z")) return info.z();
            if (outputPort.equals("name")) return info.name();
        }
        return null;
    }
}
