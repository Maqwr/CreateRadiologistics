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

        scene.addKeyframe();
        scene.overlay().showControls(util.vector().topOf(center), Pointing.DOWN, 40)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.ANTENNA.get()));
        scene.idle(20);

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, center, new net.minecraft.world.phys.AABB(center), 80);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("First, right-click the transmitter with the antenna in hand to target it.")
            .pointAt(util.vector().topOf(center));
        scene.idle(95);

        BlockPos adjacent = center.east();
        scene.world().setBlock(adjacent, ModBlocks.ANTENNA.get().defaultBlockState().setValue(AntennaBlock.TYPE, AntennaBlock.AntennaSegmentType.BASE), true);
        scene.world().showSection(util.select().position(adjacent), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Then place the antenna. It will connect to the targeted transmitter.")
            .pointAt(util.vector().topOf(adjacent));
        scene.idle(95);

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

        BlockPos computerPos = util.grid().at(2, 1, 1);
        scene.world().setBlock(computerPos, ModBlocks.MAIN_COMPUTER.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(computerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("First, place the Main Computer. It is the CPU of your network.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        scene.addKeyframe();
        BlockPos modulePos = util.grid().at(2, 1, 3);
        scene.overlay().showControls(util.vector().topOf(computerPos), Pointing.DOWN, 50)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.REDSTONE_LINK_MODULE.get()));
        scene.idle(20);

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, computerPos, new net.minecraft.world.phys.AABB(computerPos), 80);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Right-click the computer with the module in hand to set it as a target.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        scene.world().setBlock(modulePos, ModBlocks.REDSTONE_LINK_MODULE.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(modulePos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("Then place the module block. It will connect to the targeted computer.")
            .pointAt(util.vector().topOf(modulePos));
        scene.idle(95);

        scene.addKeyframe();
        BlockPos displayLinkPos = util.grid().at(1, 1, 2);
        BlockPos networkControllerPos = util.grid().at(3, 1, 2);

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

        scene.overlay().showText(80)
            .text("You can also connect a Display Link and a Network Controller from Create Radars")
            .pointAt(util.vector().centerOf(displayLinkPos));
        scene.idle(85);

        scene.addKeyframe();
        BlockPos displayBoardPos = util.grid().at(1, 1, 4);
        BlockPos cannonMountPos = util.grid().at(3, 1, 4);

        net.minecraft.world.level.block.state.BlockState cannonMountState =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("createbigcannons:cannon_mount"))
                .map(block -> block.defaultBlockState())
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        net.minecraft.world.level.block.state.BlockState displayBoardState =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("create:display_board"))
                .map(block -> block.defaultBlockState())
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        scene.world().setBlock(displayBoardPos, displayBoardState, true);
        scene.world().showSection(util.select().position(displayBoardPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("Connect a Display Board to print dynamic train-station style board info.")
            .pointAt(util.vector().centerOf(displayBoardPos));
        scene.idle(85);

        scene.world().setBlock(cannonMountPos, cannonMountState, true);
        scene.world().showSection(util.select().position(cannonMountPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("Or hook up a Cannon Mount from Create Big Cannons to receive its current pitch and yaw angles.")
            .pointAt(util.vector().centerOf(cannonMountPos));
        scene.idle(85);
    }

    public static void screenScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("transparent_screen", "Using the Transparent Screen");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computerPos = util.grid().at(2, 1, 1);
        scene.world().setBlock(computerPos, ModBlocks.MAIN_COMPUTER.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(computerPos), Direction.DOWN);
        scene.idle(15);

        BlockPos screenPos = util.grid().at(2, 1, 3);
        scene.world().setBlock(screenPos, ModBlocks.TRANSPARENT_SCREEN.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(screenPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("The Transparent Screen outputs 2D/3D graphics, text, and camera feeds.")
            .pointAt(util.vector().topOf(screenPos));
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay().showControls(util.vector().topOf(computerPos), Pointing.DOWN, 40)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.WIRE.get()));
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(screenPos), Pointing.DOWN, 40)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.WIRE.get()));
        scene.idle(20);

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, screenPos, new net.minecraft.world.phys.AABB(screenPos), 80);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("Use a Wire to connect the Screen block directly to the Main Computer.")
            .pointAt(util.vector().centerOf(screenPos));
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay().showText(100)
            .text("Once linked, the computer's graph node layout will output graphics directly on the screen canvas!")
            .pointAt(util.vector().centerOf(screenPos));

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.FAST, screenPos, new net.minecraft.world.phys.AABB(screenPos).inflate(0.01), 100);
        scene.idle(115);
    }

    public static void helmetScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("pilot_helmet", "Using the Pilot Helmet HUD");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computerPos = util.grid().at(2, 1, 1);
        scene.world().setBlock(computerPos, ModBlocks.MAIN_COMPUTER.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(computerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("The Pilot Helmet displays custom HUD graphics, telemetry, and camera streams on your screen.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay().showControls(util.vector().topOf(computerPos), Pointing.DOWN, 50)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.PILOT_HELMET.get()));
        scene.idle(20);

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, computerPos, new net.minecraft.world.phys.AABB(computerPos), 80);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("Right-click the Main Computer with the Pilot Helmet in hand to link them together.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay().showText(120)
            .text("Wear the linked helmet in your head armor slot. The computer will stream HUD overlay shapes, lines, text, and camera feeds directly to your screen!")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(130);
    }

    public static void servoMotorScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("servo_motor", "Using the Servo Motor");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.addKeyframe();
        BlockPos computerPos = util.grid().at(2, 1, 1);
        scene.world().setBlock(computerPos, ModBlocks.MAIN_COMPUTER.get().defaultBlockState(), true);
        scene.world().showSection(util.select().position(computerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("Place the Main Computer first. It will control the servo motor.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        BlockPos servoPos = util.grid().at(2, 1, 3);
        scene.overlay().showControls(util.vector().topOf(computerPos), Pointing.DOWN, 40)
            .rightClick()
            .withItem(new net.minecraft.world.item.ItemStack(com.radiologistics.create.registry.ModItems.SERVO_MOTOR.get()));
        scene.idle(20);

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, computerPos, new net.minecraft.world.phys.AABB(computerPos), 80);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("Right-click the Main Computer with the Servo Motor in hand to assign the link target.")
            .pointAt(util.vector().topOf(computerPos));
        scene.idle(95);

        scene.addKeyframe();
        BlockPos motorPos = util.grid().at(2, 1, 4);

        net.minecraft.world.level.block.state.BlockState creativeMotorState =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("create:creative_motor"))
                .map(block -> {
                    var state = block.defaultBlockState();
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH);
                    }
                    return state;
                })
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        scene.world().setBlock(motorPos, creativeMotorState, true);
        scene.world().showSection(util.select().position(motorPos), Direction.DOWN);
        scene.idle(10);

        scene.world().setBlock(servoPos, ModBlocks.SERVO_MOTOR.get().defaultBlockState().setValue(com.radiologistics.create.block.ServoMotorBlock.FACING, Direction.NORTH), true);
        scene.world().showSection(util.select().position(servoPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("Place the Servo Motor and connect kinetic energy (like a creative motor or shaft) to the back input shaft.")
            .pointAt(util.vector().centerOf(motorPos));
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay().showText(100)
            .text("Once connected, you can program node logic to set the motor's target angle and retrieve current feedback.")
            .pointAt(util.vector().centerOf(servoPos));
        scene.idle(110);
    }

    public static void smartOpticalSensorScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("smart_optical_sensor", "Using the Smart Optical Sensor");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.addKeyframe();
        BlockPos sensorPos = util.grid().at(2, 1, 2);
        scene.world().setBlock(sensorPos, ModBlocks.SMART_OPTICAL_SENSOR.get().defaultBlockState().setValue(com.radiologistics.create.block.SmartOpticalSensorBlock.FACING, Direction.SOUTH), true);
        scene.world().showSection(util.select().position(sensorPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
            .text("The Smart Optical Sensor raycasts up to 150 blocks in its facing direction.")
            .pointAt(util.vector().topOf(sensorPos));
        scene.idle(95);

        scene.addKeyframe();
        BlockPos targetPos = util.grid().at(2, 1, 4);
        scene.world().setBlock(targetPos, net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), true);
        scene.world().showSection(util.select().position(targetPos), Direction.DOWN);

        scene.world().setBlock(sensorPos, ModBlocks.SMART_OPTICAL_SENSOR.get().defaultBlockState()
            .setValue(com.radiologistics.create.block.SmartOpticalSensorBlock.FACING, Direction.SOUTH)
            .setValue(com.radiologistics.create.block.SmartOpticalSensorBlock.POWERED, true), true);
        scene.world().modifyBlockEntity(sensorPos, com.radiologistics.create.block.SmartOpticalSensorBlockEntity.class, be -> {
            be.setTargetDistance(2.0f);
        });
        scene.idle(15);

        scene.overlay().showText(80)
            .text("When hitting blocks or entities, it emits redstone power (15) and draws a red laser dot.")
            .pointAt(util.vector().topOf(targetPos));
        scene.idle(95);

        scene.addKeyframe();
        scene.overlay().showText(90)
            .text("Use the range switch on the sensor to adjust the maximum detection distance (up to 150 blocks).")
            .pointAt(util.vector().centerOf(sensorPos));
        scene.idle(105);

        scene.addKeyframe();
        BlockPos linkPos = util.grid().at(2, 1, 1);
        BlockPos boardPos = util.grid().at(2, 1, 0);

        net.minecraft.world.level.block.state.BlockState displayLinkState =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("create:display_link"))
                .map(block -> {
                    var state = block.defaultBlockState();
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
                    }
                    return state;
                })
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        net.minecraft.world.level.block.state.BlockState displayBoardState =
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse("create:display_board"))
                .map(block -> {
                    var state = block.defaultBlockState();
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
                    }
                    return state;
                })
                .orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        scene.world().setBlock(boardPos, displayBoardState, true);
        scene.world().showSection(util.select().position(boardPos), Direction.DOWN);
        scene.idle(15);

        var displayLinkItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("create:display_link"));
        net.minecraft.world.item.ItemStack displayLinkStack = new net.minecraft.world.item.ItemStack(displayLinkItem);

        scene.overlay().showControls(util.vector().topOf(boardPos), Pointing.DOWN, 40)
            .rightClick()
            .withItem(displayLinkStack);
        scene.idle(20);

        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, boardPos, new net.minecraft.world.phys.AABB(boardPos), 60);
        scene.idle(20);

        scene.overlay().showText(80)
            .text("First, right-click the target Display Board with the Display Link in hand...")
            .pointAt(util.vector().centerOf(boardPos));
        scene.idle(95);

        scene.addKeyframe();

        scene.world().setBlock(linkPos, displayLinkState, true);
        scene.world().showSection(util.select().position(linkPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(120)
            .text("...then place it on the back of the sensor. It will print the target block information dynamically!")
            .pointAt(util.vector().centerOf(linkPos));
        scene.idle(135);
    }
}
