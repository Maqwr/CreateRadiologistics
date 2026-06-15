package com.radiologistics.create.gui;

import com.radiologistics.create.Radiologistics;
import com.radiologistics.create.network.PlayerLinkManager;
import com.radiologistics.create.registry.ModItems;
import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.BaseModuleBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Set;
import java.util.HashSet;

@EventBusSubscriber(modid = Radiologistics.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class LinkOutlineRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        BlockPos targetPos = PlayerLinkManager.getClientPendingLink();
        if (targetPos != null) {
            boolean holdingModule = isModuleItem(player.getMainHandItem()) || isModuleItem(player.getOffhandItem());
            if (holdingModule) {
                renderOutline(player, targetPos);
            }
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        // 1. Highlight the wire selected group
        BlockPos wireSelectedPos = getWireSelectedPos(main);
        if (wireSelectedPos == null) {
            wireSelectedPos = getWireSelectedPos(off);
        }

        if (wireSelectedPos != null) {
            Set<BlockPos> group = findConnectedGroup(player.level(), wireSelectedPos);
            for (BlockPos pos : group) {
                renderOutline(player, pos);
            }
            return;
        }

        // 2. Fallback to highlight the linked computer
        BlockPos linkedPos = getLinkedComputerPos(main);
        if (linkedPos == null) {
            linkedPos = getLinkedComputerPos(off);
        }

        if (linkedPos != null) {
            renderOutline(player, linkedPos);
        }
    }

    private static BlockPos getWireSelectedPos(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() == ModItems.WIRE.get()) {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (tag.contains("SelectedPos")) {
                    return BlockPos.of(tag.getLong("SelectedPos"));
                }
            }
        }
        return null;
    }

    private static BlockPos getLinkedComputerPos(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() == ModItems.WIRE.get() || stack.getItem() == ModItems.PILOT_HELMET.get()) {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (tag.contains("LinkedComputer")) {
                    return BlockPos.of(tag.getLong("LinkedComputer"));
                }
            }
        }
        return null;
    }

    private static Set<BlockPos> findConnectedGroup(net.minecraft.world.level.Level level, BlockPos startPos) {
        Set<BlockPos> group = new HashSet<>();
        group.add(startPos);

        net.minecraft.world.level.block.entity.BlockEntity startBe = level.getBlockEntity(startPos);
        BlockPos computerPos = null;

        if (startBe instanceof MainComputerBlockEntity) {
            computerPos = startPos;
        } else if (startBe instanceof BaseModuleBlockEntity module) {
            computerPos = module.getComputerPos();
        } else if (startBe instanceof FlapDisplayBlockEntity || startBe instanceof NixieTubeBlockEntity) {
            computerPos = findLinkingComputer(level, startPos);
        } else if (startBe instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity) {
            computerPos = findLinkingComputer(level, startPos);
        }

        if (computerPos != null) {
            group.add(computerPos);
            net.minecraft.world.level.block.entity.BlockEntity compBe = level.getBlockEntity(computerPos);
            if (compBe instanceof MainComputerBlockEntity computer) {
                group.addAll(computer.getLinkedModules().values());
                if (computer.getDisplayLinkPos() != null) {
                    group.add(computer.getDisplayLinkPos());
                }
            }
        }

        return group;
    }

    private static BlockPos findLinkingComputer(net.minecraft.world.level.Level level, BlockPos targetPos) {
        int radius = 16;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = targetPos.offset(x, y, z);
                    net.minecraft.world.level.block.entity.BlockEntity checkBe = level.getBlockEntity(checkPos);
                    if (checkBe instanceof MainComputerBlockEntity computer) {
                        if (computer.isDisplayBoardLinked(targetPos)) {
                            return checkPos;
                        }
                        if (targetPos.equals(computer.getDisplayLinkPos())) {
                            return checkPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void renderOutline(Player player, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
        AABB aabb;
        if (!state.isAir()) {
            aabb = state.getShape(player.level(), pos).bounds().move(pos);
        } else {
            aabb = new AABB(pos);
        }
        net.createmod.catnip.outliner.Outliner.getInstance().showAABB(pos, aabb, 2)
            .colored(0xFFFFCD74) // Create's Display Link color (0xFFCD74 with full alpha)
            .lineWidth(0.0625f);
    }

    private static boolean isNetworkFiltererItem(net.minecraft.world.item.Item item) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("create_radar") && id.getPath().equals("network_filterer");
    }

    private static boolean isModuleItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == ModItems.REDSTONE_LINK_MODULE.get()
            || item == ModItems.MEMORY_MODULE.get()
            || item == ModItems.GYROSCOPE_SENSOR.get()
            || item == ModItems.ANTENNA.get()
            || item == ModItems.JAMMER.get()
            || item == ModItems.AUDIO_MODULE.get()
            || item == ModItems.TRANSPARENT_SCREEN.get()
            || item == ModItems.WIRE.get()
            || item == ModItems.PILOT_HELMET.get()
            || isNetworkFiltererItem(item);
    }
}
