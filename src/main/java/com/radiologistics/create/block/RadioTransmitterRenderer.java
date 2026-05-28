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

public class RadioTransmitterRenderer implements BlockEntityRenderer<RadioTransmitterBlockEntity> {
    private static final ModelResourceLocation LEVER_MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("radiologistics", "block/radio_transmitter_click"));

    public RadioTransmitterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RadioTransmitterBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        poseStack.pushPose();

        net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
        if (state.hasProperty(RadioTransmitterBlock.FACING)) {
            net.minecraft.core.Direction facing = state.getValue(RadioTransmitterBlock.FACING);
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

        // Translate to pivot point of the lever [8, 6, 10]
        poseStack.translate(8f / 16f, 6f / 16f, 10f / 16f);

        // Apply rotation
        float rotation = be.getCurrentLeverRotation();
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));

        // Translate back
        poseStack.translate(-8f / 16f, -6f / 16f, -10f / 16f);

        // Render model
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(LEVER_MODEL);
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            poseStack.last(),
            consumer,
            be.getBlockState(),
            model,
            1.0f, 1.0f, 1.0f,
            combinedLight,
            combinedOverlay
        );

        poseStack.popPose();
    }
}
