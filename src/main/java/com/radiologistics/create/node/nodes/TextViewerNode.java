package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class TextViewerNode extends AlgoNode {
    private String lastValue = "";
    private boolean hasValue = false;

    public TextViewerNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "text_viewer";
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

    public String getLastValue() {
        return lastValue;
    }

    public boolean hasValue() {
        return hasValue;
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("value");
        if (val != null) {
            lastValue = String.valueOf(val);
            hasValue = true;
        } else {
            lastValue = "";
            hasValue = false;
        }
        return null;
    }
}
