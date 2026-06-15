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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Radiologistics.MODID)
public class Radiologistics {
    public static final String MODID = "radiologistics";
    public static final Logger LOGGER = LoggerFactory.getLogger("Create: Radiologistics");
    public static boolean isServerStopping = false;

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
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(this::onBlockRightClicked);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
    }

    private void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() == null) return;
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // Perform check once a second for performance
        if (player.level().getGameTime() % 20 != 0) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof com.radiologistics.create.item.PilotHelmetItem) {
                net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("LinkedComputer")) {
                        BlockPos linkedPos = BlockPos.of(tag.getLong("LinkedComputer"));
                        net.minecraft.world.level.block.entity.BlockEntity be = com.radiologistics.create.block.BaseModuleBlock.resolveBlockEntity(player.level(), linkedPos);
                        if (!(be instanceof com.radiologistics.create.block.MainComputerBlockEntity)) {
                            CompoundTag newTag = customData.copyTag();
                            newTag.remove("LinkedComputer");
                            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(newTag));
                        }
                    }
                }
            }
        }
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
        LOGGER.info("[Radiologistics] Server starting - clearing radio channels...");
        RadioNetworkManager.clearChannels();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[Radiologistics] Server stopping.");
        isServerStopping = true;
    }

    private void onServerStopped(ServerStoppedEvent event) {
        isServerStopping = false;
        LOGGER.info("[Radiologistics] Server stopped - clearing radio channels.");
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
