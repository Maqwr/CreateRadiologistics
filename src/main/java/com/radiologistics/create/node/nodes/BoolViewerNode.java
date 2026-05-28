package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class BoolViewerNode extends AlgoNode {
    private boolean lastValue = false;
    private boolean hasValue = false;

    public BoolViewerNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "bool_viewer";
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

    public boolean getLastValue() {
        return lastValue;
    }

    public boolean hasValue() {
        return hasValue;
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("value");
        if (val instanceof Boolean b) {
            lastValue = b;
            hasValue = true;
        } else if (val != null) {
            String str = String.valueOf(val).trim().toLowerCase();
            if (str.isEmpty()) {
                hasValue = false;
            } else {
                lastValue = str.equals("true") || str.equals("1");
                hasValue = true;
            }
        } else {
            hasValue = false;
        }
        return null;
    }
}
