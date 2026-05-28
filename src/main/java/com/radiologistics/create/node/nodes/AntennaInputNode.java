package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.radio.RadioNetworkManager;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class AntennaInputNode extends AlgoNode {
    private String channel = "0";

    public AntennaInputNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = RadioNetworkManager.sanitizeChannel(channel);
    }

    @Override
    public String getType() {
        return "antenna_input";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("value");
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("channel", channel);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        channel = tag.contains("channel") ? tag.getString("channel") : "0";
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        return RadioNetworkManager.getSignal(context.getLevel(), context.getPos(), channel);
    }
}
