package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class CustomTargetNode extends AlgoNode {

    public CustomTargetNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "custom_target";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("x", "y", "z", "name", "event");
    }

    @Override
    public List<String> getOutputPorts() {
        return Collections.emptyList();
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
        if (context.getLevel() == null || context.getLevel().isClientSide() || context.getComputer() == null) {
            return null;
        }

        Object evt = inputValues.get("event");
        boolean shouldSet = true;
        if (evt instanceof Boolean b) {
            shouldSet = b;
        } else if (evt != null) {
            String str = String.valueOf(evt).trim().toLowerCase();
            shouldSet = str.equals("true") || str.equals("1");
        }

        if (!shouldSet) {
            if (context.getComputer().isModuleConnected("network_controller")) {
                BlockPos pos = context.getComputer().getModulePos("network_controller");
                if (pos != null) {
                    BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                    if (net.neoforged.fml.ModList.get().isLoaded("create_radar")) {
                        com.radiologistics.create.compat.RadarIntegration.clearCustomTarget(be);
                    }
                }
            }
            return null;
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        if (inputValues.get("x") instanceof Number n) x = n.doubleValue();
        if (inputValues.get("y") instanceof Number n) y = n.doubleValue();
        if (inputValues.get("z") instanceof Number n) z = n.doubleValue();

        String name = "Custom Target";
        if (inputValues.get("name") != null) {
            name = String.valueOf(inputValues.get("name"));
        }

        if (context.getComputer().isModuleConnected("network_controller")) {
            BlockPos pos = context.getComputer().getModulePos("network_controller");
            if (pos != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), pos);
                if (net.neoforged.fml.ModList.get().isLoaded("create_radar")) {
                    com.radiologistics.create.compat.RadarIntegration.setCustomTarget(be, x, y, z, name);
                }
            }
        }

        var comp = context.getComputer();
        comp.setCachedTargetX(x);
        comp.setCachedTargetY(y);
        comp.setCachedTargetZ(z);
        comp.setCachedTargetName(name);
        comp.setCachedTracksCount(1);
        comp.getCachedTargets().clear();
        comp.getCachedTargets().add(new com.radiologistics.create.compat.RadarIntegration.TargetInfo(x, y, z, name));

        return null;
    }
}
