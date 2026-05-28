package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class VariableNode extends AlgoNode {
    private String variableName = "";

    public VariableNode(String id, double x, double y) {
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
        return "variable";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("value");
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
        if (context.getGraph() != null) {
            return context.getGraph().getVariables().getOrDefault(variableName, "");
        }
        return "";
    }
}
