package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncHelmetGizmosPacket(BlockPos computerPos, String gizmosJson) implements CustomPacketPayload {
    public static final Type<SyncHelmetGizmosPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "sync_helmet_gizmos"));

    public static final StreamCodec<FriendlyByteBuf, SyncHelmetGizmosPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SyncHelmetGizmosPacket::computerPos,
        ByteBufCodecs.STRING_UTF8, SyncHelmetGizmosPacket::gizmosJson,
        SyncHelmetGizmosPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
