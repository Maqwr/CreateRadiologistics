package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AudioFinishedPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AudioFinishedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "audio_finished"));

    public static final StreamCodec<FriendlyByteBuf, AudioFinishedPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AudioFinishedPacket::pos,
        AudioFinishedPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
