package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class TextNode extends AlgoNode {
    private String text = "";

    public TextNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    @Override
    public String getType() { return "text"; }

    @Override
    public List<String> getInputPorts() { return Collections.emptyList(); }

    @Override
    public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", text);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        text = tag.getString("text");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        return text;
    }
}
