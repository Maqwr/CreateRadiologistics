package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveTransmitterPacket(BlockPos pos, String channel, String message) implements CustomPacketPayload {
    public static final Type<SaveTransmitterPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "save_transmitter"));

    public static final StreamCodec<FriendlyByteBuf, SaveTransmitterPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SaveTransmitterPacket::pos,
        ByteBufCodecs.STRING_UTF8, SaveTransmitterPacket::channel,
        ByteBufCodecs.STRING_UTF8, SaveTransmitterPacket::message,
        SaveTransmitterPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
