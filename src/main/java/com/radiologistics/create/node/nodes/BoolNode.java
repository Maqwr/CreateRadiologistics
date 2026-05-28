package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/** A constant true/false value node. Clicking the node body toggles the value inline. */
public class BoolNode extends AlgoNode {
    private boolean value = false;

    public BoolNode(String id, double x, double y) {
        super(id, x, y);
    }

    public boolean getValue() { return value; }
    public void setValue(boolean v) { this.value = v; }
    public void toggle() { this.value = !this.value; }

    @Override public String getType() { return "bool"; }
    @Override public List<String> getInputPorts() { return Collections.emptyList(); }
    @Override public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("value", value);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        value = tag.getBoolean("value");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        return value;
    }
}
