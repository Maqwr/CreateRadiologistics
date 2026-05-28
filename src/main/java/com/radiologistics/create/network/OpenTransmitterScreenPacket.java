package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenTransmitterScreenPacket(BlockPos pos, String channel, String message, int range) implements CustomPacketPayload {
    public static final Type<OpenTransmitterScreenPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "open_transmitter_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenTransmitterScreenPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenTransmitterScreenPacket::pos,
        ByteBufCodecs.STRING_UTF8, OpenTransmitterScreenPacket::channel,
        ByteBufCodecs.STRING_UTF8, OpenTransmitterScreenPacket::message,
        ByteBufCodecs.VAR_INT, OpenTransmitterScreenPacket::range,
        OpenTransmitterScreenPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
