package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class PosToRotNode extends AlgoNode {
    public PosToRotNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "pos_to_rot"; }

    @Override
    public List<String> getInputPorts() { return List.of("x", "y", "z", "x_end", "y_end", "z_end"); }

    @Override
    public List<String> getOutputPorts() { return List.of("yaw", "pitch"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        double x = toDouble(inputValues.get("x"));
        double y = toDouble(inputValues.get("y"));
        double z = toDouble(inputValues.get("z"));
        double xEnd = toDouble(inputValues.get("x_end"));
        double yEnd = toDouble(inputValues.get("y_end"));
        double zEnd = toDouble(inputValues.get("z_end"));

        double dx = xEnd - x;
        double dy = yEnd - y;
        double dz = zEnd - z;

        if (outputPort.equals("yaw")) {
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            if (yaw == Math.floor(yaw) && !Double.isInfinite(yaw)) {
                return (long) yaw;
            }
            return yaw;
        } else if (outputPort.equals("pitch")) {
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            double pitch = Math.toDegrees(Math.atan2(-dy, horizontalDistance));
            if (pitch == Math.floor(pitch) && !Double.isInfinite(pitch)) {
                return (long) pitch;
            }
            return pitch;
        }
        return 0.0;
    }
}
