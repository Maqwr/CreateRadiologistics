package com.radiologistics.create.registry;

import com.radiologistics.create.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPackets {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("radiologistics");

        registrar.playToClient(
            OpenComputerScreenPacket.TYPE,
            OpenComputerScreenPacket.STREAM_CODEC,
            PacketHandler.Client::handleOpenComputerScreen
        );

        registrar.playToServer(
            SaveComputerGraphPacket.TYPE,
            SaveComputerGraphPacket.STREAM_CODEC,
            PacketHandler.Server::handleSaveComputerGraph
        );

        registrar.playToServer(
            AudioFinishedPacket.TYPE,
            AudioFinishedPacket.STREAM_CODEC,
            PacketHandler.Server::handleAudioFinished
        );

        registrar.playToClient(
            OpenTransmitterScreenPacket.TYPE,
            OpenTransmitterScreenPacket.STREAM_CODEC,
            PacketHandler.Client::handleOpenTransmitterScreen
        );

        registrar.playToServer(
            SaveTransmitterPacket.TYPE,
            SaveTransmitterPacket.STREAM_CODEC,
            PacketHandler.Server::handleSaveTransmitter
        );

        registrar.playToClient(
            SyncPendingLinkPacket.TYPE,
            SyncPendingLinkPacket.STREAM_CODEC,
            PacketHandler.Client::handleSyncPendingLink
        );

        registrar.playToClient(
            PlayAudioModulePacket.TYPE,
            PlayAudioModulePacket.STREAM_CODEC,
            PacketHandler.Client::handlePlayAudioModule
        );

        registrar.playToClient(
            SyncHelmetGizmosPacket.TYPE,
            SyncHelmetGizmosPacket.STREAM_CODEC,
            PacketHandler.Client::handleSyncHelmetGizmos
        );
    }
}
