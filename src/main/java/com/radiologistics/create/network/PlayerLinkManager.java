package com.radiologistics.create.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerLinkManager {
    private static final Map<UUID, BlockPos> serverPendingLinks = new ConcurrentHashMap<>();
    private static BlockPos clientPendingLink = null;

    public static void setPendingLink(ServerPlayer player, BlockPos pos) {
        if (pos == null) {
            serverPendingLinks.remove(player.getUUID());
        } else {
            serverPendingLinks.put(player.getUUID(), pos);
        }

        PacketDistributor.sendToPlayer(player, new SyncPendingLinkPacket(Optional.ofNullable(pos)));
    }

    public static BlockPos getPendingLink(UUID playerUuid) {
        return serverPendingLinks.get(playerUuid);
    }

    public static void setClientPendingLink(BlockPos pos) {
        clientPendingLink = pos;
    }

    public static BlockPos getClientPendingLink() {
        return clientPendingLink;
    }
}
