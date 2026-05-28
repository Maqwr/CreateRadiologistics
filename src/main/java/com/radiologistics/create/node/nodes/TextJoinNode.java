package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class TextJoinNode extends AlgoNode {

    public TextJoinNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "text_join"; }

    @Override
    public List<String> getInputPorts() { return List.of("text1", "text2"); }

    @Override
    public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        String t1 = String.valueOf(inputValues.getOrDefault("text1", ""));
        String t2 = String.valueOf(inputValues.getOrDefault("text2", ""));
        return t1 + t2;
    }
}
