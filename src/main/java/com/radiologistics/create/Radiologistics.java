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
    public static volatile boolean isServerStopping = false;

    public Radiologistics(IEventBus modEventBus) {
        LOGGER.info("Initializing Create: Radiologistics...");

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(ModPackets::register);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerDisplaySources);
        modEventBus.addListener(this::registerCapabilities);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(this::onBlockRightClicked);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
    }

    private void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() == null) return;
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (player.level().isClientSide()) return;

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
            com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK.register(
                ModBlocks.SMART_OPTICAL_SENSOR.get(),
                java.util.List.of(com.radiologistics.create.block.SmartOpticalSensorDisplaySource.INSTANCE)
            );
        });
    }

    private void registerDisplaySources(final net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE)) {
            event.register(com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE, helper -> {
                helper.register(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "smart_optical_sensor"),
                    com.radiologistics.create.block.SmartOpticalSensorDisplaySource.INSTANCE
                );
            });
        }
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

    private void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(serverLevel)) {
                try {
                    it.unimi.dsi.fastutil.longs.LongSet forced = serverLevel.getForcedChunks();
                    if (forced != null && !forced.isEmpty()) {
                        long[] forcedArray = forced.toLongArray();
                        for (long chunkPosLong : forcedArray) {
                            int cx = net.minecraft.world.level.ChunkPos.getX(chunkPosLong);
                            int cz = net.minecraft.world.level.ChunkPos.getZ(chunkPosLong);
                            serverLevel.setChunkForced(cx, cz, false);
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.error("[Radiologistics] Error clearing forced chunks in Sable sublevel", t);
                }
            }
        }
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
                        net.minecraft.world.phys.Vec3 p1 = com.radiologistics.create.block.MainComputerBlockEntity.getWorldPos(level, pos);
                        net.minecraft.world.phys.Vec3 p2 = com.radiologistics.create.block.MainComputerBlockEntity.getWorldPos(level, computerPos);
                        double distSq = p1.distanceToSqr(p2);
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
                        net.minecraft.world.phys.Vec3 p1 = com.radiologistics.create.block.MainComputerBlockEntity.getWorldPos(level, pos);
                        net.minecraft.world.phys.Vec3 p2 = com.radiologistics.create.block.MainComputerBlockEntity.getWorldPos(level, computerPos);
                        double distSq = p1.distanceToSqr(p2);
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

    public static String rewriteUrl(String urlStr) {
        if (urlStr == null) return "";
        String trimmed = urlStr.trim();

        if (trimmed.contains("dropbox.com")) {
            if (trimmed.contains("dl=0")) {
                trimmed = trimmed.replace("dl=0", "raw=1");
            } else if (trimmed.contains("dl=1")) {
                trimmed = trimmed.replace("dl=1", "raw=1");
            } else if (!trimmed.contains("raw=1")) {
                if (trimmed.contains("?")) {
                    trimmed = trimmed + "&raw=1";
                } else {
                    trimmed = trimmed + "?raw=1";
                }
            }
            return trimmed;
        }

        if (trimmed.contains("drive.google.com")) {
            java.util.regex.Pattern patternD = java.util.regex.Pattern.compile("drive\\.google\\.com/file/d/([^/\\s?]+)");
            java.util.regex.Matcher matcherD = patternD.matcher(trimmed);
            if (matcherD.find()) {
                String fileId = matcherD.group(1);
                return "https://drive.google.com/uc?export=download&id=" + fileId;
            }
            java.util.regex.Pattern patternId = java.util.regex.Pattern.compile("[?&]id=([^&\\s]+)");
            java.util.regex.Matcher matcherId = patternId.matcher(trimmed);
            if (matcherId.find()) {
                String fileId = matcherId.group(1);
                return "https://drive.google.com/uc?export=download&id=" + fileId;
            }
        }

        return trimmed;
    }

    private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.SMART_OPTICAL_SENSOR.get(),
            (blockEntity, context) -> blockEntity.getItemHandler()
        );
    }
}
