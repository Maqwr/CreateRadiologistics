package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JammerNode extends AlgoNode {

    public JammerNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "jammer";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("start", "end", "event");
    }

    @Override
    public List<String> getOutputPorts() {
        return Collections.emptyList();
    }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getLevel() != null && !context.getLevel().isClientSide() && context.getComputer() != null) {
            Object evt = inputValues.get("event");
            boolean active = true;
            if (evt instanceof Boolean b) {
                active = b;
            } else if (evt != null) {
                String str = String.valueOf(evt).trim().toLowerCase();
                active = str.equals("true") || str.equals("1");
            }

            if (!active) {
                return null;
            }

            Object startObj = inputValues.get("start");
            Object endObj = inputValues.get("end");

            int start = parseChannelVal(startObj);
            int end = parseChannelVal(endObj);

            if (start > end) {
                int temp = start;
                start = end;
                end = temp;
            }

            int N = context.getComputer().getJammers().size();
            int maxAllowedRange = 25 * N;

            int size = end - start + 1;
            if (size > maxAllowedRange) {
                size = maxAllowedRange;
            }

            if (end > 255) {
                end = 255;
                start = end - size + 1;
                if (start < 0) start = 0;
            } else {
                end = start + size - 1;
            }

            context.getComputer().addActiveJammedRange(start, end);
        }
        return null;
    }

    private int parseChannelVal(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number num) {
            int val = num.intValue();
            return Math.max(0, val);
        }
        try {
            String str = String.valueOf(obj).replaceAll("[^0-9-]", "");
            if (str.isEmpty()) return 0;
            int val = Integer.parseInt(str);
            return Math.max(0, val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
