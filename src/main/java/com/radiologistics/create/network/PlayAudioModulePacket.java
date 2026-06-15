package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayAudioModulePacket(BlockPos pos, boolean play, String typeStr, String data, double volume, double pitch, double seekSeconds) implements CustomPacketPayload {
    public static final Type<PlayAudioModulePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "play_audio_module"));

    public static final StreamCodec<FriendlyByteBuf, PlayAudioModulePacket> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, PlayAudioModulePacket>() {
        @Override
        public PlayAudioModulePacket decode(FriendlyByteBuf buf) {
            return new PlayAudioModulePacket(
                BlockPos.STREAM_CODEC.decode(buf),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, PlayAudioModulePacket packet) {
            BlockPos.STREAM_CODEC.encode(buf, packet.pos());
            buf.writeBoolean(packet.play());
            buf.writeUtf(packet.typeStr());
            buf.writeUtf(packet.data());
            buf.writeDouble(packet.volume());
            buf.writeDouble(packet.pitch());
            buf.writeDouble(packet.seekSeconds());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
