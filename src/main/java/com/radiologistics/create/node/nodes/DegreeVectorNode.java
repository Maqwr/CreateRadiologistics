package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.*;

public class DegreeVectorNode extends AlgoNode {
    public DegreeVectorNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "degree_vector"; }

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
        double a = toDouble(inputValues.get("a"));
        double b = toDouble(inputValues.get("b"));
        
        double diff = Mth.wrapDegrees(b - a);
        
        if (diff == Math.floor(diff) && !Double.isInfinite(diff)) {
            return (long) diff;
        }
        return diff;
    }
}
