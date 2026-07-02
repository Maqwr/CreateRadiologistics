package com.radiologistics.create.block;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public class SmartOpticalSensorRenderer implements BlockEntityRenderer<SmartOpticalSensorBlockEntity> {
    private static final net.minecraft.resources.ResourceLocation GLOW_TEXTURE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("radiologistics", "textures/block/smart_optical_sensor_glow.png");

    public SmartOpticalSensorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SmartOpticalSensorBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        poseStack.pushPose();

        poseStack.translate(0.5f, 0.5f, 0.5f);

        net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
        net.minecraft.core.Direction facing = state.hasProperty(SmartOpticalSensorBlock.FACING) ? state.getValue(SmartOpticalSensorBlock.FACING) : net.minecraft.core.Direction.UP;

        switch (facing) {
            case DOWN:
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
                break;
            case NORTH:
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
                break;
            case SOUTH:
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                break;
            case WEST:
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90f));
                break;
            case EAST:
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90f));
                break;
            case UP:
            default:
                break;
        }

        VertexConsumer glowBuilder = buffer.getBuffer(RenderType.eyes(GLOW_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        float xMin = -0.375f;
        float xMax = 0.375f;
        float zMin = -0.375f;
        float zMax = 0.375f;
        float yPos = 0.568f;

        float uMin = 0.0f;
        float uMax = 1.0f;
        float vMin = 0.0f;
        float vMax = 1.0f;

        glowBuilder.addVertex(matrix, xMin, yPos, zMax).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(uMin, vMin).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setUv2(15, 15).setNormal(0.0f, 1.0f, 0.0f);
        glowBuilder.addVertex(matrix, xMax, yPos, zMax).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(uMax, vMin).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setUv2(15, 15).setNormal(0.0f, 1.0f, 0.0f);
        glowBuilder.addVertex(matrix, xMax, yPos, zMin).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(uMax, vMax).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setUv2(15, 15).setNormal(0.0f, 1.0f, 0.0f);
        glowBuilder.addVertex(matrix, xMin, yPos, zMin).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(uMin, vMax).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setUv2(15, 15).setNormal(0.0f, 1.0f, 0.0f);

        float dist = be.getTargetDistance();
        float startY = 0.5f;
        float endY = dist;
        boolean powered = state.hasProperty(SmartOpticalSensorBlock.POWERED) && state.getValue(SmartOpticalSensorBlock.POWERED);
        if (powered && endY > startY && dist > 0.05f) {
            VertexConsumer builder = buffer.getBuffer(RenderType.lightning());

            float r = 1.0f;
            float g = 0.0f;
            float b = 0.0f;
            float a = 0.9f;

            float size = 0.04f;
            float cy = endY;

            builder.addVertex(matrix, -size, cy - size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy - size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy - size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy - size, size).setColor(r, g, b, a);

            builder.addVertex(matrix, -size, cy + size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy + size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy + size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy + size, -size).setColor(r, g, b, a);

            builder.addVertex(matrix, -size, cy - size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy + size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy + size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy - size, -size).setColor(r, g, b, a);

            builder.addVertex(matrix, size, cy - size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy + size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy + size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy - size, size).setColor(r, g, b, a);

            builder.addVertex(matrix, -size, cy - size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy + size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy + size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, -size, cy - size, -size).setColor(r, g, b, a);

            builder.addVertex(matrix, size, cy - size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy + size, -size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy + size, size).setColor(r, g, b, a);
            builder.addVertex(matrix, size, cy - size, size).setColor(r, g, b, a);
        }

        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SmartOpticalSensorBlockEntity be) {
        net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
        if (state.hasProperty(SmartOpticalSensorBlock.FACING)) {
            net.minecraft.core.Direction facing = state.getValue(SmartOpticalSensorBlock.FACING);
            net.minecraft.core.BlockPos pos = be.getBlockPos();
            net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            net.minecraft.world.phys.Vec3 end = start.add(new net.minecraft.world.phys.Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()).scale(be.getTargetDistance()));
            return new net.minecraft.world.phys.AABB(start, end).inflate(1.0);
        }
        return new net.minecraft.world.phys.AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRenderOffScreen(SmartOpticalSensorBlockEntity be) {
        return true;
    }
}
