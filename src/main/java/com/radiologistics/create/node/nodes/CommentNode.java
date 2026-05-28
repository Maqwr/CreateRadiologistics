package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class CommentNode extends AlgoNode {
    private String comment = "Comment here...";

    public CommentNode(String id, double x, double y) {
        super(id, x, y);
    }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    @Override
    public String getType() { return "comment"; }

    @Override
    public List<String> getInputPorts() { return Collections.emptyList(); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("comment", comment);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        comment = tag.getString("comment");
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, com.radiologistics.create.node.EvaluationContext context) {
        return null;
    }
}
