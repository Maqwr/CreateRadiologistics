package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveComputerGraphPacket(BlockPos pos, CompoundTag graphNBT) implements CustomPacketPayload {
    public static final Type<SaveComputerGraphPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "save_computer_graph"));

    public static final StreamCodec<FriendlyByteBuf, SaveComputerGraphPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SaveComputerGraphPacket::pos,
        ByteBufCodecs.COMPOUND_TAG, SaveComputerGraphPacket::graphNBT,
        SaveComputerGraphPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
