package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class MicrophoneNode extends AlgoNode {
    public MicrophoneNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "microphone";
    }

    @Override
    public List<String> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("stream");
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
        if (context.getLevel() == null || context.getLevel().isClientSide() || context.getComputer() == null) {
            return "";
        }

        if (!"stream".equalsIgnoreCase(outputPort)) {
            return null;
        }

        BlockPos targetPos = context.getComputer().getModulePos("audio");
        if (targetPos == null) {
            targetPos = context.getPos();
        }

        double radius = 10.0;
        double radiusSqr = radius * radius;
        long gameTime = context.getLevel().getGameTime();

        Player speakingPlayer = null;
        for (Player player : context.getLevel().players()) {
            if (player.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) <= radiusSqr) {
                if (isPlayerSpeaking(player, gameTime)) {
                    speakingPlayer = player;
                    break;
                }
            }
        }

        if (speakingPlayer != null) {
            return String.format("{\"audio_type\": \"voice\", \"player_uuid\": \"%s\", \"player_name\": \"%s\", \"timestamp\": %d}",
                    speakingPlayer.getUUID().toString(),
                    speakingPlayer.getName().getString().replace("\"", "\\\""),
                    gameTime
            );
        }

        return "";
    }

    private boolean isPlayerSpeaking(Player player, long gameTime) {

        try {
            if (net.neoforged.fml.ModList.get().isLoaded("voicechat")) {
                Long lastSpoke = com.radiologistics.create.compat.VoiceChatPluginImpl.lastSpokeTicks.get(player.getUUID());
                if (lastSpoke != null && (gameTime - lastSpoke <= 12)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (net.neoforged.fml.ModList.get().isLoaded("plasmo_voice")) {
                if (player.isShiftKeyDown()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
