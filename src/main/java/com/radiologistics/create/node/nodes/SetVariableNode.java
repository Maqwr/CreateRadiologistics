package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class SetVariableNode extends AlgoNode {
    private String variableName = "";

    public SetVariableNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public String getType() {
        return "set_variable";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("value", "event");
    }

    @Override
    public List<String> getOutputPorts() {
        return Collections.emptyList();
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("variableName", variableName);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        variableName = tag.getString("variableName");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getGraph() != null && !variableName.isEmpty()) {
            boolean isTrue = true;
            boolean hasEventLink = false;
            for (com.radiologistics.create.node.NodeGraph.NodeLink link : context.getGraph().getLinks()) {
                if (link.toNode().equals(getId()) && link.toPort().equals("event")) {
                    hasEventLink = true;
                    break;
                }
            }
            if (hasEventLink) {
                Object eventVal = inputValues.get("event");
                isTrue = false;
                if (eventVal instanceof Boolean b) {
                    isTrue = b;
                } else if (eventVal instanceof Number num) {
                    isTrue = num.doubleValue() != 0.0;
                } else if (eventVal != null) {
                    String str = String.valueOf(eventVal).trim().toLowerCase();
                    isTrue = str.equals("true") || str.equals("1") || str.equals("1.0");
                }
            }
            if (isTrue) {
                Object val = inputValues.get("value");
                context.getGraph().getVariables().put(variableName, val != null ? val : "");
            }
        }
        return null;
    }
}
