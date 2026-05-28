package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class ActiveTargetNode extends AlgoNode {

    public ActiveTargetNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "active_target";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("x", "y", "z", "name");
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
        if (context.getLevel() != null && context.getLevel().isClientSide()) {
            if (context.getComputer() != null) {
                if (outputPort.equals("x")) return context.getComputer().getCachedTargetX();
                if (outputPort.equals("y")) return context.getComputer().getCachedTargetY();
                if (outputPort.equals("z")) return context.getComputer().getCachedTargetZ();
                if (outputPort.equals("name")) return context.getComputer().getCachedTargetName();
            }
            if (outputPort.equals("name")) return "";
            return 0.0;
        }

        if (context.getComputer() != null && context.getComputer().isModuleConnected("network_controller")) {
            BlockPos pos = context.getComputer().getModulePos("network_controller");
            if (pos != null && context.getLevel() != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                if (net.neoforged.fml.ModList.get().isLoaded("create_radar")) {
                    Object target = getRadarTarget(be, outputPort);
                    if (target != null) {
                        return target;
                    }
                }
            }
        }
        if (outputPort.equals("name")) return "";
        return 0.0;
    }

    private Object getRadarTarget(BlockEntity be, String outputPort) {
        com.radiologistics.create.compat.RadarIntegration.TargetInfo info = com.radiologistics.create.compat.RadarIntegration.getTargetInfo(be);
        if (info != null) {
            if (outputPort.equals("x")) return info.x();
            if (outputPort.equals("y")) return info.y();
            if (outputPort.equals("z")) return info.z();
            if (outputPort.equals("name")) return info.name();
        }
        return null;
    }
}
