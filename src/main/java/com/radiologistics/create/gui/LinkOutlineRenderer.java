package com.radiologistics.create.gui;

import com.radiologistics.create.Radiologistics;
import com.radiologistics.create.network.PlayerLinkManager;
import com.radiologistics.create.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Radiologistics.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class LinkOutlineRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        BlockPos targetPos = PlayerLinkManager.getClientPendingLink();
        if (targetPos == null) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        // Check if player is holding a module item
        boolean holdingModule = isModuleItem(player.getMainHandItem()) || isModuleItem(player.getOffhandItem());
        if (!holdingModule) {
            return;
        }

        // Render outline using Create's Outliner
        net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(targetPos);
        AABB aabb;
        if (!state.isAir()) {
            aabb = state.getShape(player.level(), targetPos).bounds().move(targetPos);
        } else {
            aabb = new AABB(targetPos);
        }
        net.createmod.catnip.outliner.Outliner.getInstance().showAABB(targetPos, aabb)
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
            || isNetworkFiltererItem(item);
    }
}
