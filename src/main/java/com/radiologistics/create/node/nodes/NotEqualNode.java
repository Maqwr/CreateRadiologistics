package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class NotEqualNode extends AlgoNode {
    public NotEqualNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "not_equal"; }

    @Override
    public List<String> getInputPorts() { return List.of("a", "b"); }

    @Override
    public List<String> getOutputPorts() { return List.of("result"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object a = inputValues.get("a");
        Object b = inputValues.get("b");
        String strA = a == null ? "" : String.valueOf(a);
        String strB = b == null ? "" : String.valueOf(b);
        return !strA.equals(strB);
    }
}
