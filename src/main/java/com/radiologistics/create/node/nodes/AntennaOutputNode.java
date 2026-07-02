package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.radio.RadioNetworkManager;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class AntennaOutputNode extends AlgoNode {

    public AntennaOutputNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "antenna_output";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("channel", "message", "event");
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
        if (context.getLevel() != null && !context.getLevel().isClientSide()
                && context.getLevel().getServer() != null && context.getComputer() != null) {

            Object evt = inputValues.get("event");
            boolean shouldSend = true;
            if (evt instanceof Boolean b) {
                shouldSend = b;
            } else if (evt != null) {
                String str = String.valueOf(evt).trim().toLowerCase();
                shouldSend = str.equals("true") || str.equals("1");
            }

            if (!shouldSend) {
                return null;
            }

            Object chObj = inputValues.get("channel");
            String channel = RadioNetworkManager.sanitizeChannel(chObj == null ? "0" : String.valueOf(chObj));

            Object msg = inputValues.get("message");
            String messageStr = msg == null ? "" : String.valueOf(msg);

            int antHeight = context.getComputer().getComputerAntennaHeight();
            RadioNetworkManager.broadcast(
                    context.getLevel().getServer(),
                    channel,
                    messageStr,
                    context.getLevel().dimension(),
                    context.getPos(),
                    antHeight);
        }
        return null;
    }
}
