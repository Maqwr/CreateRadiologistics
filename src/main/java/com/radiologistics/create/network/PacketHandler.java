package com.radiologistics.create.network;

import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PacketHandler {
    
    public static class Server {
        public static void handleSaveComputerGraph(final SaveComputerGraphPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() != null) {
                    BlockPos pos = payload.pos();
                    BlockEntity be = context.player().level().getBlockEntity(pos);
                    if (be instanceof MainComputerBlockEntity computer) {
                        computer.getGraph().loadNBT(payload.graphNBT());
                        computer.updateListeners();
                        computer.evaluateGraph();
                        computer.setChanged();
                    }
                }
            });
        }

        public static void handleSaveTransmitter(final SaveTransmitterPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() != null) {
                    BlockPos pos = payload.pos();
                    net.minecraft.world.level.Level level = context.player().level();
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof RadioTransmitterBlockEntity transmitter) {
                        transmitter.setChannel(payload.channel());
                        transmitter.setMessage(payload.message());
                        transmitter.setChanged();
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                        level.sendBlockUpdated(pos, state, state, 3);
                        // Run a transmission check if it's already powered
                        if (level.hasNeighborSignal(pos)) {
                            transmitter.transmit();
                        }
                    }
                }
            });
        }

        public static void handleAudioFinished(final AudioFinishedPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() != null) {
                    BlockPos pos = payload.pos();
                    net.minecraft.world.level.Level level = context.player().level();
                    long currentTick = level.getGameTime();
                    com.radiologistics.create.node.nodes.SoundPlayNode.recordFinished(pos, currentTick);
                    for (com.radiologistics.create.block.MainComputerBlockEntity computer : com.radiologistics.create.radio.RadioNetworkManager.activeComputers) {
                        if (computer.getLevel() == level) {
                            if (pos.equals(computer.getModulePos("audio")) || pos.equals(computer.getBlockPos())) {
                                computer.evaluateGraph();
                            }
                        }
                    }
                }
            });
        }
    }

    public static class Client {
        public static void handleOpenComputerScreen(final OpenComputerScreenPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                ClientOnlyHandler.openComputerScreen(payload.pos(), payload.graphNBT());
            });
        }

        public static void handleOpenTransmitterScreen(final OpenTransmitterScreenPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                ClientOnlyHandler.openTransmitterScreen(payload.pos(), payload.channel(), payload.message(), payload.range());
            });
        }

        public static void handleSyncPendingLink(final SyncPendingLinkPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                ClientOnlyHandler.handleSyncPendingLink(payload.pos().orElse(null));
            });
        }

        public static void handlePlayAudioModule(final PlayAudioModulePacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                ClientOnlyHandler.handlePlayAudioModule(payload.pos(), payload.play(), payload.typeStr(), payload.data(), payload.volume(), payload.pitch());
            });
        }
    }
}
