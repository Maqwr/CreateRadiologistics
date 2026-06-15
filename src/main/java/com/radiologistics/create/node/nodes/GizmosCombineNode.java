package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class GizmosCombineNode extends AlgoNode {
    private static final Gson GSON = new Gson();

    public GizmosCombineNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "gizmos_combine"; }

    @Override
    public List<String> getInputPorts() { return List.of("g1", "g2", "g3", "g4"); }

    @Override
    public List<String> getOutputPorts() { return List.of("gizmos"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        JsonArray result = new JsonArray();
        for (String port : List.of("g1", "g2", "g3", "g4")) {
            Object val = inputValues.get(port);
            if (val != null && !String.valueOf(val).isBlank()) {
                try {
                    JsonElement el = JsonParser.parseString(String.valueOf(val));
                    if (el.isJsonArray()) {
                        for (JsonElement item : el.getAsJsonArray()) {
                            result.add(item);
                        }
                    } else if (el.isJsonObject()) {
                        result.add(el);
                    }
                } catch (Exception ignored) {}
            }
        }
        return GSON.toJson(result);
    }
}
