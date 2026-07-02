package com.radiologistics.create.network;

import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
                    com.radiologistics.create.node.nodes.AudioPlayNode.recordFinished(pos, level.getGameTime());

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
        private static Object invokeClientOnly(String methodName, Class<?>[] argTypes, Object[] args) {
            try {
                Class<?> clazz = Class.forName("com.radiologistics.create.network.ClientOnlyHandler");
                java.lang.reflect.Method method = clazz.getMethod(methodName, argTypes);
                return method.invoke(null, args);
            } catch (Throwable t) {
                t.printStackTrace();
                return null;
            }
        }

        public static void handleOpenComputerScreen(final OpenComputerScreenPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                invokeClientOnly("openComputerScreen",
                    new Class<?>[]{BlockPos.class, CompoundTag.class},
                    new Object[]{payload.pos(), payload.graphNBT()}
                );
            });
        }

        public static void handleOpenTransmitterScreen(final OpenTransmitterScreenPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                invokeClientOnly("openTransmitterScreen",
                    new Class<?>[]{BlockPos.class, String.class, String.class, int.class},
                    new Object[]{payload.pos(), payload.channel(), payload.message(), payload.range()}
                );
            });
        }

        public static void handleSyncPendingLink(final SyncPendingLinkPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                invokeClientOnly("handleSyncPendingLink",
                    new Class<?>[]{BlockPos.class},
                    new Object[]{payload.pos().orElse(null)}
                );
            });
        }

        public static void handlePlayAudioModule(final PlayAudioModulePacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                invokeClientOnly("handlePlayAudioModule",
                    new Class<?>[]{BlockPos.class, boolean.class, String.class, String.class, double.class, double.class, double.class},
                    new Object[]{payload.pos(), payload.play(), payload.typeStr(), payload.data(), payload.volume(), payload.pitch(), payload.seekSeconds()}
                );
            });
        }

        public static void handleSyncHelmetGizmos(final SyncHelmetGizmosPacket payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                invokeClientOnly("handleSyncHelmetGizmos",
                    new Class<?>[]{BlockPos.class, String.class},
                    new Object[]{payload.computerPos(), payload.gizmosJson()}
                );
            });
        }
    }
}
