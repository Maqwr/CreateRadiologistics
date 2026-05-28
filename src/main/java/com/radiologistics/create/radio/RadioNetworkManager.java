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
    // Maps channel name -> last received message value
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
                    for (int[] range : computer.getActiveJammedRanges()) {
                        if (chVal >= range[0] && chVal <= range[1]) {
                            if (pos == null) {
                                return true;
                            }
                            for (BlockPos jammerPos : computer.getJammers()) {
                                double distSq = pos.distToCenterSqr(jammerPos.getX(), jammerPos.getY(), jammerPos.getZ());
                                if (distSq <= 22500.0) { // 150 * 150 = 22500
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
                    for (BlockPos jammerPos : computer.getJammers()) {
                        double distSq = pos.distToCenterSqr(jammerPos.getX(), jammerPos.getY(), jammerPos.getZ());
                        if (distSq <= 22500.0) { // 150 * 150 = 22500
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Maps channel name -> last broadcast game time tick
    private static final Map<String, Long> channelLastBroadcastTicks = new ConcurrentHashMap<>();

    // Maps channel name -> last transmission record
    private static final Map<String, TransmissionRecord> channelTransmissions = new ConcurrentHashMap<>();

    public record TransmissionRecord(String value, ResourceKey<Level> dimension, BlockPos pos, long tick) {}

    // Record to identify a listener uniquely without leaking Level references
    public record ListenerKey(ResourceKey<Level> dimension, BlockPos pos) {}

    // Maps channel name -> set of listener locations
    private static final Map<String, Set<ListenerKey>> listeners = new ConcurrentHashMap<>();

    /**
     * Clears all channels and listeners. Invoked on server start/stop to prevent leaks across singleplayer worlds.
     */
    public static void clearChannels() {
        channelValues.clear();
        listeners.clear();
        channelLastBroadcastTicks.clear();
        channelTransmissions.clear();
        activeComputers.clear();
    }

    /**
     * Sanitizes a channel name to be a number between 0 and 255.
     */
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

    /**
     * Retrieves the last received signal on a channel.
     */
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

    /**
     * Registers a Main Computer as a listener for updates on a channel.
     */
    public static void registerListener(String channel, Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        String sanitized = sanitizeChannel(channel);
        
        ListenerKey key = new ListenerKey(level.dimension(), pos);
        listeners.computeIfAbsent(sanitized, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    /**
     * Unregisters a Main Computer listener.
     */
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

    /**
     * Broadcasts a wireless message to a channel, updating all active loaded listeners.
     */
    public static void broadcast(MinecraftServer server, String channel, String value) {
        broadcast(server, channel, value, null, null, 99);
    }

    /**
     * Broadcasts a range-checked wireless message to a channel.
     */
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

            // Check if chunk is loaded before trying to access the block entity
            if (!level.hasChunkAt(listener.pos())) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(listener.pos());
            if (be instanceof MainComputerBlockEntity computer) {
                // Range checking logic
                int rxAntennaHeight = computer.getComputerAntennaHeight();
                int rxRange = rxAntennaHeight * 150;
                boolean rxInfinite = false;

                if (!txInfinite && !rxInfinite) {
                    // Check dimension
                    if (!listener.dimension().equals(txDimension)) {
                        continue;
                    }
                    // Check distance
                    double distSq = listener.pos().distToCenterSqr(txPos.getX(), txPos.getY(), txPos.getZ());
                    double maxDist = Math.min(3000.0, txRange + rxRange);
                    if (distSq > maxDist * maxDist) {
                        continue; // Out of range!
                    }
                }

                computer.onSignalReceived(sanitized, newValue);
            } else {
                // If the block entity is no longer a Main Computer, clean up the listener
                iterator.remove();
            }
        }
    }

    /**
     * Gets the Euclidean distance to the transmitter device for the last active signal on a channel.
     */
    public static double getSignalDistance(Level level, BlockPos rxPos, String channel) {
        if (level == null || level.isClientSide() || rxPos == null) return -1.0;
        String sanitized = sanitizeChannel(channel);
        TransmissionRecord record = channelTransmissions.get(sanitized);
        if (record == null) return -1.0;

        // Check if transmission has expired (more than 2 ticks old)
        long currentTick = level.getGameTime();
        if (currentTick - record.tick() > 2) {
            return -1.0;
        }

        // Check dimension
        if (!level.dimension().equals(record.dimension())) {
            return -1.0;
        }

        // Euclidean block-to-block distance
        double dx = rxPos.getX() - record.pos().getX();
        double dy = rxPos.getY() - record.pos().getY();
        double dz = rxPos.getZ() - record.pos().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Directly injects a channel signal value. Used primarily for unit testing.
     */
    public static void setSignalForTesting(String channel, String value) {
        String sanitized = sanitizeChannel(channel);
        channelValues.put(sanitized, value != null ? value : "");
    }

    /**
     * Directly injects a transmission record for testing signal distance calculations.
     */
    public static void setSignalDistanceForTesting(String channel, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, long tick) {
        String sanitized = sanitizeChannel(channel);
        channelTransmissions.put(sanitized, new TransmissionRecord("", dimension, pos, tick));
    }
}
