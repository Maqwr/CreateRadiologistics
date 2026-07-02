package com.radiologistics.create.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiologistics.create.block.MainComputerBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CameraFeedRenderer {
    public static boolean isRenderingCamera = false;

    private static class CacheEntry {
        final net.minecraft.world.item.ItemStack cassette;
        final Object videoSource;

        CacheEntry(net.minecraft.world.item.ItemStack cassette, Object videoSource) {
            this.cassette = cassette;
            this.videoSource = videoSource;
        }
    }

    private static final Map<BlockPos, CacheEntry> SOURCE_CACHE = new HashMap<>();

    public static void render(JsonObject obj, PoseStack poseStack, MultiBufferSource bufferSource, String cacheKey, int packedLight, float minX, float maxX, float minY, float maxY) {
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double w = obj.has("w") ? obj.get("w").getAsDouble() : 100;
        double h = obj.has("h") ? obj.get("h").getAsDouble() : 100;
        int cx = obj.has("cx") ? obj.get("cx").getAsInt() : 0;
        int cy = obj.has("cy") ? obj.get("cy").getAsInt() : 0;
        int cz = obj.has("cz") ? obj.get("cz").getAsInt() : 0;
        int grid = obj.has("res") ? obj.get("res").getAsInt() : 256;
        int zoom = obj.has("zoom") ? obj.get("zoom").getAsInt() : 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos modulePos = new BlockPos(cx, cy, cz);
        net.minecraft.world.item.ItemStack cassette = net.minecraft.world.item.ItemStack.EMPTY;
        if (obj.has("cassette_nbt")) {
            try {
                String nbtStr = obj.get("cassette_nbt").getAsString();
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtStr);
                cassette = net.minecraft.world.item.ItemStack.parse(mc.level.registryAccess(), tag).orElse(net.minecraft.world.item.ItemStack.EMPTY);
            } catch (Throwable ignored) {}
        }
        if (cassette.isEmpty()) {
            net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(modulePos);
            if (be instanceof MainComputerBlockEntity computer) {
                cassette = computer.getCassette();
            }
        }
        if (cassette.isEmpty()) return;

        java.util.UUID broadcastUUID = null;
        try {
            Class<?> vistaModClass = Class.forName("net.mehvahdjukaar.vista.VistaMod");
            java.util.function.Supplier<?> linkedFeedComponentSupplier = (java.util.function.Supplier<?>) vistaModClass.getField("LINKED_FEED_COMPONENT").get(null);
            net.minecraft.core.component.DataComponentType<?> componentType = (net.minecraft.core.component.DataComponentType<?>) linkedFeedComponentSupplier.get();
            broadcastUUID = (java.util.UUID) cassette.get(componentType);
        } catch (Throwable ignored) {}

        try {
            Object videoSource = getVideoSource(modulePos, cassette);
            if (videoSource == null) return;

            int resolutionScale = 8;
            try {
                Class<?> clientConfigsClass = Class.forName("net.mehvahdjukaar.vista.configs.ClientConfigs");
                java.util.function.Supplier<Integer> supplier = (java.util.function.Supplier<Integer>) clientConfigsClass.getField("LIVE_FEED_RESOLUTION_SCALE").get(null);
                if (supplier != null) {
                    resolutionScale = supplier.get();
                }
            } catch (Throwable ignored) {}

            int tvX = Math.max(1, grid / resolutionScale);
            int tvY = Math.max(1, grid / resolutionScale);

            Class<?> vec2iClass = Class.forName("net.mehvahdjukaar.moonlight.api.util.math.Vec2i");
            Object tvSize = vec2iClass.getConstructor(int.class, int.class).newInstance(tvX, tvY);
            Object pxSize = vec2iClass.getConstructor(int.class, int.class).newInstance(grid, grid);

            Class<?> animStateClass = Class.forName("net.mehvahdjukaar.vista.common.tv.IntAnimationState");
            Object noAnim = animStateClass.getField("NO_ANIM").get(null);

            java.lang.reflect.Method getFrameBuilderMethod = videoSource.getClass().getMethod(
                "getVideoFrameBuilder",
                float.class,
                MultiBufferSource.class,
                boolean.class,
                vec2iClass,
                vec2iClass,
                int.class,
                boolean.class,
                animStateClass,
                animStateClass,
                boolean.class
            );

            float partialTicks = 0.0f;
            Object vertexConsumer = getFrameBuilderMethod.invoke(
                videoSource,
                partialTicks,
                bufferSource,
                true,
                tvSize,
                pxSize,
                packedLight,
                false,
                noAnim,
                noAnim,
                true
            );

            if (vertexConsumer instanceof VertexConsumer consumer) {
                boolean mirror = cacheKey != null && !cacheKey.endsWith("_back") && !cacheKey.equals("radiologistics_helmet_feed");
                drawClipsQuad(consumer, poseStack, x, y, w, h, packedLight, minX, maxX, minY, maxY, mirror);

                String camGizmos = obj.has("gizmos") ? obj.get("gizmos").getAsString() : "[]";
                if (camGizmos != null && !camGizmos.equals("[]") && !camGizmos.isBlank()) {
                        try {
                            JsonElement el = JsonParser.parseString(camGizmos);
                            if (el.isJsonArray()) {
                                JsonArray arr = el.getAsJsonArray();
                                MultiBufferSource.BufferSource buffer = bufferSource instanceof MultiBufferSource.BufferSource bs
                                        ? bs : Minecraft.getInstance().renderBuffers().bufferSource();
                                GuiGraphics g = new GuiGraphics(mc, buffer);
                                g.pose().pushPose();
                                g.pose().mulPose(poseStack.last().pose());
                                g.pose().translate(0, 0, 0.05f);

                                g.pose().translate(x, y, 0);
                                g.pose().scale((float)(w / 100.0), (float)(h / 100.0), 1.0f);

                                Object viewFinder = null;
                                if (broadcastUUID != null) {
                                    try {
                                        Class<?> broadcastManagerClass = Class.forName("net.mehvahdjukaar.vista.common.broadcast.BroadcastManager");
                                        java.lang.reflect.Method getInstanceMethod = broadcastManagerClass.getMethod("getInstance", net.minecraft.world.level.Level.class);
                                        Object broadcastManager = getInstanceMethod.invoke(null, mc.level);
                                        if (broadcastManager != null) {
                                            java.lang.reflect.Method getBroadcastMethod = broadcastManagerClass.getMethod("getBroadcast", java.util.UUID.class, boolean.class);
                                            viewFinder = getBroadcastMethod.invoke(broadcastManager, broadcastUUID, false);
                                        }
                                    } catch (Throwable ignored) {}
                                }

                                if (viewFinder != null) {
                                    for (JsonElement gizmoEl : arr) {
                                        if (gizmoEl.isJsonObject()) {
                                            JsonObject gizmoObj = gizmoEl.getAsJsonObject();
                                            String gType = gizmoObj.has("type") ? gizmoObj.get("type").getAsString() : "2d";
                                            if (gType.equals("3d")) {
                                                renderProjected3DGizmosForCamera(gizmoObj, g, mc.font, viewFinder, 100.0, 100.0, mirror);
                                            }
                                        }
                                    }
                                }

                                g.pose().popPose();
                                g.flush();
                            }
                        } catch (Throwable ignored) {}
                    }
                }
        } catch (Throwable ignored) {

        }
    }

    public static void drawClipsQuad(VertexConsumer vertexConsumer, PoseStack poseStack, double x, double y, double w, double h, int packedLight, float minX, float maxX, float minY, float maxY, boolean mirror) {
        float x1_clamped = Math.max(minX, Math.min(maxX, (float) x));
        float x2_clamped = Math.max(minX, Math.min(maxX, (float) (x + w)));
        float y1_clamped = Math.max(minY, Math.min(maxY, (float) y));
        float y2_clamped = Math.max(minY, Math.min(maxY, (float) (y + h)));

        if (x1_clamped >= x2_clamped || y1_clamped >= y2_clamped) {
            return;
        }

        float u1 = (x1_clamped - (float) x) / (float) w;
        float u2 = (x2_clamped - (float) x) / (float) w;
        if (mirror) {
            u1 = 1.0f - u1;
            u2 = 1.0f - u2;
        }
        float v1 = (y1_clamped - (float) y) / (float) h;
        float v2 = (y2_clamped - (float) y) / (float) h;

        int skyLight = packedLight & 65535;
        int blockLight = (packedLight >> 16) & 65535;

        PoseStack.Pose lastPose = poseStack.last();
        org.joml.Matrix3f normalMatrix = lastPose.normal();
        org.joml.Vector3f normal = normalMatrix.transform(new org.joml.Vector3f(0.0F, 0.0F, -1.0F));

        vert(vertexConsumer, poseStack, x2_clamped, y1_clamped, u2, v1, skyLight, blockLight, normal);
        vert(vertexConsumer, poseStack, x1_clamped, y1_clamped, u1, v1, skyLight, blockLight, normal);
        vert(vertexConsumer, poseStack, x1_clamped, y2_clamped, u1, v2, skyLight, blockLight, normal);
        vert(vertexConsumer, poseStack, x2_clamped, y2_clamped, u2, v2, skyLight, blockLight, normal);
    }

    private static void vert(VertexConsumer vertexConsumer, PoseStack poseStack, float x, float y, float u, float v, int skyLight, int blockLight, org.joml.Vector3f normal) {
        vertexConsumer.addVertex(poseStack.last().pose(), x, y, 0.0F);
        vertexConsumer.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        vertexConsumer.setUv(u, v);
        vertexConsumer.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        vertexConsumer.setUv2(skyLight, blockLight);
        vertexConsumer.setNormal(normal.x, normal.y, normal.z);
    }

    private static Object getVideoSource(BlockPos pos, net.minecraft.world.item.ItemStack currentCassette) {
        CacheEntry entry = SOURCE_CACHE.get(pos);
        if (entry == null || !net.minecraft.world.item.ItemStack.matches(entry.cassette, currentCassette)) {
            try {
                Class<?> videoSourceClass = Class.forName("net.mehvahdjukaar.vista.client.video_source.IVideoSource");
                java.lang.reflect.Method createMethod = videoSourceClass.getMethod("create", net.minecraft.world.item.ItemStack.class);
                Object videoSource = createMethod.invoke(null, currentCassette);
                if (videoSource != null) {
                    entry = new CacheEntry(currentCassette.copy(), videoSource);
                    SOURCE_CACHE.put(pos, entry);
                } else {
                    return null;
                }
            } catch (Throwable t) {
                return null;
            }
        }
        return entry.videoSource;
    }

    private static void renderProjected3DGizmosForCamera(JsonObject obj, GuiGraphics g, Font font, Object viewFinder, double w, double h, boolean mirror) {
        double wx = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double wy = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double wz = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        JsonArray subGizmos = obj.has("gizmos") && obj.get("gizmos").isJsonArray()
                ? obj.get("gizmos").getAsJsonArray() : new JsonArray();

        Vec3 camPos;
        try {
            camPos = (Vec3) viewFinder.getClass().getMethod("getGlobalPosition").invoke(viewFinder);
        } catch (Exception e) {
            return;
        }

        org.joml.Quaternionf camRot;
        try {
            camRot = (org.joml.Quaternionf) viewFinder.getClass().getMethod("getWorldOrientation").invoke(viewFinder);
        } catch (Exception e) {
            return;
        }

        Vec3 targetPos = new Vec3(wx, wy, wz);
        Vec3 D = targetPos.subtract(camPos);

        org.joml.Vector3f fwd = camRot.transform(new org.joml.Vector3f(0, 0, -1));
        org.joml.Vector3f rgt = camRot.transform(new org.joml.Vector3f(1, 0, 0));
        org.joml.Vector3f upVec = camRot.transform(new org.joml.Vector3f(0, 1, 0));

        double z_local = D.x * fwd.x + D.y * fwd.y + D.z * fwd.z;
        if (z_local <= 0.1) return;

        double x_local = D.x * rgt.x + D.y * rgt.y + D.z * rgt.z;
        double y_local = D.x * upVec.x + D.y * upVec.y + D.z * upVec.z;

        float fov = 70.0f;
        try {
            fov = ((Number) viewFinder.getClass().getMethod("getFOV").invoke(viewFinder)).floatValue();
        } catch (Exception ignored) {}

        double halfFovTan = Math.tan(Math.toRadians(fov) / 2.0);
        double u_screen = 0.5 + (x_local / z_local) / (2.0 * halfFovTan);
        if (mirror) {
            u_screen = 1.0 - u_screen;
        }
        double v_screen = 0.5 - (y_local / z_local) / (2.0 * halfFovTan);

        double uCanvas = u_screen * 100.0;
        double vCanvas = v_screen * 100.0;

        if (uCanvas < -200 || uCanvas > 300 || vCanvas < -200 || vCanvas > 300) return;

        double t = 1.0 / (z_local * halfFovTan);

        double minX = -uCanvas / t;
        double maxX = (100.0 - uCanvas) / t;
        double minY = -vCanvas / t;
        double maxY = (100.0 - vCanvas) / t;

        g.pose().pushPose();
        g.pose().translate(uCanvas, vCanvas, 0);
        g.pose().scale((float) t, (float) t, 1.0f);

        for (JsonElement subItem : subGizmos) {
            if (!subItem.isJsonObject()) continue;
            JsonObject subObj = subItem.getAsJsonObject();
            String subType = subObj.has("type") ? subObj.get("type").getAsString() : "2d";
            if (subType.equals("2d")) {
                HelmetOverlayRenderer.render2D(g, font, subObj, minX, maxX, minY, maxY);
            }
        }

        g.pose().popPose();
    }

    public static void clearCache() {
        SOURCE_CACHE.clear();
    }
}
