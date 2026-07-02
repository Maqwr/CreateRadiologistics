package com.radiologistics.create.ponder;

import com.radiologistics.create.Radiologistics;
import com.radiologistics.create.registry.ModItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class RadiologisticsPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return Radiologistics.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(ModItems.RADIO_TRANSMITTER.getId(), "radio_transmitter", RadiologisticsPonderScenes::radioTransmitterScene);
        helper.addStoryBoard(ModItems.MAIN_COMPUTER.getId(), "main_computer", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.REDSTONE_LINK_MODULE.getId(), "redstone_link_module", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.MEMORY_MODULE.getId(), "memory_module", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.GYROSCOPE_SENSOR.getId(), "gyroscope_sensor", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.ANTENNA.getId(), "antenna", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.JAMMER.getId(), "jammer", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.AUDIO_MODULE.getId(), "audio_module", RadiologisticsPonderScenes::networkScene);
        helper.addStoryBoard(ModItems.TRANSPARENT_SCREEN.getId(), "main_computer", RadiologisticsPonderScenes::screenScene);
        helper.addStoryBoard(ModItems.PILOT_HELMET.getId(), "main_computer", RadiologisticsPonderScenes::helmetScene);
        helper.addStoryBoard(ModItems.SERVO_MOTOR.getId(), "servo_motor", RadiologisticsPonderScenes::servoMotorScene);
        helper.addStoryBoard(ModItems.SMART_OPTICAL_SENSOR.getId(), "smart_optical_sensor", RadiologisticsPonderScenes::smartOpticalSensorScene);
    }
}
