package com.radiologistics.create.registry;

import com.radiologistics.create.Radiologistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Radiologistics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
        CREATIVE_MODE_TABS.register("radiologistics_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.radiologistics_tab"))
            .icon(() -> ModItems.MAIN_COMPUTER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.MAIN_COMPUTER.get());
                output.accept(ModItems.RADIO_TRANSMITTER.get());
                output.accept(ModItems.REDSTONE_LINK_MODULE.get());
                output.accept(ModItems.MEMORY_MODULE.get());
                output.accept(ModItems.GYROSCOPE_SENSOR.get());
                output.accept(ModItems.ANTENNA.get());
                output.accept(ModItems.JAMMER.get());
                output.accept(ModItems.AUDIO_MODULE.get());
                if (net.neoforged.fml.ModList.get().isLoaded("createbigcannons")) {
                    output.accept(ModItems.WIRED_INERTIA_FUZE.get());
                }
            })
            .build());
}
