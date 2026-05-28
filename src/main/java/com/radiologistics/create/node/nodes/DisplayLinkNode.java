package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class DisplayLinkNode extends AlgoNode {

    public DisplayLinkNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "display_link"; }

    @Override
    public List<String> getInputPorts() { return Collections.emptyList(); }

    @Override
    public List<String> getOutputPorts() { return List.of("value"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        if (context.getComputer() != null) {
            return context.getComputer().getDisplayLinkText();
        }
        return "";
    }
}
