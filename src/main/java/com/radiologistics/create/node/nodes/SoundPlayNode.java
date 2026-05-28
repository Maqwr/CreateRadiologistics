package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.network.PlayAudioModulePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SoundPlayNode extends AlgoNode {
    private record PlayingState(String link, double volume, double pitch) {}
    private static final Map<BlockPos, PlayingState> playingUrls = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> finishedTicks = new java.util.concurrent.ConcurrentHashMap<>();

    public static void recordFinished(BlockPos pos, long gameTime) {
        finishedTicks.put(pos, gameTime);
    }

    public static void stopPlaying(BlockPos pos) {
        playingUrls.remove(pos);
        finishedTicks.remove(pos);
    }

    public SoundPlayNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "sound_play";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("event", "link", "volume", "pitch");
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("event");
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

        boolean isFinishedEvent = false;
        long currentTick = context.getLevel().getGameTime();
        Long finishedTick = finishedTicks.get(targetPos);
        if (finishedTick != null) {
            if (currentTick - finishedTick <= 2) {
                isFinishedEvent = true;
            } else {
                finishedTicks.remove(targetPos);
            }
        }

        PlayingState current = playingUrls.get(targetPos);
        boolean wasPlaying = current != null;
        Object linkVal = inputValues.get("link");
        String link = linkVal != null ? String.valueOf(linkVal) : "";

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

        net.minecraft.world.level.Level level = context.getLevel();
        net.minecraft.world.level.Level parentLevel = level;
        try {
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            if (subLevelClass.isInstance(level)) {
                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                net.minecraft.world.level.Level parent = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                if (parent != null) {
                    parentLevel = parent;
                }
            }
        } catch (Exception ignored) {}

        Vec3 center = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Object companion = companionClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
            Vec3 projected = (Vec3) projectMethod.invoke(companion, level, center);
            if (projected != null) {
                center = projected;
            }
        } catch (Exception ignored) {}

        if (currentEvent && (!wasPlaying || !link.equals(current.link()) || volume != current.volume() || pitch != current.pitch())) {
            playingUrls.put(targetPos, new PlayingState(link, volume, pitch));
            
            double r = 24.0;
            for (net.minecraft.world.entity.player.Player player : parentLevel.players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    if (serverPlayer.distanceToSqr(center) <= r * r) {
                        PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, true, "url", link, volume, pitch));
                    }
                }
            }
        } else if (!currentEvent && wasPlaying) {
            playingUrls.remove(targetPos);
            for (net.minecraft.world.entity.player.Player player : parentLevel.players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, false, "url", "", 1.0, 1.0));
                }
            }
        }
        if ("event".equalsIgnoreCase(outputPort)) {
            return isFinishedEvent;
        }
        return null;
    }
}
