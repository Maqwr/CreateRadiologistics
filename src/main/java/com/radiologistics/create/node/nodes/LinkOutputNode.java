package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class LinkOutputNode extends AlgoNode {
    private String freq1 = "minecraft:dirt";
    private String freq2 = "minecraft:dirt";
    private int powerLevel = 0;

    public LinkOutputNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getFreq1() {
        return freq1;
    }

    public void setFreq1(String freq1) {
        this.freq1 = freq1;
    }

    public String getFreq2() {
        return freq2;
    }

    public void setFreq2(String freq2) {
        this.freq2 = freq2;
    }

    public int getPowerLevel() {
        return powerLevel;
    }

    @Override
    public String getType() {
        return "link_output";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("power");
    }

    @Override
    public List<String> getOutputPorts() {
        return Collections.emptyList();
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("freq1", freq1);
        tag.putString("freq2", freq2);
        tag.putInt("powerLevel", powerLevel);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        freq1 = tag.contains("freq1") ? tag.getString("freq1") : "minecraft:dirt";
        freq2 = tag.contains("freq2") ? tag.getString("freq2") : "minecraft:dirt";
        powerLevel = tag.getInt("powerLevel");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
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
