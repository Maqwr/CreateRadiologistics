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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

public class GyroscopeSensorRenderer implements BlockEntityRenderer<GyroscopeSensorBlockEntity> {
    private static final ModelResourceLocation GYRO_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("radiologistics", "block/gyroscope_sensor_gyro"));

    public GyroscopeSensorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GyroscopeSensorBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        poseStack.pushPose();

        // Translate to pivot point of the gyro [8, 6, 8]
        poseStack.translate(8f / 16f, 6f / 16f, 8f / 16f);

        // Orient based on the block's facing direction
        BlockState state = be.getBlockState();
        if (state.hasProperty(GyroscopeSensorBlock.FACING)) {
            Direction facing = state.getValue(GyroscopeSensorBlock.FACING);
            float yRot = -facing.toYRot();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yRot));
        }

        // Apply dynamic Pitch and Yaw rotations
        float[] rot = be.getContraptionRotation();
        float pitchX = rot[0];
        float pitchZ = rot[1];
        float yaw = rot[2];

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitchX));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(pitchZ));

        // Translate back
        poseStack.translate(-8f / 16f, -6f / 16f, -8f / 16f);

        // Render model
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(GYRO_MODEL);
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            poseStack.last(),
            consumer,
            state,
            model,
            1.0f, 1.0f, 1.0f,
            combinedLight,
            combinedOverlay
        );

        poseStack.popPose();
    }
}
