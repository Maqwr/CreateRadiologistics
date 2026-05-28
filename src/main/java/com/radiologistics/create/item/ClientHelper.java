package com.radiologistics.create.item;

import net.minecraft.client.gui.screens.Screen;

public class ClientHelper {
    public static boolean isShiftDown() {
        try {
            return Screen.hasShiftDown();
        } catch (NoClassDefFoundError | Exception e) {
            return false;
        }
    }
}
