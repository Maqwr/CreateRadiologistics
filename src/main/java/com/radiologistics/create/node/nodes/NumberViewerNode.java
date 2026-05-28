package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class NumberViewerNode extends AlgoNode {
    private double lastValue = 0.0;
    private boolean hasValue = false;

    public NumberViewerNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "number_viewer";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("value");
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
    public void loadProperties(CompoundTag tag) {}

    public double getLastValue() {
        return lastValue;
    }

    public boolean hasValue() {
        return hasValue;
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("value");
        if (val instanceof Number n) {
            lastValue = n.doubleValue();
            hasValue = true;
        } else if (val != null) {
            String str = String.valueOf(val).trim().replace(",", ".");
            if (str.isEmpty()) {
                hasValue = false;
            } else {
                try {
                    lastValue = Double.parseDouble(str);
                    hasValue = true;
                } catch (NumberFormatException e) {
                    hasValue = false;
                }
            }
        } else {
            hasValue = false;
        }
        return null;
    }
}
