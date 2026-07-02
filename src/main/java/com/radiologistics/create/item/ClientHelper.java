package com.radiologistics.create.item;

public class ClientHelper {
    public static boolean isShiftDown() {
        try {
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            java.lang.reflect.Method method = screenClass.getMethod("hasShiftDown");
            return (boolean) method.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }
}
