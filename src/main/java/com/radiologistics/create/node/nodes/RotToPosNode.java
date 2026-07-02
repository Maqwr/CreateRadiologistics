package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public class RotToPosNode extends AlgoNode {
    public RotToPosNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "rot_to_pos"; }

    @Override
    public List<String> getInputPorts() { return List.of("yaw", "pitch", "distance", "x", "y", "z"); }

    @Override
    public List<String> getOutputPorts() { return List.of("x_out", "y_out", "z_out"); }

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
        double yaw = toDouble(inputValues.get("yaw"));
        double pitch = toDouble(inputValues.get("pitch"));
        double distance = toDouble(inputValues.get("distance"));
        double x = toDouble(inputValues.get("x"));
        double y = toDouble(inputValues.get("y"));
        double z = toDouble(inputValues.get("z"));

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double dx = -Math.sin(yawRad) * Math.cos(pitchRad) * distance;
        double dz = Math.cos(yawRad) * Math.cos(pitchRad) * distance;
        double dy = -Math.sin(pitchRad) * distance;

        if (outputPort.equals("x_out")) {
            double resX = x + dx;
            resX = Math.round(resX * 1000000.0) / 1000000.0;
            if (resX == Math.floor(resX) && !Double.isInfinite(resX)) {
                return (long) resX;
            }
            return resX;
        } else if (outputPort.equals("y_out")) {
            double resY = y + dy;
            resY = Math.round(resY * 1000000.0) / 1000000.0;
            if (resY == Math.floor(resY) && !Double.isInfinite(resY)) {
                return (long) resY;
            }
            return resY;
        } else if (outputPort.equals("z_out")) {
            double resZ = z + dz;
            resZ = Math.round(resZ * 1000000.0) / 1000000.0;
            if (resZ == Math.floor(resZ) && !Double.isInfinite(resZ)) {
                return (long) resZ;
            }
            return resZ;
        }
        return 0.0;
    }
}
