package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.network.PlayAudioModulePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class TextSpeakNode extends AlgoNode {
    private record PlayingTtsState(String text, double volume, double pitch) {}
    private static final Map<BlockPos, PlayingTtsState> playingTts = new java.util.concurrent.ConcurrentHashMap<>();

    public static void stopPlaying(BlockPos pos) {
        playingTts.remove(pos);
    }

    public TextSpeakNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "text_speak";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("event", "text", "volume", "pitch");
    }

    @Override
    public List<String> getOutputPorts() {
        return Collections.emptyList();
    }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getLevel() == null || context.getLevel().isClientSide()) return null;

        Object eventVal = inputValues.get("event");
        boolean currentEvent = false;
        if (eventVal instanceof Boolean b) {
            currentEvent = b;
        } else if (eventVal instanceof Number num) {
            currentEvent = num.doubleValue() != 0.0;
        } else if (eventVal != null) {
            String str = String.valueOf(eventVal).trim().toLowerCase();
            currentEvent = str.equals("true") || str.equals("1") || str.equals("1.0");
        }

        BlockPos targetPos = context.getComputer().getModulePos("audio");
        if (targetPos == null) {
            targetPos = context.getPos();
        }

        PlayingTtsState current = playingTts.get(targetPos);
        boolean wasPlaying = current != null;

        Object textVal = inputValues.get("text");
        String text = textVal != null ? String.valueOf(textVal) : "";

        Object volumeVal = inputValues.get("volume");
        double volume = 1.0;
        if (volumeVal instanceof Number num) {
            volume = num.doubleValue();
        }
        if (volume > 10.0) volume = 10.0;
        if (volume < 0.0) volume = 0.0;

        Object pitchVal = inputValues.get("pitch");
        double pitch = 1.0;
        if (pitchVal instanceof Number num) {
            pitch = num.doubleValue();
        }

        if (currentEvent && (!wasPlaying || !text.equals(current.text()) || volume != current.volume() || pitch != current.pitch())) {
            playingTts.put(targetPos, new PlayingTtsState(text, volume, pitch));
            
            double r = 24.0;
            Vec3 center = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            for (net.minecraft.world.entity.player.Player player : context.getLevel().players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    if (serverPlayer.distanceToSqr(center) <= r * r) {
                        PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, true, "tts", text, volume, pitch));
                    }
                }
            }
        } else if (!currentEvent && wasPlaying) {
            playingTts.remove(targetPos);
            for (net.minecraft.world.entity.player.Player player : context.getLevel().players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, false, "tts", "", 1.0, 1.0));
                }
            }
        }
        return null;
    }
}
