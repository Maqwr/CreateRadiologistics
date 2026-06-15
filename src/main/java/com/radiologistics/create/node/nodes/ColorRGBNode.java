package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class ColorRGBNode extends AlgoNode {
    public ColorRGBNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "color_rgb"; }

    @Override
    public List<String> getInputPorts() { return List.of("red", "green", "blue"); }

    @Override
    public List<String> getOutputPorts() { return List.of("color"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        int r = clamp(toInt(inputValues.get("red"), 0), 0, 255);
        int g = clamp(toInt(inputValues.get("green"), 255), 0, 255);
        int b = clamp(toInt(inputValues.get("blue"), 255), 0, 255);
        return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    }

    private int toInt(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
