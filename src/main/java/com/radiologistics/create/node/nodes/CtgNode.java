package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class CtgNode extends AlgoNode {
    public CtgNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "ctg"; }

    @Override
    public List<String> getInputPorts() { return List.of("number"); }

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
        double val = toDouble(inputValues.get("number"));
        double tanVal = Math.tan(Math.toRadians(val));
        if (tanVal == 0.0) return 0.0;
        double result = 1.0 / tanVal;
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return (long) result;
        }
        return result;
    }
}
