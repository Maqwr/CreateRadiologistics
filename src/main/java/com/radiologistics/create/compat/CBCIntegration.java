package com.radiologistics.create.compat;

import net.minecraft.world.item.Item;
import com.radiologistics.create.item.WiredInertiaFuzeItem;

public class CBCIntegration {
    public static Item createWiredInertiaFuzeItem() {
        return new WiredInertiaFuzeItem(new Item.Properties().stacksTo(64));
    }
}
