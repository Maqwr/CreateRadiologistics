package com.radiologistics.create.network;

import com.radiologistics.create.gui.NodeEditorScreen;
import com.radiologistics.create.gui.TransmitterScreen;
import com.radiologistics.create.node.NodeGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class ClientOnlyHandler {
    public static void openComputerScreen(BlockPos pos, CompoundTag graphNBT) {
        NodeGraph graph = new NodeGraph();
        graph.loadNBT(graphNBT);
        Minecraft.getInstance().setScreen(new NodeEditorScreen(pos, graph, graphNBT));
    }

    public static void openTransmitterScreen(BlockPos pos, String channel, String message, int range) {
        Minecraft.getInstance().setScreen(new TransmitterScreen(pos, channel, message, range));
    }

    public static void handleSyncPendingLink(BlockPos pos) {
        PlayerLinkManager.setClientPendingLink(pos);
    }

    public static void handlePlayAudioModule(BlockPos pos, boolean play, String typeStr, String data, double volume, double pitch) {
        if (!play) {
            ClientAudioPlayer.stop(pos);
        } else if ("tts".equals(typeStr)) {
            ClientAudioPlayer.playTTS(pos, data, volume, pitch);
        } else if ("url".equals(typeStr)) {
            ClientAudioPlayer.playURL(pos, data, volume, pitch);
        }
    }
}
