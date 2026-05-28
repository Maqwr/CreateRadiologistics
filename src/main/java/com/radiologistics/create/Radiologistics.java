package com.radiologistics.create;

import com.radiologistics.create.registry.ModBlocks;
import com.radiologistics.create.registry.ModItems;
import com.radiologistics.create.registry.ModBlockEntities;
import com.radiologistics.create.registry.ModCreativeModeTabs;
import com.radiologistics.create.registry.ModPackets;
import com.radiologistics.create.radio.RadioNetworkManager;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Radiologistics.MODID)
public class Radiologistics {
    public static final String MODID = "radiologistics";
    public static final Logger LOGGER = LoggerFactory.getLogger("Create: Radiologistics");

    public Radiologistics(IEventBus modEventBus) {
        LOGGER.info("Initializing Create: Radiologistics...");

        // Register registries to mod event bus
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Register networking
        modEventBus.addListener(ModPackets::register);
        modEventBus.addListener(this::commonSetup);

        // Hook server lifecycle events to clear network registry and prevent leaks
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(this::onBlockRightClicked);
    }

    private void commonSetup(final net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.simibubi.create.api.behaviour.display.DisplayTarget.BY_BLOCK.register(
                ModBlocks.MAIN_COMPUTER.get(),
                new com.radiologistics.create.block.ComputerDisplayTarget()
            );
        });
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("Server starting - clearing radio channels...");
        RadioNetworkManager.clearChannels();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server stopped - clearing radio channels...");
        RadioNetworkManager.clearChannels();
    }

    private static boolean isNetworkFilterer(net.minecraft.world.level.block.Block block) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("create_radar") && id.getPath().equals("network_filterer");
    }

    private void onBlockPlaced(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.world.level.block.state.BlockState state = event.getPlacedBlock();
            if (isNetworkFilterer(state.getBlock())) {
                net.minecraft.core.BlockPos pos = event.getPos();
                net.minecraft.core.BlockPos computerPos = com.radiologistics.create.network.PlayerLinkManager.getPendingLink(player.getUUID());
                if (computerPos != null) {
                    net.minecraft.world.level.Level level = event.getLevel() instanceof net.minecraft.world.level.Level l ? l : null;
                    net.minecraft.world.level.block.entity.BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(level, computerPos);
                    if (be instanceof com.radiologistics.create.block.MainComputerBlockEntity computer) {
                        double distSq = pos.distSqr(computerPos);
                        if (distSq > 100.0) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("too far").withStyle(net.minecraft.ChatFormatting.RED), true);
                        } else {
                            boolean success = computer.linkModule("network_controller", pos);
                            if (success) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("success").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                                com.radiologistics.create.network.PlayerLinkManager.setPendingLink(player, null);
                            } else {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("module already connected").withStyle(net.minecraft.ChatFormatting.RED), true);
                            }
                        }
                    }
                }
            }
        }
    }

    private void onBlockRightClicked(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.world.level.block.state.BlockState state = event.getLevel().getBlockState(event.getPos());
            if (isNetworkFilterer(state.getBlock())) {
                net.minecraft.core.BlockPos pos = event.getPos();
                net.minecraft.core.BlockPos computerPos = com.radiologistics.create.network.PlayerLinkManager.getPendingLink(player.getUUID());
                if (computerPos != null) {
                    net.minecraft.world.level.Level level = event.getLevel() instanceof net.minecraft.world.level.Level l ? l : null;
                    net.minecraft.world.level.block.entity.BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(level, computerPos);
                    if (be instanceof com.radiologistics.create.block.MainComputerBlockEntity computer) {
                        double distSq = pos.distSqr(computerPos);
                        if (distSq > 100.0) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("too far").withStyle(net.minecraft.ChatFormatting.RED), true);
                        } else {
                            boolean success = computer.linkModule("network_controller", pos);
                            if (success) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("success").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                                com.radiologistics.create.network.PlayerLinkManager.setPendingLink(player, null);
                                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                                event.setCanceled(true);
                            } else {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("module already connected").withStyle(net.minecraft.ChatFormatting.RED), true);
                            }
                        }
                    }
                }
            }
        }
    }
}
