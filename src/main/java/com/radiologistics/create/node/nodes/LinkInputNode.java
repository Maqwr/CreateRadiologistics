package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.VirtualLinkable;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class LinkInputNode extends AlgoNode {
    private String freq1 = "minecraft:dirt";
    private String freq2 = "minecraft:dirt";

    public LinkInputNode(String id, double x, double y) {
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

    @Override
    public String getType() {
        return "link_input";
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
        tag.putString("freq1", freq1);
        tag.putString("freq2", freq2);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        freq1 = tag.contains("freq1") ? tag.getString("freq1") : "minecraft:dirt";
        freq2 = tag.contains("freq2") ? tag.getString("freq2") : "minecraft:dirt";
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() != null) {
            VirtualLinkable linkable = context.getComputer().getVirtualLinkable(id);
            if (linkable != null) {
                return (double) linkable.getReceivedStrength();
            }
        }
        return 0.0;
    }
}
