package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Gizmos2DNode extends AlgoNode {
    private static final Gson GSON = new Gson();

    public Gizmos2DNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "gizmos_2d"; }

    @Override
    public List<String> getInputPorts() { return List.of("shape", "x", "y", "w", "h", "color", "text", "layer"); }

    @Override
    public List<String> getOutputPorts() { return List.of("gizmos"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        String shapeRaw = inputValues.get("shape") != null ? String.valueOf(inputValues.get("shape")).trim().toLowerCase() : "rect";
        String shape = shapeRaw;
        int outline = 100;
        if (shapeRaw.contains(":")) {
            String[] parts = shapeRaw.split(":");
            if (parts.length >= 2) {
                shape = parts[0].trim();
                try {
                    outline = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        double x = toDouble(inputValues.get("x"));
        double y = toDouble(inputValues.get("y"));
        double w = toDouble(inputValues.get("w"));
        double h = toDouble(inputValues.get("h"));
        String color = inputValues.get("color") != null ? String.valueOf(inputValues.get("color")) : "";
        String text = inputValues.get("text") != null ? String.valueOf(inputValues.get("text")) : "";

        Object layerVal = inputValues.get("layer");
        int layer = 0;
        if (layerVal != null && !layerVal.equals("")) {
            try {
                layer = (int) toDouble(layerVal);
            } catch (Exception ignored) {}
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "2d");
        obj.addProperty("shape", shape);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("w", w);
        obj.addProperty("h", h);
        obj.addProperty("color", color);
        obj.addProperty("text", text);
        obj.addProperty("outline", outline);
        obj.addProperty("layer", layer);

        JsonArray arr = new JsonArray();
        arr.add(obj);
        return GSON.toJson(arr);
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }
}
