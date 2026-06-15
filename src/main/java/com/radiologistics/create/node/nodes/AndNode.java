package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class AndNode extends AlgoNode {
    public AndNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "and"; }

    @Override
    public List<String> getInputPorts() { return List.of("a", "b"); }

    @Override
    public List<String> getOutputPorts() { return List.of("result"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    private boolean isTruthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        } else if (v instanceof Number num) {
            return num.doubleValue() != 0.0;
        } else if (v != null) {
            String str = String.valueOf(v).trim().toLowerCase();
            return str.equals("true") || str.equals("1") || str.equals("1.0") || str.equals("yes");
        }
        return false;
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        return isTruthy(inputValues.get("a")) && isTruthy(inputValues.get("b"));
    }
}
