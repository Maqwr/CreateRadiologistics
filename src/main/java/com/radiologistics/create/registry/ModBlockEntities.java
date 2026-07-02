package com.radiologistics.create.registry;

import com.radiologistics.create.Radiologistics;
import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.RadioTransmitterBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Radiologistics.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MainComputerBlockEntity>> MAIN_COMPUTER =
        BLOCK_ENTITY_TYPES.register("main_computer",
            () -> BlockEntityType.Builder.of(MainComputerBlockEntity::new, ModBlocks.MAIN_COMPUTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioTransmitterBlockEntity>> RADIO_TRANSMITTER =
        BLOCK_ENTITY_TYPES.register("radio_transmitter",
            () -> BlockEntityType.Builder.of(RadioTransmitterBlockEntity::new, ModBlocks.RADIO_TRANSMITTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.RedstoneLinkModuleBlockEntity>> REDSTONE_LINK_MODULE =
        BLOCK_ENTITY_TYPES.register("redstone_link_module",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.RedstoneLinkModuleBlockEntity::new, ModBlocks.REDSTONE_LINK_MODULE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.MemoryModuleBlockEntity>> MEMORY_MODULE =
        BLOCK_ENTITY_TYPES.register("memory_module",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.MemoryModuleBlockEntity::new, ModBlocks.MEMORY_MODULE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.GyroscopeSensorBlockEntity>> GYROSCOPE_SENSOR =
        BLOCK_ENTITY_TYPES.register("gyroscope_sensor",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.GyroscopeSensorBlockEntity::new, ModBlocks.GYROSCOPE_SENSOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.AntennaBlockEntity>> ANTENNA =
        BLOCK_ENTITY_TYPES.register("antenna",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.AntennaBlockEntity::new, ModBlocks.ANTENNA.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.JammerBlockEntity>> JAMMER =
        BLOCK_ENTITY_TYPES.register("jammer",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.JammerBlockEntity::new, ModBlocks.JAMMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.AudioModuleBlockEntity>> AUDIO_MODULE =
        BLOCK_ENTITY_TYPES.register("audio_module",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.AudioModuleBlockEntity::new, ModBlocks.AUDIO_MODULE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.ScreenBlockEntity>> TRANSPARENT_SCREEN =
        BLOCK_ENTITY_TYPES.register("transparent_screen",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.ScreenBlockEntity::new, ModBlocks.TRANSPARENT_SCREEN.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.ServoMotorBlockEntity>> SERVO_MOTOR =
        BLOCK_ENTITY_TYPES.register("servo_motor",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.ServoMotorBlockEntity::new, ModBlocks.SERVO_MOTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.radiologistics.create.block.SmartOpticalSensorBlockEntity>> SMART_OPTICAL_SENSOR =
        BLOCK_ENTITY_TYPES.register("smart_optical_sensor",
            () -> BlockEntityType.Builder.of(com.radiologistics.create.block.SmartOpticalSensorBlockEntity::new, ModBlocks.SMART_OPTICAL_SENSOR.get()).build(null));
}
