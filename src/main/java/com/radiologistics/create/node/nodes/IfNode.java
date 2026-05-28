package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class IfNode extends AlgoNode {
    public IfNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "if"; }

    @Override
    public List<String> getInputPorts() { return List.of("condition", "true_val", "false_val"); }

    @Override
    public List<String> getOutputPorts() { return List.of("result"); }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        Object condition = inputValues.get("condition");
        boolean isTrue = false;
        if (condition instanceof Boolean b) {
            isTrue = b;
        } else if (condition instanceof Number num) {
            isTrue = num.doubleValue() != 0.0;
        } else if (condition != null) {
            String str = String.valueOf(condition).trim().toLowerCase();
            isTrue = str.equals("true") || str.equals("1") || str.equals("1.0") || str.equals("yes");
        }

        return isTrue ? inputValues.getOrDefault("true_val", "") : inputValues.getOrDefault("false_val", "");
    }
}
