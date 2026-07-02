package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.ScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

public class CameraScreenNode extends AlgoNode {
    private static final Gson GSON = new Gson();
    private int screenIndex = 0;

    public CameraScreenNode(String id, double x, double y) {
        super(id, x, y);
    }

    public int getScreenIndex() { return screenIndex; }
    public void setScreenIndex(int index) { this.screenIndex = index; }

    @Override
    public String getType() { return "camera_screen"; }

    @Override
    public List<String> getInputPorts() { return List.of("gizmos_3d", "zoom", "pitch", "yaw"); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("screenIndex", screenIndex);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("screenIndex")) {
            screenIndex = tag.getInt("screenIndex");
        } else {
            screenIndex = 0;
        }
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() == null || context.getLevel() == null) {
            return null;
        }

        BlockPos computerPos = context.getPos();
        MainComputerBlockEntity computer = context.getComputer();
        net.minecraft.world.item.ItemStack cassette = computer.getCassette();

        Object zoomVal = inputValues.get("zoom");
        Object pitchVal = inputValues.get("pitch");
        Object yawVal = inputValues.get("yaw");

        boolean hasZoom = zoomVal != null && !zoomVal.equals("");
        boolean hasPitch = pitchVal != null && !pitchVal.equals("");
        boolean hasYaw = yawVal != null && !yawVal.equals("");

        double zoom = hasZoom ? toDouble(zoomVal) : 0;
        double pitch = hasPitch ? toDouble(pitchVal) : 0;
        double yaw = hasYaw ? toDouble(yawVal) : 0;

        JsonObject cameraObj = new JsonObject();
        cameraObj.addProperty("type", "camera_feed");
        cameraObj.addProperty("x", 0.0);
        cameraObj.addProperty("y", 0.0);
        cameraObj.addProperty("w", 100.0);
        cameraObj.addProperty("h", 100.0);
        cameraObj.addProperty("zoom", (int) zoom);
        cameraObj.addProperty("cx", computerPos.getX());
        cameraObj.addProperty("cy", computerPos.getY());
        cameraObj.addProperty("cz", computerPos.getZ());
        cameraObj.addProperty("res", 256);
        cameraObj.addProperty("layer", 0);

        Object gizmosInput = inputValues.get("gizmos_3d");
        String gizmosJson = (gizmosInput != null) ? String.valueOf(gizmosInput) : "[]";
        cameraObj.addProperty("gizmos", gizmosJson);

        boolean changed = false;
        if (!context.getLevel().isClientSide() && !cassette.isEmpty()) {
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

        JsonArray arr = new JsonArray();
        arr.add(cameraObj);
        String finalJson = GSON.toJson(arr);

        if (!context.getLevel().isClientSide()) {
            BlockPos screenPos = computer.getModulePos("screen_" + screenIndex);
            if (screenPos != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), screenPos);
                if (be instanceof ScreenBlockEntity screen) {
                    screen.setGizmosJson(finalJson);
                }
            }
        }

        return null;
    }
}
