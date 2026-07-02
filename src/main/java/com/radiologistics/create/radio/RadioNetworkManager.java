package com.radiologistics.create.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.radiologistics.create.block.MainComputerBlockEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RadioNetworkManager {

    private static final Map<String, String> channelValues = new ConcurrentHashMap<>();

    public static final Set<MainComputerBlockEntity> activeComputers = ConcurrentHashMap.newKeySet();

    public static boolean isChannelJammedAt(Level level, BlockPos pos, String channel) {
        if (channel == null) return false;
        String sanitized = sanitizeChannel(channel);
        int chVal;
        try {
            chVal = Integer.parseInt(sanitized);
        } catch (NumberFormatException e) {
            return false;
        }

        for (MainComputerBlockEntity computer : activeComputers) {
            if (computer.getLevel() != null && level != null && computer.getLevel().dimension().equals(level.dimension())) {
                if (computer.isModuleConnected("jammer")) {
                    int N = computer.getJammers().size();
                    double maxDist = 50.0 * N;
                    double maxDistSq = maxDist * maxDist;
                    for (int[] range : computer.getActiveJammedRanges()) {
                        if (chVal >= range[0] && chVal <= range[1]) {
                            if (pos == null) {
                                return true;
                            }
                            for (BlockPos jammerPos : computer.getJammers()) {
                                double distSq = pos.distToCenterSqr(jammerPos.getX(), jammerPos.getY(), jammerPos.getZ());
                                if (distSq <= maxDistSq) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isChannelJammed(Level level, String channel) {
        return isChannelJammedAt(level, null, channel);
    }

    public static boolean isRedstoneLinkJammedAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        for (MainComputerBlockEntity computer : activeComputers) {
            if (computer.getLevel() != null && computer.getLevel().dimension().equals(level.dimension())) {
                if (computer.isModuleConnected("jammer") && !computer.getActiveJammedRanges().isEmpty()) {
                    int N = computer.getJammers().size();
                    double maxDist = 50.0 * N;
                    double maxDistSq = maxDist * maxDist;
                    for (BlockPos jammerPos : computer.getJammers()) {
                        double distSq = pos.distToCenterSqr(jammerPos.getX(), jammerPos.getY(), jammerPos.getZ());
                        if (distSq <= maxDistSq) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void updateAllRedstoneLinks(Level level) {
        if (level == null || level.isClientSide()) return;
        try {
            var handler = com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER;
            var networks = handler.networksIn(level);
            if (networks != null) {
                List<Set<?>> networkSets = new ArrayList<>();
                synchronized (networks) {
                    for (Object val : networks.values()) {
                        if (val instanceof Set<?> set && !set.isEmpty()) {
                            networkSets.add(new HashSet<>(set));
                        }
                    }
                }
                for (Set<?> set : networkSets) {
                    var first = set.iterator().next();
                    if (first instanceof com.simibubi.create.content.redstone.link.IRedstoneLinkable linkable) {
                        handler.updateNetworkOf(level, linkable);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static final Map<String, Long> channelLastBroadcastTicks = new ConcurrentHashMap<>();

    private static final Map<String, TransmissionRecord> channelTransmissions = new ConcurrentHashMap<>();

    public record TransmissionRecord(String value, ResourceKey<Level> dimension, BlockPos pos, long tick) {}

    public record ListenerKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<String, Set<ListenerKey>> listeners = new ConcurrentHashMap<>();

    public static void clearChannels() {
        channelValues.clear();
        listeners.clear();
        channelLastBroadcastTicks.clear();
        channelTransmissions.clear();
        activeComputers.clear();
    }

    public static String sanitizeChannel(String channel) {
        if (channel == null || channel.isEmpty()) return "0";
        try {
            String digits = channel.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return "0";
            int val = Integer.parseInt(digits);
            if (val < 0) return "0";
            if (val > 255) return "255";
            return String.valueOf(val);
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    public static String getSignal(Level level, BlockPos pos, String channel) {
        if (level != null && isChannelJammedAt(level, pos, channel)) {
            return "";
        }
        String sanitized = sanitizeChannel(channel);
        if (level != null && !level.isClientSide()) {
            Long lastTick = channelLastBroadcastTicks.get(sanitized);
            if (lastTick != null) {
                long currentTick = level.getGameTime();
                if (currentTick - lastTick > 2) {
                    channelValues.put(sanitized, "");
                    channelLastBroadcastTicks.remove(sanitized);
                }
            }
        }
        return channelValues.getOrDefault(sanitized, "");
    }

    public static String getSignal(Level level, String channel) {
        return getSignal(level, null, channel);
    }

    public static String getSignal(String channel) {
        return getSignal(null, channel);
    }

    public static void registerListener(String channel, Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        String sanitized = sanitizeChannel(channel);

        ListenerKey key = new ListenerKey(level.dimension(), pos);
        listeners.computeIfAbsent(sanitized, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public static void unregisterListener(String channel, Level level, BlockPos pos) {
        if (level == null) return;
        String sanitized = sanitizeChannel(channel);

        ListenerKey key = new ListenerKey(level.dimension(), pos);
        Set<ListenerKey> set = listeners.get(sanitized);
        if (set != null) {
            set.remove(key);
            if (set.isEmpty()) {
                listeners.remove(sanitized);
            }
        }
    }

    public static void broadcast(MinecraftServer server, String channel, String value) {
        broadcast(server, channel, value, null, null, 99);
    }

    public static void broadcast(MinecraftServer server, String channel, String value, net.minecraft.resources.ResourceKey<Level> txDimension, BlockPos txPos, int txAntennaHeight) {
        if (server == null) return;
        Level txLevel = txDimension != null ? server.getLevel(txDimension) : server.getLevel(Level.OVERWORLD);
        if (txLevel != null && isChannelJammedAt(txLevel, txPos, channel)) {
            value = "";
        }
        String sanitized = sanitizeChannel(channel);

        String newValue = value != null ? value : "";
        channelValues.put(sanitized, newValue);

        long tick = txLevel != null ? txLevel.getGameTime() : 0L;
        if (txLevel != null) {
            channelLastBroadcastTicks.put(sanitized, tick);
        }

        if (txDimension != null && txPos != null) {
            channelTransmissions.put(sanitized, new TransmissionRecord(newValue, txDimension, txPos, tick));
        } else {
            channelTransmissions.remove(sanitized);
        }

        Set<ListenerKey> channelListeners = listeners.get(sanitized);
        if (channelListeners == null) return;

        int txRange = txAntennaHeight == 0 ? 100 : 100 + txAntennaHeight * 150;
        if (txRange > 3000) txRange = 3000;
        boolean txInfinite = txDimension == null || txPos == null;

        Iterator<ListenerKey> iterator = channelListeners.iterator();
        while (iterator.hasNext()) {
            ListenerKey listener = iterator.next();
            Level level = server.getLevel(listener.dimension());

            if (level == null) {
                continue;
            }

            if (!level.hasChunkAt(listener.pos())) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(listener.pos());
            if (be instanceof MainComputerBlockEntity computer) {

                int rxAntennaHeight = computer.getComputerAntennaHeight();
                int rxRange = rxAntennaHeight * 150;
                boolean rxInfinite = false;

                if (!txInfinite && !rxInfinite) {

                    if (!listener.dimension().equals(txDimension)) {
                        continue;
                    }

                    double distSq = listener.pos().distToCenterSqr(txPos.getX(), txPos.getY(), txPos.getZ());
                    double maxDist = Math.min(3000.0, txRange + rxRange);
                    if (distSq > maxDist * maxDist) {
                        continue;
                    }
                }

                computer.onSignalReceived(sanitized, newValue);
            } else {

                iterator.remove();
            }
        }
    }

    public static double getSignalDistance(Level level, BlockPos rxPos, String channel) {
        if (level == null || level.isClientSide() || rxPos == null) return -1.0;
        String sanitized = sanitizeChannel(channel);
        TransmissionRecord record = channelTransmissions.get(sanitized);
        if (record == null) return -1.0;

        long currentTick = level.getGameTime();
        if (currentTick - record.tick() > 2) {
            return -1.0;
        }

        if (!level.dimension().equals(record.dimension())) {
            return -1.0;
        }

        double dx = rxPos.getX() - record.pos().getX();
        double dy = rxPos.getY() - record.pos().getY();
        double dz = rxPos.getZ() - record.pos().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static void setSignalForTesting(String channel, String value) {
        String sanitized = sanitizeChannel(channel);
        channelValues.put(sanitized, value != null ? value : "");
    }

    public static void setSignalDistanceForTesting(String channel, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, long tick) {
        String sanitized = sanitizeChannel(channel);
        channelTransmissions.put(sanitized, new TransmissionRecord("", dimension, pos, tick));
    }
}
