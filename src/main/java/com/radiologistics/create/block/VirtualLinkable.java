package com.radiologistics.create.block;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class VirtualLinkable implements IRedstoneLinkable {
    private final MainComputerBlockEntity computer;
    private final String nodeId;
    private final Couple<Frequency> networkKey;
    private final boolean isListening;
    private int receivedStrength = 0;
    private int transmittedStrength = 0;

    public VirtualLinkable(MainComputerBlockEntity computer, String nodeId, String freq1, String freq2, boolean isListening) {
        this.computer = computer;
        this.nodeId = nodeId;
        this.isListening = isListening;

        Item item1 = BuiltInRegistries.ITEM.get(ResourceLocation.parse(freq1));
        Item item2 = BuiltInRegistries.ITEM.get(ResourceLocation.parse(freq2));
        this.networkKey = Couple.create(Frequency.of(new ItemStack(item1)), Frequency.of(new ItemStack(item2)));
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getReceivedStrength() {
        return receivedStrength;
    }

    public void setTransmittedStrength(int strength) {
        this.transmittedStrength = strength;
    }

    @Override
    public BlockPos getLocation() {
        return computer.getBlockPos();
    }

    @Override
    public boolean isAlive() {
        return !computer.isRemoved();
    }

    @Override
    public void setReceivedStrength(int strength) {
        this.receivedStrength = strength;
        if (isListening) {
            computer.evaluateGraph();
        }
    }

    @Override
    public int getTransmittedStrength() {
        return transmittedStrength;
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return networkKey;
    }

    @Override
    public boolean isListening() {
        return isListening;
    }

    public net.minecraft.world.level.Level getLevel() {
        return computer.getLevel();
    }
}
