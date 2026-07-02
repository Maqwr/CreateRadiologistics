package com.radiologistics.create.block;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class ServoMotorRenderer implements BlockEntityRenderer<ServoMotorBlockEntity> {
    private static final ModelResourceLocation HORN_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("radiologistics", "block/servo_motor_horn"));

    public ServoMotorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ServoMotorBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        poseStack.pushPose();

        net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
        if (state.hasProperty(ServoMotorBlock.FACING)) {
            net.minecraft.core.Direction facing = state.getValue(ServoMotorBlock.FACING);
            net.minecraft.core.Direction.Axis axis = facing.getAxis();
            net.minecraft.world.level.block.state.BlockState shaftState = com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.shaft(axis);

            poseStack.pushPose();
            float inputOffset = (facing == net.minecraft.core.Direction.WEST || facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.DOWN) ? 0.3125f : -0.3125f;
            if (axis == net.minecraft.core.Direction.Axis.X) {
                poseStack.translate(inputOffset, 0, 0);
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(0.375f, 1.0f, 1.0f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
            } else if (axis == net.minecraft.core.Direction.Axis.Y) {
                poseStack.translate(0, inputOffset, 0);
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(1.0f, 0.375f, 1.0f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
            } else {
                poseStack.translate(0, 0, inputOffset);
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(1.0f, 1.0f, 0.375f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
            }
            com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.renderRotatingKineticBlock(
                be, shaftState, poseStack, buffer.getBuffer(RenderType.solid()), combinedLight
            );
            poseStack.popPose();

            poseStack.pushPose();
            float outputOffset = -inputOffset;
            if (axis == net.minecraft.core.Direction.Axis.X) {
                poseStack.translate(outputOffset, 0, 0);
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(0.375f, 1.0f, 1.0f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
            } else if (axis == net.minecraft.core.Direction.Axis.Y) {
                poseStack.translate(0, outputOffset, 0);
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(1.0f, 0.375f, 1.0f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
            } else {
                poseStack.translate(0, 0, outputOffset);
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(1.0f, 1.0f, 0.375f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);
            }

            float originalSpeed = be.getSpeed();
            float outputSpeed = be.getSpeed() * be.getRotationSpeedModifier(facing);
            be.setSpeed(outputSpeed);

            com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.renderRotatingKineticBlock(
                be, shaftState, poseStack, buffer.getBuffer(RenderType.solid()), combinedLight
            );

            be.setSpeed(originalSpeed);
            poseStack.popPose();

            poseStack.translate(0.5f, 0.5f, 0.5f);
            switch (facing) {
                case DOWN:
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
                    break;
                case NORTH:
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                    break;
                case SOUTH:
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                    break;
                case EAST:
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270f));
                    break;
                case WEST:
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
                    break;
                case UP:
                default:
                    break;
            }
            poseStack.translate(-0.5f, -0.5f, -0.5f);
        }

        poseStack.popPose();
    }
}
