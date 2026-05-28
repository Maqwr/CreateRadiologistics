package com.radiologistics.create.registry;

import com.radiologistics.create.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPackets {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("radiologistics");

        // Server -> Client
        registrar.playToClient(
            OpenComputerScreenPacket.TYPE,
            OpenComputerScreenPacket.STREAM_CODEC,
            PacketHandler.Client::handleOpenComputerScreen
        );

        // Client -> Server
        registrar.playToServer(
            SaveComputerGraphPacket.TYPE,
            SaveComputerGraphPacket.STREAM_CODEC,
            PacketHandler.Server::handleSaveComputerGraph
        );

        // Client -> Server
        registrar.playToServer(
            AudioFinishedPacket.TYPE,
            AudioFinishedPacket.STREAM_CODEC,
            PacketHandler.Server::handleAudioFinished
        );

        // Server -> Client
        registrar.playToClient(
            OpenTransmitterScreenPacket.TYPE,
            OpenTransmitterScreenPacket.STREAM_CODEC,
            PacketHandler.Client::handleOpenTransmitterScreen
        );

        // Client -> Server
        registrar.playToServer(
            SaveTransmitterPacket.TYPE,
            SaveTransmitterPacket.STREAM_CODEC,
            PacketHandler.Server::handleSaveTransmitter
        );

        // Server -> Client
        registrar.playToClient(
            SyncPendingLinkPacket.TYPE,
            SyncPendingLinkPacket.STREAM_CODEC,
            PacketHandler.Client::handleSyncPendingLink
        );

        // Server -> Client
        registrar.playToClient(
            PlayAudioModulePacket.TYPE,
            PlayAudioModulePacket.STREAM_CODEC,
            PacketHandler.Client::handlePlayAudioModule
        );
    }
}
