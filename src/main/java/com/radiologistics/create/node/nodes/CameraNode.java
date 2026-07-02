package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.MainComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

public class CameraNode extends AlgoNode {
    private static final Gson GSON = new Gson();

    public CameraNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "camera"; }

    @Override
    public List<String> getInputPorts() { return List.of("x", "y", "w", "h", "zoom", "pitch", "yaw", "layer", "gizmos"); }

    @Override
    public List<String> getOutputPorts() { return List.of("gizmos"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        JsonArray arr = new JsonArray();
        if (context.getComputer() == null || context.getLevel() == null) {
            return GSON.toJson(arr);
        }

        BlockPos computerPos = context.getPos();
        MainComputerBlockEntity computer = context.getComputer();
        net.minecraft.world.item.ItemStack cassette = computer.getCassette();
        if (cassette.isEmpty()) {
            return GSON.toJson(arr);
        }

        Object zoomVal = inputValues.get("zoom");
        Object pitchVal = inputValues.get("pitch");
        Object yawVal = inputValues.get("yaw");

        boolean hasZoom = zoomVal != null && !zoomVal.equals("");
        boolean hasPitch = pitchVal != null && !pitchVal.equals("");
        boolean hasYaw = yawVal != null && !yawVal.equals("");

        double screenX = toDouble(inputValues.get("x"));
        double screenY = toDouble(inputValues.get("y"));
        double screenW = toDouble(inputValues.get("w"));
        double screenH = toDouble(inputValues.get("h"));
        double zoom = hasZoom ? toDouble(zoomVal) : 0;
        double pitch = hasPitch ? toDouble(pitchVal) : 0;
        double yaw = hasYaw ? toDouble(yawVal) : 0;

        if (screenW <= 0) screenW = 100;
        if (screenH <= 0) screenH = 100;

        int grid = 256;

        Object layerVal = inputValues.get("layer");
        int layer = 0;
        if (layerVal != null && !layerVal.equals("")) {
            try {
                layer = (int) toDouble(layerVal);
            } catch (Exception ignored) {}
        }

        JsonObject cameraObj = new JsonObject();
        cameraObj.addProperty("type", "camera_feed");
        cameraObj.addProperty("x", screenX);
        cameraObj.addProperty("y", screenY);
        cameraObj.addProperty("w", screenW);
        cameraObj.addProperty("h", screenH);
        cameraObj.addProperty("zoom", (int) zoom);
        cameraObj.addProperty("cx", computerPos.getX());
        cameraObj.addProperty("cy", computerPos.getY());
        cameraObj.addProperty("cz", computerPos.getZ());
        cameraObj.addProperty("res", grid);
        cameraObj.addProperty("layer", layer);

        Object gizmosInput = inputValues.get("gizmos");
        String gizmosJson = (gizmosInput != null) ? String.valueOf(gizmosInput) : "[]";
        cameraObj.addProperty("gizmos", gizmosJson);

        boolean changed = false;
        if (!context.getLevel().isClientSide()) {
            try {

                java.util.UUID broadcastUUID = null;
                Class<?> vistaModClass = Class.forName("net.mehvahdjukaar.vista.VistaMod");
                java.util.function.Supplier<?> linkedFeedComponentSupplier = (java.util.function.Supplier<?>) vistaModClass.getField("LINKED_FEED_COMPONENT").get(null);
                net.minecraft.core.component.DataComponentType<?> componentType = (net.minecraft.core.component.DataComponentType<?>) linkedFeedComponentSupplier.get();
                broadcastUUID = (java.util.UUID) cassette.get(componentType);

                if (broadcastUUID != null) {
                    Class<?> broadcastManagerClass = Class.forName("net.mehvahdjukaar.vista.common.broadcast.BroadcastManager");
                    java.lang.reflect.Method getInstanceMethod = broadcastManagerClass.getMethod("getInstance", net.minecraft.world.level.Level.class);
                    Object broadcastManager = getInstanceMethod.invoke(null, context.getLevel());
                    if (broadcastManager != null) {
                        java.lang.reflect.Method getBroadcastMethod = broadcastManagerClass.getMethod("getBroadcast", java.util.UUID.class, boolean.class);
                        Object broadcastSource = getBroadcastMethod.invoke(broadcastManager, broadcastUUID, false);
                        if (broadcastSource != null && broadcastSource.getClass().getName().equals("net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity")) {

                            if (hasZoom) {
                                java.lang.reflect.Method setZoomMethod = broadcastSource.getClass().getMethod("setZoomLevel", int.class);
                                int maxZoom = 4;
                                try {
                                    java.lang.reflect.Field maxZoomField = broadcastSource.getClass().getField("MAX_ZOOM");
                                    maxZoom = maxZoomField.getInt(null);
                                } catch (Throwable ignored) {}
                                int clampedZoom = Math.min(Math.max(0, (int) zoom), maxZoom);
                                setZoomMethod.invoke(broadcastSource, clampedZoom);
                                changed = true;
                            } else {
                                try {
                                    java.lang.reflect.Method getZoomMethod = broadcastSource.getClass().getMethod("getZoomLevel");
                                    zoom = ((Number) getZoomMethod.invoke(broadcastSource)).doubleValue();
                                    cameraObj.addProperty("zoom", (int) zoom);
                                } catch (Throwable ignored) {}
                            }

                            if (hasPitch || hasYaw) {
                                float finalPitch = 0.0f;
                                float finalYaw = 0.0f;
                                try {
                                    java.lang.reflect.Method getOrientMethod = broadcastSource.getClass().getMethod("getLocalOrientation");
                                    org.joml.Quaternionf currentQ = (org.joml.Quaternionf) getOrientMethod.invoke(broadcastSource);
                                    if (currentQ != null) {
                                        org.joml.Vector3f angles = currentQ.getEulerAnglesYXZ(new org.joml.Vector3f());
                                        finalYaw = (float) Math.toDegrees(angles.y);
                                        finalPitch = (float) Math.toDegrees(angles.x);
                                    }
                                } catch (Throwable ignored) {}

                                if (hasPitch) {
                                    finalPitch = (float) pitch;
                                }
                                if (hasYaw) {
                                    finalYaw = (float) yaw;
                                }

                                org.joml.Quaternionf q = new org.joml.Quaternionf().rotationYXZ((float)Math.toRadians(finalYaw), (float)Math.toRadians(finalPitch), 0.0f);
                                java.lang.reflect.Method setLocalOrientMethod = broadcastSource.getClass().getMethod("setLocalOrientation", org.joml.Quaternionf.class);
                                setLocalOrientMethod.invoke(broadcastSource, q);
                                changed = true;
                            }

                            if (changed && broadcastSource instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                                be.setChanged();
                                net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                                context.getLevel().sendBlockUpdated(be.getBlockPos(), state, state, 3);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            if (!cassette.isEmpty()) {
                net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) cassette.save(context.getLevel().registryAccess());
                cameraObj.addProperty("cassette_nbt", tag.toString());
            }
        } catch (Throwable ignored) {}

        arr.add(cameraObj);
        return GSON.toJson(arr);
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }
}
