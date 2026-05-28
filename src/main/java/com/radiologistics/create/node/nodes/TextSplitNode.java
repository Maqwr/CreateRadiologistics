package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class TextSplitNode extends AlgoNode {

    public TextSplitNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "text_split"; }

    @Override
    public List<String> getInputPorts() { return List.of("text", "separator", "part_number"); }

    @Override
    public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        String textVal = String.valueOf(inputValues.getOrDefault("text", ""));
        String separator = " ";
        if (inputValues.containsKey("separator")) {
            separator = String.valueOf(inputValues.get("separator"));
        }
        Object partObj = inputValues.getOrDefault("part_number", 1.0);
        int partNum = 1;
        if (partObj instanceof Number num) {
            partNum = num.intValue();
        } else if (partObj instanceof String s) {
            try { partNum = (int) Double.parseDouble(s); } catch (Exception ignored) {}
        }
        
        String[] parts;
        if (separator.isEmpty()) {
            parts = textVal.split("");
        } else if (separator.equals(" ")) {
            parts = textVal.trim().split("\\s+");
        } else {
            parts = textVal.split(java.util.regex.Pattern.quote(separator));
        }
        if (parts.length > 0 && partNum >= 1 && partNum <= parts.length) {
            return parts[partNum - 1];
        }
        return "";
    }
}
