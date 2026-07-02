package com.radiologistics.create.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

public class VistaIntegrationHelper {
    public static final boolean IS_VISTA_LOADED = ModList.get().isLoaded("vista");

    private static Class<?> tvBlockEntityClass;
    private static Class<?> serverCameraChunkManagerClass;
    private static java.lang.reflect.Method trackTvMethod;
    private static java.lang.reflect.Method untrackTvMethod;
    private static java.lang.reflect.Method clearAllMethod;

    static {
        if (IS_VISTA_LOADED) {
            try {
                tvBlockEntityClass = Class.forName("net.mehvahdjukaar.vista.common.tv.TVBlockEntity");
                serverCameraChunkManagerClass = Class.forName("net.mehvahdjukaar.vista.common.chunk_tracking.ServerCameraChunkManager");
                trackTvMethod = serverCameraChunkManagerClass.getMethod("trackTv", tvBlockEntityClass);
                untrackTvMethod = serverCameraChunkManagerClass.getMethod("untrackTv", tvBlockEntityClass);
                clearAllMethod = serverCameraChunkManagerClass.getMethod("clearAll");
            } catch (Throwable ignored) {}
        }
    }

    public static void clearAll() {
        if (!IS_VISTA_LOADED || clearAllMethod == null) return;
        try {
            clearAllMethod.invoke(null);
        } catch (Throwable ignored) {}
    }

    public static Object createMockTv(Level level, BlockPos pos, BlockState computerState, ItemStack cassette) {
        if (!IS_VISTA_LOADED || tvBlockEntityClass == null) return null;
        try {
            Block tvBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vista:tv"));
            BlockState state = tvBlock != null ? tvBlock.defaultBlockState() : computerState;

            if (tvBlock != null) {
                try {
                    Class<?> powerStateClass = Class.forName("net.mehvahdjukaar.vista.common.tv.PowerState");
                    Object directPower = powerStateClass.getField("DIRECT").get(null);
                    net.minecraft.world.level.block.state.properties.Property powerStateProp =
                        (net.minecraft.world.level.block.state.properties.Property) tvBlock.getClass().getField("POWER_STATE").get(null);
                    state = state.setValue(powerStateProp, (Comparable) directPower);
                } catch (Throwable ignored) {}
            }

            BlockEntity tvBE = (BlockEntity) tvBlockEntityClass.getConstructor(BlockPos.class, BlockState.class).newInstance(pos, state);
            tvBE.setLevel(level);

            for (java.lang.reflect.Field field : BlockEntity.class.getDeclaredFields()) {
                if (field.getType() == BlockState.class) {
                    field.setAccessible(true);
                    field.set(tvBE, state);
                    break;
                }
            }

            if (tvBE instanceof net.minecraft.world.Container container) {
                container.setItem(0, cassette);
            } else {
                java.lang.reflect.Method setItemMethod = tvBE.getClass().getMethod("setItem", int.class, ItemStack.class);
                setItemMethod.invoke(tvBE, 0, cassette);
            }

            try {
                java.lang.reflect.Method updateInventoryMethod = tvBlockEntityClass.getDeclaredMethod("updateTileOnInventoryChanged");
                updateInventoryMethod.setAccessible(true);
                updateInventoryMethod.invoke(tvBE);
            } catch (Throwable ignored) {}

            try {
                Class<?> tvEnergyHandlerClass = Class.forName("net.mehvahdjukaar.vista.platform.TvEnergyHandler");
                java.lang.reflect.Method getOrCreateEnergy = tvEnergyHandlerClass.getMethod("getOrCreate", tvBlockEntityClass);
                Object energyHandler = getOrCreateEnergy.invoke(null, tvBE);
                if (energyHandler != null) {
                    java.lang.reflect.Field storedField = tvEnergyHandlerClass.getDeclaredField("stored");
                    storedField.setAccessible(true);
                    storedField.set(energyHandler, Integer.MAX_VALUE);
                }
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Field pausedField = tvBlockEntityClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                pausedField.set(tvBE, false);
            } catch (Throwable ignored) {}

            return tvBE;
        } catch (Throwable ignored) {}
        return null;
    }

    public static void trackTv(Object tvBlockEntity) {
        if (!IS_VISTA_LOADED || trackTvMethod == null || tvBlockEntity == null) return;
        try {
            trackTvMethod.invoke(null, tvBlockEntity);
        } catch (Throwable ignored) {}
    }

    public static void untrackTv(Object tvBlockEntity) {
        if (!IS_VISTA_LOADED || untrackTvMethod == null || tvBlockEntity == null) return;
        try {
            untrackTvMethod.invoke(null, tvBlockEntity);
        } catch (Throwable ignored) {}
    }

    public static void disableVistaDebugLines() {
        if (IS_VISTA_LOADED) {
            try {
                Class<?> clientConfigsClass = Class.forName("net.mehvahdjukaar.vista.configs.ClientConfigs");
                java.lang.reflect.Field field = clientConfigsClass.getField("RENDER_DEBUG");
                field.setAccessible(true);
                try {
                    java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                } catch (Throwable ignored) {}
                field.set(null, (java.util.function.Supplier<Boolean>) () -> false);
            } catch (Throwable ignored) {}
        }
    }
}
