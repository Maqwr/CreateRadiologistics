package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.network.PlayAudioModulePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AudioPlayNode extends AlgoNode {
    public record PlayingSoundState(String url, double volume, double pitch, long startTick) {}
    public static final Map<BlockPos, PlayingSoundState> activeSounds = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> lastParticleTicks = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> finishedTicks = new ConcurrentHashMap<>();

    public static void stopPlaying(BlockPos pos) {
        activeSounds.remove(pos);
        lastParticleTicks.remove(pos);
    }

    public static void recordFinished(BlockPos pos, long gameTime) {
        finishedTicks.put(pos, gameTime);
        activeSounds.remove(pos);
    }

    public AudioPlayNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "audio_play";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("event", "stream");
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
        if (context.getLevel() == null || context.getLevel().isClientSide() || context.getComputer() == null || com.radiologistics.create.Radiologistics.isServerStopping) {
            return null;
        }

        boolean isEventConnected = false;
        if (context.getComputer() != null && context.getComputer().getGraph() != null) {
            for (com.radiologistics.create.node.NodeGraph.NodeLink link : context.getComputer().getGraph().getLinks()) {
                if (link.toNode().equals(getId()) && link.toPort().equals("event")) {
                    isEventConnected = true;
                    break;
                }
            }
        }

        boolean currentEvent = false;
        if (!isEventConnected) {
            currentEvent = true;
        } else {
            Object eventVal = inputValues.get("event");
            if (eventVal instanceof Boolean b) {
                currentEvent = b;
            } else if (eventVal instanceof Number num) {
                currentEvent = num.doubleValue() != 0.0;
            } else if (eventVal != null) {
                String str = String.valueOf(eventVal).trim().toLowerCase();
                currentEvent = str.equals("true") || str.equals("1") || str.equals("1.0");
            }
        }

        BlockPos targetPos = context.getComputer().getModulePos("audio");
        if (targetPos == null) {
            return null;
        }

        Object streamVal = inputValues.get("stream");
        String streamStr = streamVal != null ? String.valueOf(streamVal).trim() : "";

        boolean wasPlaying = activeSounds.containsKey(targetPos);

        if (currentEvent && !streamStr.isEmpty()) {
            try {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(streamStr).getAsJsonObject();
                String type = obj.get("audio_type").getAsString();
                if ("sound".equals(type)) {
                    String url = obj.get("url").getAsString();
                    double volume = obj.has("volume") ? obj.get("volume").getAsDouble() : 1.0;
                    double pitch = obj.has("pitch") ? obj.get("pitch").getAsDouble() : 1.0;

                    PlayingSoundState current = activeSounds.get(targetPos);
                    long startTick;
                    if (current == null || !url.equals(current.url())) {
                        startTick = context.getLevel().getGameTime();
                    } else {
                        startTick = current.startTick();
                    }

                    long currentTick = context.getLevel().getGameTime();
                    double seekSeconds = Math.max(0.0, (currentTick - startTick) / 20.0);

                    if (current == null || !url.equals(current.url()) || volume != current.volume() || pitch != current.pitch()) {
                        activeSounds.put(targetPos, new PlayingSoundState(url, volume, pitch, startTick));

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
                        } catch (Throwable ignored) {}

                        Vec3 center = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                        try {
                            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                            Object companion = companionClass.getField("INSTANCE").get(null);
                            java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
                            Vec3 projected = (Vec3) projectMethod.invoke(companion, level, center);
                            if (projected != null) {
                                center = projected;
                            }
                        } catch (Throwable ignored) {}

                        double r = 24.0;
                        for (Player player : parentLevel.players()) {
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                if (serverPlayer.distanceToSqr(center) <= r * r) {
                                    PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, true, "url", url, volume, pitch, seekSeconds));
                                }
                            }
                        }
                    }
                    // Spawn NOTE particles scattered across the module surface
                    long currentTickForParticle = context.getLevel().getGameTime();
                    Long lastPt = lastParticleTicks.get(targetPos);
                    if (lastPt == null || currentTickForParticle - lastPt >= 4) {
                        lastParticleTicks.put(targetPos, currentTickForParticle);
                        net.minecraft.world.level.Level particleLevel = context.getLevel();
                        try {
                            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                            if (subLevelClass.isInstance(particleLevel)) {
                                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                                net.minecraft.world.level.Level parent = (net.minecraft.world.level.Level) getLevelMethod.invoke(particleLevel);
                                if (parent != null) particleLevel = parent;
                            }
                        } catch (Throwable ignored) {}
                        if (particleLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                            // Scatter 3 particles randomly over the 14x14 module face (offsets -0.35 to +0.35)
                            java.util.Random rand = new java.util.Random();
                            for (int i = 0; i < 3; i++) {
                                double px = targetPos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;
                                double py = targetPos.getY() + 0.3;
                                double pz = targetPos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;
                                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                                        px, py, pz, 0, rand.nextDouble(), 0.0, 0.0, 1.0);
                            }
                        }
                    }
                } else if ("tts".equals(type)) {
                    String text = obj.has("text") ? obj.get("text").getAsString() : "";
                    double volume = obj.has("volume") ? obj.get("volume").getAsDouble() : 1.0;
                    double pitch  = obj.has("pitch")  ? obj.get("pitch").getAsDouble()  : 1.0;
                    String language = obj.has("language") ? obj.get("language").getAsString() : "en";

                    if (!text.isEmpty()) {
                        String ttsKey = "tts:" + text + ":" + language;
                        PlayingSoundState current = activeSounds.get(targetPos);
                        if (current == null || !ttsKey.equals(current.url()) || volume != current.volume() || pitch != current.pitch()) {
                            activeSounds.put(targetPos, new PlayingSoundState(ttsKey, volume, pitch, context.getLevel().getGameTime()));

                            net.minecraft.world.level.Level level = context.getLevel();
                            net.minecraft.world.level.Level parentLevel = level;
                            try {
                                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                                if (subLevelClass.isInstance(level)) {
                                    java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                                    net.minecraft.world.level.Level parent = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                                    if (parent != null) parentLevel = parent;
                                }
                            } catch (Throwable ignored) {}

                            Vec3 center = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                            try {
                                Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                                Object companion = companionClass.getField("INSTANCE").get(null);
                                java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
                                Vec3 projected = (Vec3) projectMethod.invoke(companion, level, center);
                                if (projected != null) center = projected;
                            } catch (Throwable ignored) {}

                            String packetData = language + "|" + text;
                            double r = 24.0;
                            for (Player player : parentLevel.players()) {
                                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                    if (serverPlayer.distanceToSqr(center) <= r * r) {
                                        PacketDistributor.sendToPlayer(serverPlayer,
                                            new PlayAudioModulePacket(targetPos, true, "tts", packetData, volume, pitch, 0.0));
                                    }
                                }
                            }
                        }

                        // Spawn NOTE particles scattered across the module surface
                        long currentTickForParticle = context.getLevel().getGameTime();
                        Long lastPt = lastParticleTicks.get(targetPos);
                        if (lastPt == null || currentTickForParticle - lastPt >= 4) {
                            lastParticleTicks.put(targetPos, currentTickForParticle);
                            net.minecraft.world.level.Level particleLevel = context.getLevel();
                            try {
                                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                                if (subLevelClass.isInstance(particleLevel)) {
                                    java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                                    net.minecraft.world.level.Level parent = (net.minecraft.world.level.Level) getLevelMethod.invoke(particleLevel);
                                    if (parent != null) particleLevel = parent;
                                }
                            } catch (Throwable ignored) {}
                            if (particleLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                                java.util.Random rand = new java.util.Random();
                                for (int i = 0; i < 3; i++) {
                                    double px = targetPos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;
                                    double py = targetPos.getY() + 0.3;
                                    double pz = targetPos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;
                                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                                            px, py, pz, 0, rand.nextDouble(), 0.0, 0.0, 1.0);
                                }
                            }
                        }
                    }
                } else if ("voice".equals(type)) {
                    String playerUuidStr = obj.get("player_uuid").getAsString();
                    UUID playerUUID = UUID.fromString(playerUuidStr);

                    long currentTick = context.getLevel().getGameTime();
                    
                    if (net.neoforged.fml.ModList.get().isLoaded("voicechat")) {
                        com.radiologistics.create.compat.VoiceChatPluginImpl.addOrUpdateRelay(
                            playerUUID, targetPos, context.getLevel().dimension(), 24, currentTick
                        );
                    }
                }
            } catch (Exception ignored) {}
        } else {
            // Only stop if something was actually playing — prevents packet spam every tick when Event=false
            PlayingSoundState state = activeSounds.remove(targetPos);
            lastParticleTicks.remove(targetPos);

            if (state != null) {
                // Transition: was playing → now stopped. Send stop packets exactly once.
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
                } catch (Throwable ignored) {}

                Vec3 center = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                double r = 32.0;
                for (Player player : parentLevel.players()) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        if (serverPlayer.distanceToSqr(center) <= r * r) {
                            PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, false, "url", "", 1.0, 1.0, 0.0));
                            PacketDistributor.sendToPlayer(serverPlayer, new PlayAudioModulePacket(targetPos, false, "tts", "", 1.0, 1.0, 0.0));
                        }
                    }
                }
            }
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

        if ("event".equalsIgnoreCase(outputPort)) {
            return isFinishedEvent;
        }
        return null;
    }
}
