package com.radiologistics.create.block;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.radiologistics.create.gui.HelmetOverlayRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class ScreenBlockEntityRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
    private final Font font;

    private static final RenderType SCREEN_GUI_RENDER_TYPE = RenderType.create(
            "screen_gui",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                    .setTransparencyState(new net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard("translucent_transparency", () -> {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    }, () -> {
                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    }))
                    .setDepthTestState(new net.minecraft.client.renderer.RenderStateShard.DepthTestStateShard("lequal", 515))
                    .createCompositeState(false)
    );

    private static final double CANVAS = 100.0;

    private static final float SCALE = 0.0095f;

    public ScreenBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    private java.util.Set<BlockPos> findConnectedScreens(net.minecraft.world.level.Level level, BlockPos startPos, net.minecraft.core.Direction facing) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos curr = queue.poll();
            java.util.List<BlockPos> neighbors = new java.util.ArrayList<>();
            neighbors.add(curr.above());
            neighbors.add(curr.below());
            if (facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH) {
                neighbors.add(curr.east());
                neighbors.add(curr.west());
            } else {
                neighbors.add(curr.south());
                neighbors.add(curr.north());
            }

            for (BlockPos neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    net.minecraft.world.level.block.state.BlockState nState = level.getBlockState(neighbor);
                    if (nState.getBlock() instanceof ScreenBlock) {
                        net.minecraft.core.Direction nFacing = nState.hasProperty(ScreenBlock.FACING)
                                ? nState.getValue(ScreenBlock.FACING)
                                : net.minecraft.core.Direction.NORTH;
                        if (nFacing == facing) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return visited;
    }

    @Override
    public void render(ScreenBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (com.radiologistics.create.gui.CameraFeedRenderer.isRenderingCamera) {
            return;
        }

        net.minecraft.world.level.block.state.BlockState state = blockEntity.getBlockState();
        net.minecraft.core.Direction facing = state.hasProperty(ScreenBlock.FACING)
                ? state.getValue(ScreenBlock.FACING)
                : net.minecraft.core.Direction.NORTH;

        java.util.Set<BlockPos> connected = findConnectedScreens(blockEntity.getLevel(), blockEntity.getBlockPos(), facing);

        BlockPos masterPos = null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : connected) {
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        if (facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH) {
            for (BlockPos pos : connected) {
                if (pos.getX() == minX && pos.getY() == minY) {
                    masterPos = pos;
                    break;
                }
            }
        } else {
            for (BlockPos pos : connected) {
                if (pos.getZ() == minZ && pos.getY() == minY) {
                    masterPos = pos;
                    break;
                }
            }
        }

        if (masterPos == null || !blockEntity.getBlockPos().equals(masterPos)) {
            return;
        }

        int width = (facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH)
                ? (maxX - minX + 1)
                : (maxZ - minZ + 1);
        int height = (maxY - minY + 1);

        String json = blockEntity.getGizmosJson();
        if (json == null || json.equals("[]") || json.isBlank()) {
            for (BlockPos pos : connected) {
                net.minecraft.world.level.block.entity.BlockEntity be = BaseModuleBlock.resolveBlockEntity(blockEntity.getLevel(), pos);
                if (be instanceof ScreenBlockEntity sbe) {
                    String sJson = sbe.getGizmosJson();
                    if (sJson != null && !sJson.equals("[]") && !sJson.isBlank()) {
                        json = sJson;
                        break;
                    }
                }
            }
        }

        if (json == null || json.equals("[]") || json.isBlank()) return;

        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;
            JsonArray arr = root.getAsJsonArray();
            if (arr.isEmpty()) return;

            double diffX, diffY, diffZ;
            if (facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH) {
                diffX = width * 0.5;
                diffY = height * 0.5;
                diffZ = 0.5;
            } else {
                diffX = 0.5;
                diffY = height * 0.5;
                diffZ = width * 0.5;
            }

            double centerX = masterPos.getX() + diffX;
            double centerY = masterPos.getY() + diffY;
            double centerZ = masterPos.getZ() + diffZ;
            Vec3 sCenter = new Vec3(centerX, centerY, centerZ);

            Vec3 normal = Vec3.ZERO;
            switch (facing) {
                case NORTH -> normal = new Vec3(0, 0, -1);
                case SOUTH -> normal = new Vec3(0, 0, 1);
                case WEST  -> normal = new Vec3(-1, 0, 0);
                case EAST  -> normal = new Vec3(1, 0, 0);
                default    -> {}
            }

            Minecraft mc = Minecraft.getInstance();
            Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
            double dScreen = camPos.subtract(sCenter).dot(normal);

            MultiBufferSource.BufferSource buffer = bufferSource instanceof MultiBufferSource.BufferSource bs
                    ? bs : Minecraft.getInstance().renderBuffers().bufferSource();

            renderFace(arr, poseStack, buffer, facing, false, blockEntity, 0xF000F0, width, height);
            renderFace(arr, poseStack, buffer, facing, true, blockEntity, 0xF000F0, width, height);

        } catch (Exception e) {
            com.radiologistics.create.Radiologistics.LOGGER.error("Error rendering screen block entity", e);
        }
    }

    private void renderProjected3DGizmos(JsonObject obj, GuiGraphics g, Font font, ScreenBlockEntity blockEntity,
                                         net.minecraft.core.Direction facing, int width, int height, boolean backFace, PoseStack screenCenterPoseStack) {
        double wx = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double wy = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double wz = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        JsonArray subGizmos = obj.has("gizmos") && obj.get("gizmos").isJsonArray()
                ? obj.get("gizmos").getAsJsonArray() : new JsonArray();

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        org.joml.Matrix4f poseMat = new org.joml.Matrix4f(screenCenterPoseStack.last().pose());

        org.joml.Vector4f centerVec = new org.joml.Vector4f(0, 0, 0, 1);
        poseMat.transform(centerVec);
        Vec3 sCenterCameraRelative = new Vec3(centerVec.x(), centerVec.y(), centerVec.z());

        org.joml.Vector4f uVec = new org.joml.Vector4f(1, 0, 0, 0);
        poseMat.transform(uVec);
        Vec3 uDir = new Vec3(uVec.x(), uVec.y(), uVec.z()).normalize();

        org.joml.Vector4f vVec = new org.joml.Vector4f(0, 1, 0, 0);
        poseMat.transform(vVec);
        Vec3 vDir = new Vec3(vVec.x(), vVec.y(), vVec.z()).normalize();

        org.joml.Vector4f nVec = new org.joml.Vector4f(0, 0, 1, 0);
        poseMat.transform(nVec);
        Vec3 normal = new Vec3(nVec.x(), nVec.y(), nVec.z()).normalize();

        Vec3 targetPos = new Vec3(wx, wy, wz);
        Vec3 rayDir = targetPos.subtract(camPos);

        double dScreen = sCenterCameraRelative.scale(-1.0).dot(normal);
        double dTarget = rayDir.dot(normal);

        if (Math.abs(dTarget) < 1e-5) return;
        double t = -dScreen / dTarget;
        if (t <= 0) return;

        Vec3 intersectPos = rayDir.scale(t);
        Vec3 offset = intersectPos.subtract(sCenterCameraRelative);

        double uBlocks = offset.dot(uDir);
        double vBlocks = offset.dot(vDir);

        double uCanvas = (backFace ? (0.5 - uBlocks / width) : (0.5 + uBlocks / width)) * CANVAS;
        double vCanvas = (0.5 - vBlocks / height) * CANVAS;

        if (uCanvas < -200 || uCanvas > 300 || vCanvas < -200 || vCanvas > 300) return;

        double minX = -uCanvas / t;
        double maxX = (100.0 - uCanvas) / t;
        double minY = -vCanvas / t;
        double maxY = (100.0 - vCanvas) / t;

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 worldUpOnPlane = worldUp.subtract(normal.scale(worldUp.dot(normal)));
        double length = worldUpOnPlane.length();
        float rollCorrection = 0.0f;
        if (length > 1e-4) {
            Vec3 vUpright = worldUpOnPlane.scale(1.0 / length);
            double x = vUpright.dot(uDir);
            double y = vUpright.dot(vDir);

            double xGui = x;
            double yGui = -y;

            double theta = Math.atan2(yGui, xGui) + Math.PI / 2.0;
            rollCorrection = (float) theta;
        }

        g.pose().pushPose();
        g.pose().translate(uCanvas, vCanvas, 0);
        if (Math.abs(rollCorrection) > 1e-4) {
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(rollCorrection));
        }

        g.pose().scale((float) t, (float) t, 1.0f);

        for (JsonElement subItem : subGizmos) {
            if (!subItem.isJsonObject()) continue;
            JsonObject subObj = subItem.getAsJsonObject();
            String subType = subObj.has("type") ? subObj.get("type").getAsString() : "2d";
            if (subType.equals("2d")) {
                HelmetOverlayRenderer.render2D(g, font, subObj, minX, maxX, minY, maxY, SCREEN_GUI_RENDER_TYPE);
            }
        }

        g.pose().popPose();
    }

    private void renderFace(JsonArray arr, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                            net.minecraft.core.Direction facing, boolean backFace, ScreenBlockEntity blockEntity, int packedLight,
                            int width, int height) {
        poseStack.pushPose();
        try {
            double diffX, diffY, diffZ;
            if (facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH) {
                diffX = width * 0.5;
                diffY = height * 0.5;
                diffZ = 0.5;
            } else {
                diffX = 0.5;
                diffY = height * 0.5;
                diffZ = width * 0.5;
            }

            poseStack.translate(diffX, diffY, diffZ);

            switch (facing) {
                case SOUTH -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                case WEST  -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                case EAST  -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
                default    -> {}
            }

            if (backFace) {
                poseStack.translate(0, 0, 0.066);
            } else {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                poseStack.translate(0, 0, 0.066);
            }

            PoseStack centerPose = new PoseStack();
            centerPose.last().pose().set(poseStack.last().pose());
            centerPose.last().normal().set(poseStack.last().normal());

            float scaleX = (width - 0.05f) / 100.0f;
            float scaleY = (height - 0.05f) / 100.0f;
            poseStack.scale(scaleX, -scaleY, SCALE);
            poseStack.translate(-CANVAS / 2.0, -CANVAS / 2.0, 0);

            GuiGraphics g = new GuiGraphics(Minecraft.getInstance(), buffer);
            g.pose().pushPose();
            try {
                g.pose().mulPose(poseStack.last().pose());

                boolean isTransparent = blockEntity.getBlockState().is(com.radiologistics.create.registry.ModBlocks.TRANSPARENT_SCREEN.get());
                double zOffset = isTransparent ? -7.0 : 0.05;

                java.util.List<JsonObject> sorted = sortGizmos(arr);
                for (JsonObject obj : sorted) {
                    String type = obj.has("type") ? obj.get("type").getAsString() : "2d";
                    if (type.equals("2d")) {
                        g.pose().pushPose();
                        try {
                            g.pose().translate(0.0, 0.0, zOffset);
                            HelmetOverlayRenderer.render2D(g, font, obj, 0.0, CANVAS, 0.0, CANVAS, SCREEN_GUI_RENDER_TYPE);
                        } finally {
                            g.pose().popPose();
                        }
                    } else if (type.equals("3d")) {
                        g.pose().pushPose();
                        try {
                            g.pose().translate(0.0, 0.0, zOffset);
                            renderProjected3DGizmos(obj, g, font, blockEntity, facing, width, height, backFace, centerPose);
                        } finally {
                            g.pose().popPose();
                        }
                    } else if (type.equals("camera_feed")) {
                        poseStack.pushPose();
                        try {

                            poseStack.translate(0, 0, -7.0f);
                            String cacheKey = "radiologistics_screen_feed_" + blockEntity.getBlockPos().getX() + "_" + blockEntity.getBlockPos().getY() + "_" + blockEntity.getBlockPos().getZ() + "_" + (backFace ? "back" : "front");
                            com.radiologistics.create.gui.CameraFeedRenderer.render(obj, poseStack, buffer, cacheKey, packedLight, 0.0f, 100.0f, 0.0f, 100.0f);
                        } finally {
                            poseStack.popPose();
                        }
                    }
                }

            } finally {
                g.pose().popPose();
            }
            g.flush();
        } finally {
            poseStack.popPose();
        }
    }

    private static boolean intersectsBox(Vec3 start, Vec3 end, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;

        double tMin = 0.0;
        double tMax = 1.0;

        if (Math.abs(dx) < 1e-6) {
            if (start.x < minX || start.x > maxX) return false;
        } else {
            double t1 = (minX - start.x) / dx;
            double t2 = (maxX - start.x) / dx;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (Math.abs(dy) < 1e-6) {
            if (start.y < minY || start.y > maxY) return false;
        } else {
            double t1 = (minY - start.y) / dy;
            double t2 = (maxY - start.y) / dy;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (Math.abs(dz) < 1e-6) {
            if (start.z < minZ || start.z > maxZ) return false;
        } else {
            double t1 = (minZ - start.z) / dz;
            double t2 = (maxZ - start.z) / dz;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        return tMin <= tMax;
    }

    private static java.util.List<JsonObject> sortGizmos(JsonArray arr) {
        java.util.List<JsonObject> list = new java.util.ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                list.add(el.getAsJsonObject());
            }
        }
        list.sort((a, b) -> {
            int layerA = a.has("layer") && a.get("layer").isJsonPrimitive() ? a.get("layer").getAsInt() : 0;
            int layerB = b.has("layer") && b.get("layer").isJsonPrimitive() ? b.get("layer").getAsInt() : 0;
            return Integer.compare(layerA, layerB);
        });
        return list;
    }
}
