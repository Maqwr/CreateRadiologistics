package com.radiologistics.create.registry;

import com.radiologistics.create.Radiologistics;
import com.radiologistics.create.item.ModBlockItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Radiologistics.MODID);

    public static final DeferredHolder<Item, BlockItem> MAIN_COMPUTER = ITEMS.register("main_computer", () -> new ModBlockItem(ModBlocks.MAIN_COMPUTER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> RADIO_TRANSMITTER = ITEMS.register("radio_transmitter", () -> new ModBlockItem(ModBlocks.RADIO_TRANSMITTER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> REDSTONE_LINK_MODULE = ITEMS.register("redstone_link_module", () -> new ModBlockItem(ModBlocks.REDSTONE_LINK_MODULE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> MEMORY_MODULE = ITEMS.register("memory_module", () -> new ModBlockItem(ModBlocks.MEMORY_MODULE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> GYROSCOPE_SENSOR = ITEMS.register("gyroscope_sensor", () -> new ModBlockItem(ModBlocks.GYROSCOPE_SENSOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> ANTENNA = ITEMS.register("antenna", () -> new ModBlockItem(ModBlocks.ANTENNA.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> JAMMER = ITEMS.register("jammer", () -> new ModBlockItem(ModBlocks.JAMMER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> AUDIO_MODULE = ITEMS.register("audio_module", () -> new ModBlockItem(ModBlocks.AUDIO_MODULE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WIRED_INERTIA_FUZE = ITEMS.register("wired_inertia_fuze", () -> {
        if (net.neoforged.fml.ModList.get().isLoaded("createbigcannons")) {
            try {
                return (Item) Class.forName("com.radiologistics.create.compat.CBCIntegration")
                    .getMethod("createWiredInertiaFuzeItem")
                    .invoke(null);
            } catch (Exception e) {
                Radiologistics.LOGGER.error("Failed to load Wired Inertia Fuze integration", e);
            }
        }
        return new Item(new Item.Properties());
    });
}
