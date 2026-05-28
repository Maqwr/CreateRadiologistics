package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayAudioModulePacket(BlockPos pos, boolean play, String typeStr, String data, double volume, double pitch) implements CustomPacketPayload {
    public static final Type<PlayAudioModulePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "play_audio_module"));

    public static final StreamCodec<FriendlyByteBuf, PlayAudioModulePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PlayAudioModulePacket::pos,
        ByteBufCodecs.BOOL, PlayAudioModulePacket::play,
        ByteBufCodecs.STRING_UTF8, PlayAudioModulePacket::typeStr,
        ByteBufCodecs.STRING_UTF8, PlayAudioModulePacket::data,
        ByteBufCodecs.DOUBLE, PlayAudioModulePacket::volume,
        ByteBufCodecs.DOUBLE, PlayAudioModulePacket::pitch,
        PlayAudioModulePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
