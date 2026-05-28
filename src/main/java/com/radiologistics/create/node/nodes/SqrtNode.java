package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class SqrtNode extends AlgoNode {

    public SqrtNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "sqrt";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("number");
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("result");
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
        Object numObj = inputValues.get("number");
        double val = 0;
        if (numObj instanceof Number n) {
            val = n.doubleValue();
        } else if (numObj != null) {
            try {
                val = Double.parseDouble(String.valueOf(numObj));
            } catch (NumberFormatException ignored) {}
        }
        return val >= 0 ? Math.sqrt(val) : 0.0;
    }
}
