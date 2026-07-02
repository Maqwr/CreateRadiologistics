package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class GetListNode extends AlgoNode {
    private static final Gson GSON = new Gson();
    private String variableName = "";

    public GetListNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public String getType() {
        return "get_list";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("key");
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("value");
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("variableName", variableName);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        variableName = tag.getString("variableName");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getGraph() != null && !variableName.isEmpty()) {
            Object raw = context.getGraph().getVariables().get(variableName);
            String json = raw != null ? raw.toString() : "";
            Map<String, Object> map = parseList(json);

            Object keyVal = inputValues.get("key");
            if (keyVal != null) {
                String keyStr = String.valueOf(keyVal);
                Object val = map.get(keyStr);
                if (val instanceof Number num) {
                    double d = num.doubleValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d)) {
                        return (long) d;
                    }
                    return d;
                }
                return val != null ? val : "";
            }
        }
        return "";
    }

    private Map<String, Object> parseList(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
