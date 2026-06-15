package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Gizmos3DNode extends AlgoNode {
    private static final Gson GSON = new Gson();

    public Gizmos3DNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "gizmos_3d"; }

    @Override
    public List<String> getInputPorts() { return List.of("x", "y", "z", "gizmos", "layer"); }

    @Override
    public List<String> getOutputPorts() { return List.of("gizmos"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        double x = toDouble(inputValues.get("x"));
        double y = toDouble(inputValues.get("y"));
        double z = toDouble(inputValues.get("z"));
        Object gizmosVal = inputValues.get("gizmos");
        String gizmosJson = (gizmosVal != null) ? String.valueOf(gizmosVal) : "[]";

        Object layerVal = inputValues.get("layer");
        int layer = 0;
        if (layerVal != null && !layerVal.equals("")) {
            try {
                layer = (int) toDouble(layerVal);
            } catch (Exception ignored) {}
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("type", "3d");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("layer", layer);

        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(gizmosJson);
            if (el.isJsonArray()) {
                obj.add("gizmos", el.getAsJsonArray());
            } else {
                obj.add("gizmos", new JsonArray());
            }
        } catch (Exception e) {
            obj.add("gizmos", new JsonArray());
        }

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
