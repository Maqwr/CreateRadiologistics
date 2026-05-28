package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenComputerScreenPacket(BlockPos pos, CompoundTag graphNBT) implements CustomPacketPayload {
    public static final Type<OpenComputerScreenPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "open_computer_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenComputerScreenPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenComputerScreenPacket::pos,
        ByteBufCodecs.COMPOUND_TAG, OpenComputerScreenPacket::graphNBT,
        OpenComputerScreenPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
