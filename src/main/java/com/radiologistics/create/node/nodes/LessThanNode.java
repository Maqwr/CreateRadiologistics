package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class LessThanNode extends AlgoNode {
    public LessThanNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "less_than"; }

    @Override
    public List<String> getInputPorts() { return List.of("a", "b"); }

    @Override
    public List<String> getOutputPorts() { return List.of("result"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        return toDouble(inputValues.get("a")) < toDouble(inputValues.get("b"));
    }
}
