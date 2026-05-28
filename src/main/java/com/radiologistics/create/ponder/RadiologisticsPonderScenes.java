package com.radiologistics.create.ponder;

import com.radiologistics.create.registry.ModBlocks;
import com.radiologistics.create.block.AntennaBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class RadiologisticsPonderScenes {

    public static void radioTransmitterScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radio_transmitter", "Wireless Radio Transmitter");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos center = util.grid().at(2, 1, 2);
        scene.world().setBlock(center, ModBlocks.RADIO_TRANSMITTER.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(center), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("The Radio Transmitter broadcasts wireless signals when powered by redstone.")
            .pointAt(util.vector().topOf(center));
        scene.idle(90);

        scene.addKeyframe();
        scene.overlay().showControls(util.vector().topOf(center), Pointing.DOWN, 40)
            .rightClick();
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Right-click it to configure the channel (0-255) and transmission message.")
            .pointAt(util.vector().topOf(center));
        scene.idle(90);

        // Show targeting connection
        scene.addKeyframe();
        scene.overlay().showControls(util.vector().topOf(center), Pointing.DOWN, 40)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.ANTENNA.get()));
        scene.idle(20);

        // Show selection outline in Ponder
        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, center, new net.minecraft.world.phys.AABB(center), 80);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("First, right-click the transmitter with the antenna in hand to target it.")
            .pointAt(util.vector().topOf(center));
        scene.idle(95);

        // Place Base Antenna
        BlockPos adjacent = center.east();
        scene.world().setBlock(adjacent, ModBlocks.ANTENNA.get().defaultBlockState().setValue(AntennaBlock.TYPE, AntennaBlock.AntennaSegmentType.BASE), true);
        scene.world().showSection(util.select().position(adjacent), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Then place the antenna. It will connect to the targeted transmitter.")
            .pointAt(util.vector().topOf(adjacent));
        scene.idle(95);

        // Stack more segments to show multiblock height summing
        scene.addKeyframe();
        BlockPos midPos = adjacent.above();
        BlockPos topPos = midPos.above();

        scene.world().setBlock(midPos, ModBlocks.ANTENNA.get().defaultBlockState().setValue(AntennaBlock.TYPE, AntennaBlock.AntennaSegmentType.MIDDLE), true);
        scene.world().showSection(util.select().position(midPos), Direction.DOWN);
        scene.idle(15);

        scene.world().setBlock(topPos, ModBlocks.ANTENNA.get().defaultBlockState().setValue(AntennaBlock.TYPE, AntennaBlock.AntennaSegmentType.TOP), true);
        scene.world().showSection(util.select().position(topPos), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(100)
            .text("Stacking multiple antenna segments vertically sums their height to boost transmission range.")
            .pointAt(util.vector().blockSurface(topPos, Direction.WEST));
        scene.idle(110);
    }

    public static void networkScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("network_scene", "Computer Network Setup");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // 1. Place the Computer
        BlockPos computerPos = util.grid().at(2, 1, 1);
        scene.world().setBlock(computerPos, ModBlocks.MAIN_COMPUTER.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(computerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("First, place the Main Computer. It is the CPU of your network.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        // 2. Link Module
        scene.addKeyframe();
        BlockPos modulePos = util.grid().at(2, 1, 3);
        scene.overlay().showControls(util.vector().topOf(computerPos), Pointing.DOWN, 50)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.REDSTONE_LINK_MODULE.get()));
        scene.idle(20);

        // Show selection outline in Ponder
        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, computerPos, new net.minecraft.world.phys.AABB(computerPos), 80);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Right-click the computer with the module in hand to set it as a target.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        // Place Module
        scene.world().setBlock(modulePos, ModBlocks.REDSTONE_LINK_MODULE.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(modulePos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("Then place the module block. It will connect to the targeted computer.")
            .pointAt(util.vector().topOf(modulePos));
        scene.idle(95);

        // 3. Place Display Link and Network Controller
        scene.addKeyframe();
        BlockPos displayLinkPos = util.grid().at(1, 1, 2);
        BlockPos networkControllerPos = util.grid().at(3, 1, 2);

        // Get Display Link state (safely, standing on floor)
        net.minecraft.world.level.block.state.BlockState displayLinkState = 
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("create:display_link"))
                .map(block -> {
                    var state = block.defaultBlockState();
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, 
                            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
                    }
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH);
                    }
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP);
                    }
                    return state;
                })
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        scene.world().setBlock(displayLinkPos, displayLinkState, true);
        scene.world().showSection(util.select().position(displayLinkPos), Direction.DOWN);

        // Get Network Filterer state (safely, standing on floor)
        net.minecraft.world.level.block.state.BlockState filtererState = 
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("create_radar:network_filterer"))
                .map(block -> {
                    var state = block.defaultBlockState();
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP);
                    } else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH);
                    }
                    return state;
                })
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        scene.world().setBlock(networkControllerPos, filtererState, true);
        scene.world().showSection(util.select().position(networkControllerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(100)
            .text("You can also connect a Display Link and a Network Controller from Create Radars")
            .pointAt(util.vector().centerOf(displayLinkPos));
        scene.idle(115);
    }
}
