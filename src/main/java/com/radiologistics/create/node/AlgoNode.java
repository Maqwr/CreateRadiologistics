package com.radiologistics.create.node;

import com.radiologistics.create.node.nodes.*;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public abstract class AlgoNode {
    protected final String id;
    protected double x;
    protected double y;

    public AlgoNode(String id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPos(double x, double y) { this.x = x; this.y = y; }

    public abstract String getType();
    public abstract List<String> getInputPorts();
    public abstract List<String> getOutputPorts();

    public abstract CompoundTag saveProperties();
    public abstract void loadProperties(CompoundTag tag);

    /**
     * Evaluates this node's output port value based on evaluated values at its input ports.
     */
    public abstract Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context);

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("type", getType());
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.put("properties", saveProperties());
        return tag;
    }

    public static AlgoNode fromNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String type = tag.getString("type");
        double x = tag.getDouble("x");
        double y = tag.getDouble("y");
        CompoundTag props = tag.getCompound("properties");

        AlgoNode node = createNode(type, id, x, y);
        if (node != null) {
            node.loadProperties(props);
        }
        return node;
    }

    public static AlgoNode createNode(String type, String id, double x, double y) {
        return switch (type.toLowerCase()) {
            case "signal" -> new SignalNode(id, x, y);
            case "comment" -> new CommentNode(id, x, y);
            case "bool"   -> new BoolNode(id, x, y);
            case "number" -> new NumberNode(id, x, y);
            case "text"   -> new TextNode(id, x, y);
            case "equal" -> new EqualNode(id, x, y);
            case "not_equal" -> new NotEqualNode(id, x, y);
            case "greater_than" -> new GreaterThanNode(id, x, y);
            case "less_than" -> new LessThanNode(id, x, y);
            case "add" -> new AddNode(id, x, y);
            case "subtract" -> new SubtractNode(id, x, y);
            case "multiply" -> new MultiplyNode(id, x, y);
            case "divide" -> new DivideNode(id, x, y);
            case "if" -> new IfNode(id, x, y);
            case "or" -> new OrNode(id, x, y);
            case "floor" -> new FloorNode(id, x, y);
            case "sqrt" -> new SqrtNode(id, x, y);
            case "square" -> new SquareNode(id, x, y);
            case "atan2" -> new Atan2Node(id, x, y);
            case "invert" -> new InvertNode(id, x, y);
            case "abs" -> new AbsNode(id, x, y);
            case "cos" -> new CosNode(id, x, y);
            case "sin" -> new SinNode(id, x, y);
            case "tan" -> new TanNode(id, x, y);
            case "ctg" -> new CtgNode(id, x, y);
            case "active_target" -> new ActiveTargetNode(id, x, y);
            case "redstone_output" -> new RedstoneOutputNode(id, x, y);
            case "link_input" -> new LinkInputNode(id, x, y);
            case "link_output" -> new LinkOutputNode(id, x, y);
            case "variable" -> new VariableNode(id, x, y);
            case "set_variable" -> new SetVariableNode(id, x, y);
            case "gyroscope" -> new GyroscopeNode(id, x, y);
            case "gyroscope_position" -> new GyroscopePositionNode(id, x, y);
            case "antenna_input" -> new AntennaInputNode(id, x, y);
            case "antenna_output" -> new AntennaOutputNode(id, x, y);
            case "jammer" -> new JammerNode(id, x, y);
            case "bool_viewer" -> new BoolViewerNode(id, x, y);
            case "number_viewer" -> new NumberViewerNode(id, x, y);
            case "text_viewer" -> new TextViewerNode(id, x, y);
            case "display_link" -> new DisplayLinkNode(id, x, y);
            case "text_split" -> new TextSplitNode(id, x, y);
            case "text_join" -> new TextJoinNode(id, x, y);
            case "text_speak" -> new TextSpeakNode(id, x, y);
            case "sound_play" -> new SoundPlayNode(id, x, y);
            default -> null;
        };
    }
}
