package com.radiologistics.create.registry;

import com.radiologistics.create.Radiologistics;
import com.radiologistics.create.block.MainComputerBlock;
import com.radiologistics.create.block.RadioTransmitterBlock;
import com.radiologistics.create.block.RedstoneLinkModuleBlock;
import com.radiologistics.create.block.MemoryModuleBlock;
import com.radiologistics.create.block.GyroscopeSensorBlock;
import com.radiologistics.create.block.AntennaBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Radiologistics.MODID);

    public static final DeferredBlock<Block> MAIN_COMPUTER = BLOCKS.register("main_computer",
        () -> new MainComputerBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> RADIO_TRANSMITTER = BLOCKS.register("radio_transmitter",
        () -> new RadioTransmitterBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> REDSTONE_LINK_MODULE = BLOCKS.register("redstone_link_module",
        () -> new RedstoneLinkModuleBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> MEMORY_MODULE = BLOCKS.register("memory_module",
        () -> new MemoryModuleBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> GYROSCOPE_SENSOR = BLOCKS.register("gyroscope_sensor",
        () -> new GyroscopeSensorBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> ANTENNA = BLOCKS.register("antenna",
        () -> new AntennaBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> JAMMER = BLOCKS.register("jammer",
        () -> new com.radiologistics.create.block.JammerBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> AUDIO_MODULE = BLOCKS.register("audio_module",
        () -> new com.radiologistics.create.block.AudioModuleBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<Block> TRANSPARENT_SCREEN = BLOCKS.register("transparent_screen",
        () -> new com.radiologistics.create.block.ScreenBlock(BlockBehaviour.Properties.of()
            .strength(3.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()));
}
