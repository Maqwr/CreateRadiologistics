package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class RedstoneOutputNode extends AlgoNode {
    private String side = "north";
    private int powerLevel = 0;

    public RedstoneOutputNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public int getPowerLevel() { return powerLevel; }

    @Override
    public String getType() { return "redstone_output"; }

    @Override
    public List<String> getInputPorts() { return List.of("power"); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("side", side);
        tag.putInt("powerLevel", powerLevel);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        side = tag.getString("side");
        powerLevel = tag.getInt("powerLevel");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        Object power = inputValues.get("power");
        if (power == null) {
            powerLevel = 0;
        } else if (power instanceof Boolean b) {
            powerLevel = b ? 15 : 0;
        } else if (power instanceof Number n) {
            powerLevel = Math.max(0, Math.min(15, n.intValue()));
        } else {
            String str = String.valueOf(power).trim();
            if (str.equalsIgnoreCase("true") || str.equals("1")) {
                powerLevel = 15;
            } else {
                try {
                    double d = Double.parseDouble(str);
                    powerLevel = Math.max(0, Math.min(15, (int) Math.round(d)));
                } catch (NumberFormatException e) {
                    powerLevel = 0;
                }
            }
        }
        return null;
    }
}
