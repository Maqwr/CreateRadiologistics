package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class ShapeNode extends AlgoNode {
    private static final String[] SHAPES = {"rect", "circle", "line", "text", "point"};
    private int shapeIndex = 0;

    public ShapeNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getShape() {
        return SHAPES[shapeIndex];
    }

    public void cycle() {
        shapeIndex = (shapeIndex + 1) % SHAPES.length;
    }

    public void setShape(String val) {
        for (int i = 0; i < SHAPES.length; i++) {
            if (SHAPES[i].equalsIgnoreCase(val)) {
                shapeIndex = i;
                return;
            }
        }
    }

    @Override
    public String getType() { return "shape"; }

    @Override
    public List<String> getInputPorts() { return List.of("outline"); }

    @Override
    public List<String> getOutputPorts() { return List.of("shape"); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("shape", getShape());
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("shape")) {
            setShape(tag.getString("shape"));
        }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        double outlineVal = 100.0;
        if (inputValues.containsKey("outline")) {
            Object v = inputValues.get("outline");
            if (v instanceof Number n) {
                outlineVal = n.doubleValue();
            } else if (v != null) {
                try {
                    outlineVal = Double.parseDouble(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return getShape() + ":" + (int) outlineVal;
    }
}
