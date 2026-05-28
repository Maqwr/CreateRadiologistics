package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/** A constant numeric value node. Clicking the node body opens an inline text editor. */
public class NumberNode extends AlgoNode {
    private double value = 0;

    public NumberNode(String id, double x, double y) {
        super(id, x, y);
    }

    public double getValue() { return value; }

    public void setValue(double v) { this.value = v; }

    /** Returns a clean string: integer if no fractional part, otherwise decimal. */
    public String getValueString() {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override public String getType() { return "number"; }
    @Override public List<String> getInputPorts() { return Collections.emptyList(); }
    @Override public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("value", value);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        value = tag.getDouble("value");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        // Return as Long when integer, Double otherwise — downstream nodes handle both
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return (long) value;
        }
        return value;
    }
}
