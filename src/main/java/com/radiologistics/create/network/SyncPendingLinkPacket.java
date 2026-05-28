package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record SyncPendingLinkPacket(Optional<BlockPos> pos) implements CustomPacketPayload {
    public static final Type<SyncPendingLinkPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radiologistics.MODID, "sync_pending_link"));

    public static final StreamCodec<FriendlyByteBuf, SyncPendingLinkPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SyncPendingLinkPacket::pos,
        SyncPendingLinkPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
