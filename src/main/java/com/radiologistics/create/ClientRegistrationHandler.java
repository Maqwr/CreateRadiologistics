package com.radiologistics.create;

import com.radiologistics.create.block.RadioTransmitterRenderer;
import com.radiologistics.create.block.GyroscopeSensorRenderer;
import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = Radiologistics.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientRegistrationHandler {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RADIO_TRANSMITTER.get(), RadioTransmitterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GYROSCOPE_SENSOR.get(), GyroscopeSensorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSPARENT_SCREEN.get(), com.radiologistics.create.block.ScreenBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "block/radio_transmitter_click")));
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "block/gyroscope_sensor_gyro")));
    }

    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new com.radiologistics.create.ponder.RadiologisticsPonderPlugin());
        com.radiologistics.create.compat.VistaIntegrationHelper.disableVistaDebugLines();
    }
}
