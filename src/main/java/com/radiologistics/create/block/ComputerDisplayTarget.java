package com.radiologistics.create.block;

import com.simibubi.create.content.redstone.displayLink.target.SingleLineDisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ComputerDisplayTarget extends SingleLineDisplayTarget {
    @Override
    protected void acceptLine(MutableComponent text, DisplayLinkContext context) {
        BlockEntity be = context.getTargetBlockEntity();
        if (be instanceof MainComputerBlockEntity computer) {
            computer.setDisplayLinkText(text.getString());
            computer.registerDisplayLinkUpdate(context.blockEntity().getBlockPos());
        }
    }

    @Override
    protected int getWidth(DisplayLinkContext context) {
        return 128;
    }
}
