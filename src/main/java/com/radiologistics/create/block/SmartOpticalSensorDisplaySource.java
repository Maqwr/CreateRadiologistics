package com.radiologistics.create.block;

import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SmartOpticalSensorDisplaySource extends SingleLineDisplaySource {
    public static final SmartOpticalSensorDisplaySource INSTANCE = new SmartOpticalSensorDisplaySource();
    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (be instanceof SmartOpticalSensorBlockEntity sensor) {
            return Component.literal(sensor.getLookedAtName());
        }
        return Component.empty();
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }
}
