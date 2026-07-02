package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.radio.RadioNetworkManager;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class SignalNode extends AlgoNode {

    private String legacyChannel = null;

    public SignalNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "signal"; }

    @Override
    public List<String> getInputPorts() { return List.of("channel"); }

    @Override
    public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() {

        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {

        if (tag.contains("channel")) {
            legacyChannel = tag.getString("channel");
        }
    }

    public String getLegacyChannel() { return legacyChannel; }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object chObj = inputValues.get("channel");
        String channel;
        if (chObj == null || String.valueOf(chObj).isBlank()) {
            channel = legacyChannel != null ? legacyChannel : "0";
        } else {
            channel = RadioNetworkManager.sanitizeChannel(String.valueOf(chObj));
        }
        return RadioNetworkManager.getSignal(context.getLevel(), context.getPos(), channel);
    }
}
