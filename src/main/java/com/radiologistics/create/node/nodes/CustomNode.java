package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class CustomNode extends AlgoNode {
    private static final Gson GSON = new Gson();
    private static final String DEFAULT_SCRIPT = "double temp = x * y;\nsum = temp;\ndiff = x - y;";

    private final List<String> inputs = new ArrayList<>(List.of("x", "y"));
    private final List<String> outputs = new ArrayList<>(List.of("sum", "diff"));
    private String codeScript = DEFAULT_SCRIPT;

    private transient Map<String, Double> lastExecutionResults = null;
    private transient long lastExecutionTime = -1;

    public CustomNode(String id, double x, double y) {
        super(id, x, y);
    }

    public List<String> getInputsList() {
        return inputs;
    }

    public List<String> getOutputsList() {
        return outputs;
    }

    public String getCodeScript() {
        return codeScript;
    }

    public void setCodeScript(String codeScript) {
        this.codeScript = codeScript;
        invalidateCache();
    }

    public void setSchema(List<String> newInputs, List<String> newOutputs, String newScript) {
        this.inputs.clear();
        this.inputs.addAll(newInputs);
        this.outputs.clear();
        this.outputs.addAll(newOutputs);
        this.codeScript = newScript;
        invalidateCache();
    }

    private void invalidateCache() {
        lastExecutionResults = null;
        lastExecutionTime = -1;
    }

    @Override
    public String getType() {
        return "custom";
    }

    @Override
    public List<String> getInputPorts() {
        return inputs;
    }

    @Override
    public List<String> getOutputPorts() {
        return outputs;
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag insList = new net.minecraft.nbt.ListTag();
        for (String in : inputs) {
            insList.add(net.minecraft.nbt.StringTag.valueOf(in));
        }
        tag.put("inputs", insList);

        net.minecraft.nbt.ListTag outsList = new net.minecraft.nbt.ListTag();
        for (String out : outputs) {
            outsList.add(net.minecraft.nbt.StringTag.valueOf(out));
        }
        tag.put("outputs", outsList);

        tag.putString("codeScript", codeScript);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {

        if (tag.contains("schemaJson")) {
            String json = tag.getString("schemaJson");
            try {
                Map<String, Object> map = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                List<?> inputsList = (List<?>) map.get("inputs");
                inputs.clear();
                if (inputsList != null) {
                    for (Object o : inputsList) inputs.add(String.valueOf(o));
                }
                List<?> outputsList = (List<?>) map.get("outputs");
                outputs.clear();
                if (outputsList != null) {
                    for (Object o : outputsList) outputs.add(String.valueOf(o));
                }
                Map<?, ?> formulasMap = (Map<?, ?>) map.get("formulas");
                StringBuilder sb = new StringBuilder();
                if (formulasMap != null) {
                    for (Map.Entry<?, ?> entry : formulasMap.entrySet()) {
                        sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append(";\n");
                    }
                }
                codeScript = sb.toString();
            } catch (Exception ignored) {}
            invalidateCache();
            return;
        }

        if (tag.contains("inputs")) {
            inputs.clear();
            net.minecraft.nbt.ListTag insList = tag.getList("inputs", 8);
            for (int i = 0; i < insList.size(); i++) {
                inputs.add(insList.getString(i));
            }
        }
        if (tag.contains("outputs")) {
            outputs.clear();
            net.minecraft.nbt.ListTag outsList = tag.getList("outputs", 8);
            for (int i = 0; i < outsList.size(); i++) {
                outputs.add(outsList.getString(i));
            }
        }
        if (tag.contains("codeScript")) {
            codeScript = tag.getString("codeScript");
        }
        invalidateCache();
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        long now = context.getLevel().getGameTime();
        if (lastExecutionResults == null || lastExecutionTime != now) {
            Map<String, Double> vars = new HashMap<>();

            for (String inName : inputs) {
                vars.put(inName, toDouble(inputValues.get(inName)));
            }

            try {
                JavaLikeScriptInterpreter.Stmt program = JavaLikeScriptInterpreter.parse(codeScript);
                program.execute(vars);
            } catch (Exception ignored) {}

            lastExecutionResults = vars;
            lastExecutionTime = now;
        }

        double val = lastExecutionResults.getOrDefault(outputPort, 0.0);
        if (val == Math.floor(val) && !Double.isInfinite(val) && !Double.isNaN(val)) {
            return (long) val;
        }
        return val;
    }
}
