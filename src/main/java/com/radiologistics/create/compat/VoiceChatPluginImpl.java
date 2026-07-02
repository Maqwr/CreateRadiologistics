package com.radiologistics.create.compat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ForgeVoicechatPlugin
public class VoiceChatPluginImpl implements VoicechatPlugin {
    private static VoicechatServerApi voicechatApi;

    public static final Map<UUID, Long> lastSpokeTicks = new ConcurrentHashMap<>();

    public static final Map<UUID, Set<RelayTarget>> activeRelays = new ConcurrentHashMap<>();

    public static class RelayTarget {
        public final BlockPos pos;
        public final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        public final int range;
        public long lastTick;
        public long lastParticleTick = 0;

        public RelayTarget(BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int range, long lastTick) {
            this.pos = pos;
            this.dimension = dimension;
            this.range = range;
            this.lastTick = lastTick;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelayTarget that)) return false;
            return pos.equals(that.pos) && dimension.equals(that.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, dimension);
        }
    }

    public static void addOrUpdateRelay(UUID playerUUID, BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int range, long currentTick) {
        Set<RelayTarget> targets = activeRelays.computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet());
        for (RelayTarget target : targets) {
            if (target.pos.equals(pos) && target.dimension.equals(dimension)) {
                target.lastTick = currentTick;
                return;
            }
        }
        targets.add(new RelayTarget(pos, dimension, range, currentTick));
    }

    @Override
    public String getPluginId() {
        return "radiologistics";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            voicechatApi = serverApi;
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    public static VoicechatServerApi getApi() {
        return voicechatApi;
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (com.radiologistics.create.Radiologistics.isServerStopping) return;
        VoicechatConnection senderConn = event.getSenderConnection();
        if (senderConn == null) return;

        ServerPlayer player = (ServerPlayer) senderConn.getPlayer().getPlayer();
        if (player == null) return;

        UUID playerUUID = player.getUUID();
        long gameTime = player.serverLevel().getGameTime();
        lastSpokeTicks.put(playerUUID, gameTime);

        activeRelays.forEach((sourceUuid, targets) -> {
            targets.removeIf(t -> gameTime - t.lastTick > 10);
        });

        Set<RelayTarget> targets = activeRelays.get(playerUUID);
        if (targets == null || targets.isEmpty()) return;

        MicrophonePacket micPacket = event.getPacket();

        for (RelayTarget target : targets) {
            net.minecraft.server.MinecraftServer server = player.getServer();
            if (server == null) continue;
            net.minecraft.server.level.ServerLevel targetLevel = server.getLevel(target.dimension);
            if (targetLevel == null) continue;

            double radius = target.range;
            List<ServerPlayer> nearbyPlayers = targetLevel.getPlayers(p -> p.distanceToSqr(target.pos.getX() + 0.5, target.pos.getY() + 0.5, target.pos.getZ() + 0.5) <= radius * radius);
            if (nearbyPlayers.isEmpty()) continue;

            if (gameTime - target.lastParticleTick >= 4) {
                target.lastParticleTick = gameTime;
                double px = target.pos.getX() + 0.5;
                double py = target.pos.getY() + 1.2;
                double pz = target.pos.getZ() + 0.5;
                double color = Math.random();
                server.execute(() -> {
                    net.minecraft.server.level.ServerLevel level = server.getLevel(target.dimension);
                    if (level != null) {
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, px, py, pz, 0, color, 0.0, 0.0, 1.0);
                    }
                });
            }

            try {
                LocationalSoundPacket locPacket = micPacket.locationalSoundPacketBuilder()
                    .position(voicechatApi.createPosition(target.pos.getX() + 0.5, target.pos.getY() + 0.5, target.pos.getZ() + 0.5))
                    .build();

                for (ServerPlayer nearbyPlayer : nearbyPlayers) {
                    VoicechatConnection receiverConn = voicechatApi.getConnectionOf(nearbyPlayer.getUUID());
                    if (receiverConn != null) {
                        voicechatApi.sendLocationalSoundPacketTo(receiverConn, locPacket);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
