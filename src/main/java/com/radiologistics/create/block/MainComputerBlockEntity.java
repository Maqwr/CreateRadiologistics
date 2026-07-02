package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.NodeGraph;
import com.radiologistics.create.node.nodes.*;
import com.radiologistics.create.radio.RadioNetworkManager;
import com.radiologistics.create.item.PilotHelmetItem;
import com.radiologistics.create.network.SyncHelmetGizmosPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class MainComputerBlockEntity extends BlockEntity {
    private net.minecraft.world.item.ItemStack cassette = net.minecraft.world.item.ItemStack.EMPTY;
    private final List<Object> trackedTvMocks = new ArrayList<>();
    private final NodeGraph graph = new NodeGraph();
    private final Map<Direction, Integer> redstoneOutputs = new EnumMap<>(Direction.class);
    private final Set<String> registeredChannels = new HashSet<>();
    private final Map<String, BlockPos> linkedModules = new HashMap<>();
    private final Map<String, VirtualLinkable> activeLinkables = new HashMap<>();

    private double cachedTargetX = 0.0;
    private double cachedTargetY = 0.0;
    private double cachedTargetZ = 0.0;
    private String cachedTargetName = "";
    private int cachedTracksCount = 0;
    private final List<com.radiologistics.create.compat.RadarIntegration.TargetInfo> cachedTargets = new ArrayList<>();
    public List<com.radiologistics.create.compat.RadarIntegration.TargetInfo> getCachedTargets() { return cachedTargets; }
    private int jammedMaxChannel = 0;
    private final List<int[]> activeJammedRanges = new ArrayList<>();
    private String displayLinkText = "";
    private long lastDisplayLinkUpdate = 0;
    private BlockPos displayLinkPos = null;

    private boolean lastJammerConnected = false;
    private int lastJammersCount = 0;
    private final List<int[]> lastJammedRanges = new ArrayList<>();
    private String cameraGizmosJson = "[]";

    public String getCameraGizmosJson() {
        return cameraGizmosJson;
    }

    public void setCameraGizmosJson(String cameraGizmosJson) {
        if (cameraGizmosJson == null) cameraGizmosJson = "[]";
        if (!this.cameraGizmosJson.equals(cameraGizmosJson)) {
            this.cameraGizmosJson = cameraGizmosJson;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private final Set<Long> forcedChunks = new HashSet<>();
    private ResourceKey<Level> forcedLevelKey = null;

    public int getForcedChunksCount() { return forcedChunks.size(); }

    public void releaseAllForcedChunks() {
        if (forcedChunks.isEmpty() || forcedLevelKey == null) {
            forcedChunks.clear();
            forcedLevelKey = null;
            return;
        }

        net.minecraft.server.MinecraftServer mcServer = level != null ? level.getServer() : null;
        if (com.radiologistics.create.Radiologistics.isServerStopping
                || level == null || level.isClientSide()
                || (mcServer != null && !mcServer.isRunning())) {
            forcedChunks.clear();
            forcedLevelKey = null;
            return;
        }
        try {
            net.minecraft.server.level.ServerLevel serverLevel = null;
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                serverLevel = sl;
            } else if (isSableSubLevel(level)) {
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                net.minecraft.world.level.Level parentLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                if (parentLevel instanceof net.minecraft.server.level.ServerLevel sl) {
                    serverLevel = sl;
                }
            }
            if (serverLevel != null && serverLevel.getServer() != null && serverLevel.getServer().isRunning()) {
                net.minecraft.server.level.ServerLevel targetLevel = serverLevel.getServer().getLevel(forcedLevelKey);
                if (targetLevel != null) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("Releasing {} forced chunks in dimension {} for computer at {}", forcedChunks.size(), forcedLevelKey.location(), worldPosition);
                    for (long chunkPosLong : forcedChunks) {
                        int cx = net.minecraft.world.level.ChunkPos.getX(chunkPosLong);
                        int cz = net.minecraft.world.level.ChunkPos.getZ(chunkPosLong);
                        targetLevel.setChunkForced(cx, cz, false);
                    }
                }
            }
        } catch (Exception ignored) {}
        forcedChunks.clear();
        forcedLevelKey = null;
    }

    private void updateForcedChunks(Set<Long> targets, net.minecraft.server.level.ServerLevel targetLevel) {
        if (level == null || level.isClientSide()) return;
        net.minecraft.server.MinecraftServer mcServer = level.getServer();
        if (com.radiologistics.create.Radiologistics.isServerStopping
                || (mcServer != null && !mcServer.isRunning())) {
            forcedChunks.clear();
            forcedLevelKey = null;
            return;
        }
        ResourceKey<Level> targetDim = targetLevel.dimension();

        if (forcedLevelKey != null && !forcedLevelKey.equals(targetDim)) {
            releaseAllForcedChunks();
        }

        forcedLevelKey = targetDim;

        for (long chunkPosLong : new ArrayList<>(forcedChunks)) {
            if (!targets.contains(chunkPosLong)) {
                int cx = net.minecraft.world.level.ChunkPos.getX(chunkPosLong);
                int cz = net.minecraft.world.level.ChunkPos.getZ(chunkPosLong);
                targetLevel.setChunkForced(cx, cz, false);
                forcedChunks.remove(chunkPosLong);
            }
        }

        for (long chunkPosLong : targets) {
            if (!forcedChunks.contains(chunkPosLong)) {
                int cx = net.minecraft.world.level.ChunkPos.getX(chunkPosLong);
                int cz = net.minecraft.world.level.ChunkPos.getZ(chunkPosLong);
                targetLevel.setChunkForced(cx, cz, true);
                forcedChunks.add(chunkPosLong);
            }
        }
        setChanged();
    }

    public String getDisplayLinkText() {
        return displayLinkText;
    }

    public void setDisplayLinkText(String text) {
        if (text == null) text = "";
        if (!this.displayLinkText.equals(text)) {
            this.displayLinkText = text;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                evaluateGraph();
            }
        }
    }

    public void registerDisplayLinkUpdate(BlockPos dlPos) {
        this.displayLinkPos = dlPos;
        registerDisplayLinkUpdate();
    }

    public void registerDisplayLinkUpdate() {
        if (this.level != null) {
            long prev = this.lastDisplayLinkUpdate;
            this.lastDisplayLinkUpdate = this.level.getGameTime();
            if (this.level.getGameTime() - prev >= 40) {
                setChanged();
                if (!this.level.isClientSide()) {
                    this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    public boolean isDisplayLinkConnected() {
        if (level == null) return false;
        if (level.getGameTime() - lastDisplayLinkUpdate < 100) {
            return true;
        }
        if (displayLinkPos != null) {
            BlockEntity be = resolveBlockEntity(level, displayLinkPos);
            if (be instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity dl) {
                if (worldPosition.equals(dl.getTargetPosition())) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<int[]> getActiveJammedRanges() {
        return activeJammedRanges;
    }

    public void clearActiveJammedRanges() {
        activeJammedRanges.clear();
    }

    public void addActiveJammedRange(int start, int end) {
        activeJammedRanges.add(new int[]{start, end});
    }

    public int getJammedMaxChannel() { return jammedMaxChannel; }
    public void setJammedMaxChannel(int val) {
        this.jammedMaxChannel = val;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public double getCachedTargetX() { return cachedTargetX; }
    public double getCachedTargetY() { return cachedTargetY; }
    public double getCachedTargetZ() { return cachedTargetZ; }
    public String getCachedTargetName() { return cachedTargetName; }
    public int getCachedTracksCount() { return cachedTracksCount; }

    public void setCachedTargetX(double val) { this.cachedTargetX = val; }
    public void setCachedTargetY(double val) { this.cachedTargetY = val; }
    public void setCachedTargetZ(double val) { this.cachedTargetZ = val; }
    public void setCachedTargetName(String val) { this.cachedTargetName = val; }
    public void setCachedTracksCount(int val) { this.cachedTracksCount = val; }

    public MainComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAIN_COMPUTER.get(), pos, state);
        for (Direction dir : Direction.values()) {
            redstoneOutputs.put(dir, 0);
        }
    }

    public boolean linkModule(String type, BlockPos pos) {
        if (type.equals("jammer")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("jammer_") && entry.getValue().equals(pos)) {
                    return true;
                }
            }
            int index = 0;
            while (linkedModules.containsKey("jammer_" + index)) {
                index++;
            }
            linkedModules.put("jammer_" + index, pos);
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                evaluateGraph();
            }
            return true;
        }

        if (type.equals("servo_motor")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("servo_motor_") && entry.getValue().equals(pos)) {
                    return true;
                }
            }
            int index = 0;
            while (linkedModules.containsKey("servo_motor_" + index)) {
                index++;
            }
            linkedModules.put("servo_motor_" + index, pos);
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                updateListeners();
                evaluateGraph();
            }
            return true;
        }

        if (type.equals("gyroscope")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("gyroscope_") && entry.getValue().equals(pos)) {
                    return true;
                }
            }
            int index = 0;
            while (linkedModules.containsKey("gyroscope_" + index)) {
                index++;
            }
            linkedModules.put("gyroscope_" + index, pos);
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                evaluateGraph();
            }
            return true;
        }

        if (type.equals("screen")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("screen_") && entry.getValue().equals(pos)) {
                    return true;
                }
            }
            int index = 0;
            while (linkedModules.containsKey("screen_" + index)) {
                index++;
            }
            linkedModules.put("screen_" + index, pos);
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                updateListeners();
                trackCassette();
                evaluateGraph();
            }
            return true;
        }

        if (linkedModules.containsKey(type)) {
            BlockPos oldPos = linkedModules.get(type);
            if (!pos.equals(oldPos)) {
                if (level != null && level.hasChunkAt(oldPos)) {
                    net.minecraft.world.level.block.state.BlockState oldState = level.getBlockState(oldPos);
                    if (oldState.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals(type)) {
                        return false;
                    }
                }
            }
        }
        linkedModules.put(type, pos);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateListeners();
            if (type.equals("screen")) {
                trackCassette();
            }
            evaluateGraph();
        }
        return true;
    }

    public void unlinkModule(String type) {
        BlockPos oldPos = linkedModules.remove(type);
        if (oldPos != null) {
            if (level != null && !level.isClientSide()) {
                BlockEntity moduleBE = resolveBlockEntity(level, oldPos);
                if (moduleBE instanceof IComputerLinkable module) {
                    if (worldPosition.equals(module.getComputerPos())) {
                        module.setComputerPos(null);
                        if (module instanceof ScreenBlockEntity screen) {
                            screen.setGizmosJson("[]");
                        }
                        module.setChanged();
                        level.sendBlockUpdated(oldPos, moduleBE.getBlockState(), moduleBE.getBlockState(), 3);
                    }
                }
            }
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                updateListeners();
                if (type.equals("screen")) {
                    trackCassette();
                }
                evaluateGraph();
            }
        }
    }

    public void unlinkModule(String type, BlockPos pos) {
        if (type.equals("jammer")) {
            String keyToRemove = null;
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("jammer_") && entry.getValue().equals(pos)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                linkedModules.remove(keyToRemove);
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    evaluateGraph();
                }
            }
            return;
        }
        if (type.equals("servo_motor")) {
            String keyToRemove = null;
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("servo_motor_") && entry.getValue().equals(pos)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                linkedModules.remove(keyToRemove);
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    updateListeners();
                    evaluateGraph();
                }
            }
            return;
        }
        if (type.equals("gyroscope")) {
            String keyToRemove = null;
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("gyroscope_") && entry.getValue().equals(pos)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                linkedModules.remove(keyToRemove);
                if (level != null && !level.isClientSide()) {
                    BlockEntity moduleBE = resolveBlockEntity(level, pos);
                    if (moduleBE instanceof GyroscopeSensorBlockEntity gyro) {
                        if (worldPosition.equals(gyro.getComputerPos())) {
                            gyro.setComputerPos(null);
                            gyro.setChanged();
                            level.sendBlockUpdated(pos, moduleBE.getBlockState(), moduleBE.getBlockState(), 3);
                        }
                    }
                }
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    updateListeners();
                    evaluateGraph();
                }
            }
            return;
        }
        if (type.equals("screen")) {
            String keyToRemove = null;
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("screen_") && entry.getValue().equals(pos)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                linkedModules.remove(keyToRemove);
                if (level != null && !level.isClientSide()) {
                    BlockEntity moduleBE = resolveBlockEntity(level, pos);
                    if (moduleBE instanceof ScreenBlockEntity screen) {
                        if (worldPosition.equals(screen.getComputerPos())) {
                            screen.setComputerPos(null);
                            screen.setGizmosJson("[]");
                            screen.setChanged();
                            level.sendBlockUpdated(pos, moduleBE.getBlockState(), moduleBE.getBlockState(), 3);
                        }
                    }
                }
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    updateListeners();
                    trackCassette();
                    evaluateGraph();
                }
            }
            return;
        }
        unlinkModule(type);
    }

    public boolean isModuleConnected(String type) {
        if (type.equals("jammer")) {
            return !getJammers().isEmpty();
        }
        if (type.equals("servo_motor")) {
            return !getServos().isEmpty();
        }
        if (type.equals("gyroscope")) {
            return !getGyroscopes().isEmpty();
        }
        if (type.equals("screen")) {
            return !getScreens().isEmpty();
        }
        if (type.equals("cassette_reader")) {
            return !getCassette().isEmpty();
        }

        BlockPos pos = linkedModules.get(type);
        if (pos == null) return false;

        BlockEntity be = resolveBlockEntity(level, pos);
        if (type.equals("network_controller")) {
            if (be != null && be.getClass().getName().equals("com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity")) {
                return true;
            }
            return false;
        }
        if (type.equals("cannon_mount")) {
            if (be != null && be.getClass().getName().equals("rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity")) {
                return true;
            }
            return false;
        }

        if (be instanceof IComputerLinkable module) {
            net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
            if (state.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals(type)) {
                return true;
            }
            if (type.startsWith("servo_motor") && state.getBlock() instanceof ServoMotorBlock) {
                return true;
            }
        }
        return false;
    }

    public List<BlockPos> getJammers() {
        List<BlockPos> list = new ArrayList<>();
        for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
            if (entry.getKey().startsWith("jammer_")) {
                BlockPos pos = entry.getValue();
                BlockEntity be = resolveBlockEntity(level, pos);
                if (be instanceof IComputerLinkable module) {
                    net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                    if (state.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals("jammer")) {
                        list.add(pos);
                    }
                }
            }
        }
        return list;
    }

    public List<BlockPos> getServos() {
        List<BlockPos> list = new ArrayList<>();
        for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
            if (entry.getKey().startsWith("servo_motor_")) {
                BlockPos pos = entry.getValue();
                BlockEntity be = resolveBlockEntity(level, pos);
                if (be instanceof ServoMotorBlockEntity) {
                    list.add(pos);
                }
            }
        }
        return list;
    }

    public List<BlockPos> getGyroscopes() {
        List<BlockPos> list = new ArrayList<>();
        for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
            if (entry.getKey().startsWith("gyroscope_")) {
                BlockPos pos = entry.getValue();
                BlockEntity be = resolveBlockEntity(level, pos);
                if (be instanceof GyroscopeSensorBlockEntity) {
                    list.add(pos);
                }
            }
        }
        return list;
    }

    public List<BlockPos> getScreens() {
        List<BlockPos> list = new ArrayList<>();
        for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
            if (entry.getKey().startsWith("screen_")) {
                BlockPos pos = entry.getValue();
                BlockEntity be = resolveBlockEntity(level, pos);
                if (be instanceof ScreenBlockEntity) {
                    list.add(pos);
                }
            }
        }
        return list;
    }

    private static boolean reflectionChecked = false;
    private static Class<?> subLevelClass = null;
    private static java.lang.reflect.Method getLevelMethod = null;
    private static Class<?> companionClass = null;
    private static Object companionInstance = null;
    private static java.lang.reflect.Method getContainingMethod = null;

    private static void initReflection() {
        if (reflectionChecked) return;
        reflectionChecked = true;
        try {
            subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            getLevelMethod = subLevelClass.getMethod("getLevel");
        } catch (Throwable ignored) {}
        try {
            companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            companionInstance = companionClass.getField("INSTANCE").get(null);
            getContainingMethod = companionClass.getMethod("getContaining", net.minecraft.world.level.Level.class, net.minecraft.core.Vec3i.class);
        } catch (Throwable ignored) {}
    }

    public static boolean isSableSubLevel(net.minecraft.world.level.Level level) {
        return isSableObject(level);
    }

    public static net.minecraft.world.phys.Vec3 getWorldPos(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null) return net.minecraft.world.phys.Vec3.ZERO;
        BlockEntity be = resolveBlockEntity(level, pos);
        net.minecraft.world.level.Level activeLevel = (be != null && be.getLevel() != null) ? be.getLevel() : level;
        BlockPos activePos = (be != null) ? be.getBlockPos() : pos;
        net.minecraft.world.phys.Vec3 vec = new net.minecraft.world.phys.Vec3(activePos.getX() + 0.5, activePos.getY() + 0.5, activePos.getZ() + 0.5);
        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Object companion = companionClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
            net.minecraft.world.phys.Vec3 projected = (net.minecraft.world.phys.Vec3) projectMethod.invoke(companion, activeLevel, vec);
            if (projected != null) {
                return projected;
            }
        } catch (Exception ignored) {}
        return vec;
    }

    public static boolean isSableObject(Object obj) {
        if (obj == null) return false;
        initReflection();
        if (subLevelClass != null && subLevelClass.isInstance(obj)) {
            return true;
        }

        Class<?> curr = obj.getClass();
        while (curr != null) {
            String name = curr.getName();
            if (name.equals("dev.ryanhcode.sable.sublevel.SubLevel")
                || name.equals("dev.ryanhcode.sable.sublevel.ClientSubLevel")
                || name.equals("dev.ryanhcode.sable.sublevel.ServerSubLevel")
                || name.equals("dev.ryanhcode.sable.companion.SubLevelAccess")) {
                return true;
            }
            curr = curr.getSuperclass();
        }
        return false;
    }

    private static long lastGyroResolveLogTime = 0;

    public static BlockEntity resolveBlockEntity(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null) return null;

        long now = System.currentTimeMillis();
        boolean shouldLog = (now - lastGyroResolveLogTime > 5000);
        if (shouldLog) {
            lastGyroResolveLogTime = now;
            com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: start. level=" + level.getClass().getName() + " pos=" + pos);
        }

        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (shouldLog) {
                com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 1 (local) returned=" + (be != null ? be.getClass().getName() : "null"));
            }
            if (be != null) return be;
        } catch (Throwable ignored) {
            if (shouldLog) {
                com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 1 threw exception");
            }
        }

        if (com.radiologistics.create.Radiologistics.isServerStopping) {
            return null;
        }

        initReflection();

        if (getLevelMethod != null && isSableSubLevel(level)) {
            try {
                net.minecraft.world.level.Level parentLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 4 (parent) level=" + (parentLevel != null ? parentLevel.getClass().getName() : "null"));
                }
                if (parentLevel != null) {
                    BlockEntity be = parentLevel.getBlockEntity(pos);
                    if (shouldLog) {
                        com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 4 (parent) returned=" + (be != null ? be.getClass().getName() : "null"));
                    }
                    if (be != null) return be;
                }
            } catch (Throwable ignored) {
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 4 threw exception");
                }
            }
        }

        if (companionInstance != null && getContainingMethod != null) {
            try {
                Object subLevel = getContainingMethod.invoke(companionInstance, level, pos);
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 5 (containing) subLevel=" + (subLevel != null ? subLevel.getClass().getName() : "null"));
                }
                if (subLevel != null) {
                    try {
                        Object plot = subLevel.getClass().getMethod("getPlot").invoke(subLevel);
                        if (plot != null) {
                            net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(pos);
                            java.lang.reflect.Method getChunkMethod = plot.getClass().getMethod("getChunk", net.minecraft.world.level.ChunkPos.class);
                            net.minecraft.world.level.chunk.LevelChunk chunk = (net.minecraft.world.level.chunk.LevelChunk) getChunkMethod.invoke(plot, chunkPos);
                            if (chunk != null) {
                                BlockEntity be = chunk.getBlockEntity(pos);
                                if (shouldLog) {
                                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 5 (containing chunk) returned=" + (be != null ? be.getClass().getName() : "null"));
                                }
                                if (be != null) return be;
                            }
                        }
                    } catch (Throwable ignored) {
                        if (shouldLog) {
                            com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 5 (containing chunk) threw exception");
                        }
                    }
                }
            } catch (Throwable ignored) {
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG RESOLVE: step 5 threw exception");
                }
            }
        }

        return null;
    }

    public net.minecraft.world.item.ItemStack getCassette() {
        return cassette;
    }

    public void setCassette(net.minecraft.world.item.ItemStack cassette) {
        this.cassette = cassette == null ? net.minecraft.world.item.ItemStack.EMPTY : cassette;
        setChanged();
        if (level != null) {
            if (!level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                trackCassette();
                evaluateGraph();
            }
        }
    }

    public void trackCassette() {
        if (level == null || level.isClientSide()) return;
        for (Object mock : trackedTvMocks) {
            com.radiologistics.create.compat.VistaIntegrationHelper.untrackTv(mock);
        }
        trackedTvMocks.clear();

        if (isSableSubLevel(level)) {
            return;
        }

        if (!cassette.isEmpty()) {

            Object computerMock = com.radiologistics.create.compat.VistaIntegrationHelper.createMockTv(level, worldPosition, getBlockState(), cassette);
            if (computerMock != null) {
                com.radiologistics.create.compat.VistaIntegrationHelper.trackTv(computerMock);
                trackedTvMocks.add(computerMock);
            }

            for (BlockPos screenPos : getScreens()) {
                Object screenMock = com.radiologistics.create.compat.VistaIntegrationHelper.createMockTv(level, screenPos, getBlockState(), cassette);
                if (screenMock != null) {
                    com.radiologistics.create.compat.VistaIntegrationHelper.trackTv(screenMock);
                    trackedTvMocks.add(screenMock);
                }
            }
        }
    }

    public void untrackCassette() {
        for (Object mock : trackedTvMocks) {
            com.radiologistics.create.compat.VistaIntegrationHelper.untrackTv(mock);
        }
        trackedTvMocks.clear();
    }

    public BlockPos getDisplayLinkPos() {
        return displayLinkPos;
    }

    public java.util.Set<String> getConnectedModuleTypes() {
        java.util.Set<String> connected = new HashSet<>();
        for (String type : new String[]{"redstone_link", "memory", "gyroscope", "antenna", "network_controller", "jammer", "audio", "screen", "cannon_mount", "servo_motor"}) {
            if (isModuleConnected(type)) {
                connected.add(type);
            }
        }
        if (!getCassette().isEmpty()) {
            connected.add("cassette_reader");
        }
        if (isDisplayLinkConnected()) {
            connected.add("display_link");
        }
        for (String key : linkedModules.keySet()) {
            if (key.startsWith("display_board_") || key.startsWith("jammer_") || key.startsWith("servo_motor_")
                || key.startsWith("gyroscope_") || key.startsWith("screen_")) {
                connected.add(key);
            }
        }
        boolean helmetLinked = false;
        if (level != null) {
            if (level.isClientSide()) {
                net.minecraft.client.player.LocalPlayer lp = net.minecraft.client.Minecraft.getInstance().player;
                if (lp != null) {
                    net.minecraft.world.item.ItemStack head = lp.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                    if (head.getItem() instanceof com.radiologistics.create.item.PilotHelmetItem && isHelmetLinked(head, worldPosition)) {
                        helmetLinked = true;
                    }
                }
            } else {
                net.minecraft.server.MinecraftServer server = level.getServer();
                if (server != null) {
                    for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                        net.minecraft.world.item.ItemStack head = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                        if (head.getItem() instanceof com.radiologistics.create.item.PilotHelmetItem && isHelmetLinked(head, worldPosition)) {
                            helmetLinked = true;
                            break;
                        }
                    }
                }
            }
        }
        if (helmetLinked) {
            connected.add("helmet");
        }
        return connected;
    }

    public Map<String, BlockPos> getLinkedModules() {
        return linkedModules;
    }

    public void linkDisplayBoard(BlockPos boardPos) {
        String key = "display_board_" + boardPos.getX() + "_" + boardPos.getY() + "_" + boardPos.getZ();
        linkedModules.put(key, boardPos);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            evaluateGraph();
        }
    }

    public boolean isDisplayBoardLinked(BlockPos boardPos) {
        String key = "display_board_" + boardPos.getX() + "_" + boardPos.getY() + "_" + boardPos.getZ();
        return linkedModules.containsKey(key);
    }

    public void unlinkDisplayBoard(BlockPos boardPos) {
        String key = "display_board_" + boardPos.getX() + "_" + boardPos.getY() + "_" + boardPos.getZ();
        if (linkedModules.remove(key) != null) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                evaluateGraph();
            }
        }
    }

    private boolean isHelmetLinked(net.minecraft.world.item.ItemStack head, BlockPos computerPos) {
        net.minecraft.world.item.component.CustomData customData = head.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("LinkedComputer")) {
                return BlockPos.of(tag.getLong("LinkedComputer")).equals(computerPos);
            }
        }
        return false;
    }

    public int getComputerAntennaHeight() {
        BlockPos pos = getModulePos("antenna");
        if (pos != null && level != null) {
            BlockEntity be = resolveBlockEntity(level, pos);
            if (be instanceof AntennaBlockEntity antenna) {
                return antenna.getAntennaHeight();
            }
        }
        return 0;
    }

    public BlockPos getModulePos(String type) {
        if (type.equals("gyroscope")) {
            List<BlockPos> list = getGyroscopes();
            return list.isEmpty() ? null : list.get(0);
        }
        if (type.equals("screen")) {
            List<BlockPos> list = getScreens();
            return list.isEmpty() ? null : list.get(0);
        }
        if (type.equals("jammer")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("jammer_")) {
                    return entry.getValue();
                }
            }
            return null;
        }
        if (type.equals("servo_motor")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("servo_motor_")) {
                    return entry.getValue();
                }
            }
            return null;
        }
        if (linkedModules.containsKey(type)) {
            return linkedModules.get(type);
        }
        return null;
    }

    public NodeGraph getGraph() { return graph; }

    public int getRedstoneOutput(Direction side) {
        return redstoneOutputs.getOrDefault(side, 0);
    }

    public VirtualLinkable getVirtualLinkable(String nodeId) {
        return activeLinkables.get(nodeId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            RadioNetworkManager.activeComputers.add(this);
            updateListeners();
            trackCassette();
            evaluateGraph();
            if (!forcedChunks.isEmpty()) {
                releaseAllForcedChunks();
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        RadioNetworkManager.activeComputers.remove(this);
        if (level != null && !level.isClientSide()) {
            if (com.radiologistics.create.Radiologistics.isServerStopping) {
                untrackCassette();
                forcedChunks.clear();
                forcedLevelKey = null;
                return;
            }
            untrackCassette();
            releaseAllForcedChunks();
            for (VirtualLinkable linkable : activeLinkables.values()) {
                com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkable);
            }
            activeLinkables.clear();
            for (String channel : registeredChannels) {
                RadioNetworkManager.unregisterListener(channel, level, worldPosition);
            }
            registeredChannels.clear();

            for (BlockPos modulePos : new ArrayList<>(linkedModules.values())) {
                BlockEntity moduleBE = resolveBlockEntity(level, modulePos);
                if (moduleBE instanceof IComputerLinkable module) {
                    if (worldPosition.equals(module.getComputerPos())) {
                        module.setComputerPos(null);
                        if (module instanceof ScreenBlockEntity screen) {
                            screen.setGizmosJson("[]");
                        }
                        module.setChanged();
                        level.sendBlockUpdated(modulePos, moduleBE.getBlockState(), moduleBE.getBlockState(), 3);
                    }
                }
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        RadioNetworkManager.activeComputers.remove(this);

        net.minecraft.server.MinecraftServer mcServer = level != null ? level.getServer() : null;
        boolean serverStopping = com.radiologistics.create.Radiologistics.isServerStopping
                || (mcServer != null && !mcServer.isRunning());

        if (serverStopping) {

            trackedTvMocks.clear();
            forcedChunks.clear();
            forcedLevelKey = null;
            return;
        }

        untrackCassette();

        if (level != null && !level.isClientSide() && !forcedChunks.isEmpty() && forcedLevelKey != null) {
            final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey = forcedLevelKey;
            final java.util.List<Long> chunksToRelease = new java.util.ArrayList<>(forcedChunks);
            if (mcServer != null) {
                try {
                    mcServer.execute(() -> {
                        try {
                            net.minecraft.server.level.ServerLevel targetLevel = mcServer.getLevel(levelKey);
                            if (targetLevel != null) {
                                for (long chunkPosLong : chunksToRelease) {
                                    int cx = net.minecraft.world.level.ChunkPos.getX(chunkPosLong);
                                    int cz = net.minecraft.world.level.ChunkPos.getZ(chunkPosLong);
                                    try { targetLevel.setChunkForced(cx, cz, false); } catch (Throwable ignored) {}
                                }
                            }
                        } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}
            }
        }

        forcedChunks.clear();
        forcedLevelKey = null;

        if (level != null && !level.isClientSide()) {
            for (VirtualLinkable linkable : activeLinkables.values()) {
                com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkable);
            }
            activeLinkables.clear();
            for (String channel : registeredChannels) {
                RadioNetworkManager.unregisterListener(channel, level, worldPosition);
            }
            registeredChannels.clear();
        }
    }

    public void onSignalReceived(String channel, String value) {
        if (level != null && !level.isClientSide()) {
            evaluateGraph();
        }
    }

    public void updateListeners() {
        if (level == null || level.isClientSide()) return;

        for (VirtualLinkable linkable : activeLinkables.values()) {
            com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkable);
        }
        activeLinkables.clear();

        for (String ch : registeredChannels) {
            RadioNetworkManager.unregisterListener(ch, level, worldPosition);
        }
        registeredChannels.clear();

        for (AlgoNode node : graph.getNodes().values()) {
            if (node instanceof SignalNode sigNode) {

                String ch = null;
                for (com.radiologistics.create.node.NodeGraph.NodeLink link : graph.getLinks()) {
                    if (link.toNode().equals(sigNode.getId()) && link.toPort().equals("channel")) {
                        AlgoNode src = graph.getNodes().get(link.fromNode());
                        ch = resolveStaticChannel(src);
                        break;
                    }
                }
                if (ch == null) {
                    String legacy = sigNode.getLegacyChannel();
                    ch = RadioNetworkManager.sanitizeChannel(legacy != null ? legacy : "0");
                }
                RadioNetworkManager.registerListener(ch, level, worldPosition);
                registeredChannels.add(ch);
            }
        }

        if (isModuleConnected("redstone_link")) {
            for (AlgoNode node : graph.getNodes().values()) {
                if (node instanceof LinkInputNode lin) {
                    VirtualLinkable linkable = new VirtualLinkable(this, lin.getId(), lin.getFreq1(), lin.getFreq2(), true);
                    activeLinkables.put(lin.getId(), linkable);
                    com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, linkable);
                } else if (node instanceof LinkOutputNode lon) {
                    VirtualLinkable linkable = new VirtualLinkable(this, lon.getId(), lon.getFreq1(), lon.getFreq2(), false);
                    activeLinkables.put(lon.getId(), linkable);
                    com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, linkable);
                }
            }
        }
    }

    public void evaluateGraph() {
        if (level == null || level.isClientSide()) return;

        clearActiveJammedRanges();
        com.radiologistics.create.node.EvaluationContext context = new com.radiologistics.create.node.EvaluationContext(level, worldPosition, this, graph);
        graph.evaluate(context);

        Map<Direction, Integer> newOutputs = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            newOutputs.put(dir, 0);
        }

        for (AlgoNode node : graph.getNodes().values()) {
            if (node instanceof RedstoneOutputNode outNode) {
                Direction dir = parseDirection(outNode.getSide());
                if (dir != null) {
                    int currentMax = newOutputs.getOrDefault(dir, 0);
                    newOutputs.put(dir, Math.max(currentMax, outNode.getPowerLevel()));
                }
            }
        }

        if (isModuleConnected("redstone_link")) {
            for (AlgoNode node : graph.getNodes().values()) {
                if (node instanceof LinkOutputNode lon) {
                    VirtualLinkable linkable = activeLinkables.get(lon.getId());
                    if (linkable != null) {
                        int oldStrength = linkable.getTransmittedStrength();
                        int newStrength = lon.getPowerLevel();
                        if (oldStrength != newStrength) {
                            linkable.setTransmittedStrength(newStrength);
                            com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, linkable);
                        }
                    }
                }
            }

            boolean linkActive = false;
            for (VirtualLinkable linkable : activeLinkables.values()) {
                if (linkable.isListening() && linkable.getReceivedStrength() > 0) {
                    linkActive = true;
                    break;
                }
                if (!linkable.isListening() && linkable.getTransmittedStrength() > 0) {
                    linkActive = true;
                    break;
                }
            }
            BlockPos linkPos = getModulePos("redstone_link");
            if (linkPos != null && level != null) {
                BlockState linkState = level.getBlockState(linkPos);
                if (linkState.getBlock() instanceof RedstoneLinkModuleBlock) {
                    boolean currentlyActive = linkState.getValue(RedstoneLinkModuleBlock.ACTIVE);
                    if (currentlyActive != linkActive) {
                        level.setBlock(linkPos, linkState.setValue(RedstoneLinkModuleBlock.ACTIVE, linkActive), 3);
                    }
                }
            }
        }

        updateRedstoneOutputs(newOutputs);

        boolean hasScreenNode = false;
        boolean hasHelmetScreenNode = false;
        for (AlgoNode node : graph.getNodes().values()) {
            if (node instanceof ScreenNode) {
                hasScreenNode = true;
            } else if (node instanceof HelmetScreenNode) {
                hasHelmetScreenNode = true;
            }
        }

        if (!hasScreenNode && isModuleConnected("screen")) {
            BlockPos screenPos = getModulePos("screen");
            if (screenPos != null) {
                BlockEntity be = resolveBlockEntity(level, screenPos);
                if (be instanceof ScreenBlockEntity screen) {
                    screen.setGizmosJson("[]");
                }
            }
        }

        if (!hasHelmetScreenNode) {
            net.minecraft.server.MinecraftServer server = level.getServer();
            if (server != null) {
                for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                    net.minecraft.world.item.ItemStack head = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                    if (head.getItem() instanceof PilotHelmetItem) {
                        net.minecraft.world.item.component.CustomData customData = head.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        if (customData != null) {
                            CompoundTag tag = customData.copyTag();
                            if (tag.contains("LinkedComputer") && BlockPos.of(tag.getLong("LinkedComputer")).equals(worldPosition)) {
                                PacketDistributor.sendToPlayer(p, new SyncHelmetGizmosPacket(worldPosition, "[]"));
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateRedstoneOutputs(Map<Direction, Integer> newOutputs) {
        boolean changed = false;
        List<Direction> changedDirs = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            int oldVal = redstoneOutputs.getOrDefault(dir, 0);
            int newVal = newOutputs.getOrDefault(dir, 0);
            if (oldVal != newVal) {
                redstoneOutputs.put(dir, newVal);
                changed = true;
                changedDirs.add(dir);
            }
        }
        if (changed) {
            setChanged();
            if (level != null) {

                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());

                for (Direction dir : changedDirs) {
                    level.updateNeighborsAt(worldPosition.relative(dir), getBlockState().getBlock());
                }
                if (!level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    private Direction parseDirection(String side) {
        if (side == null) return null;
        return switch (side.toLowerCase()) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("graph", graph.toNBT());
        if (!cassette.isEmpty()) {
            tag.put("cassette", cassette.save(registries));
        }

        CompoundTag outputsTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            outputsTag.putInt(dir.name(), redstoneOutputs.getOrDefault(dir, 0));
        }
        tag.put("outputs", outputsTag);

        CompoundTag modulesTag = new CompoundTag();
        for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
            BlockPos modulePos = entry.getValue();
            CompoundTag offsetTag = new CompoundTag();
            offsetTag.putInt("dx", modulePos.getX() - worldPosition.getX());
            offsetTag.putInt("dy", modulePos.getY() - worldPosition.getY());
            offsetTag.putInt("dz", modulePos.getZ() - worldPosition.getZ());
            modulesTag.put(entry.getKey(), offsetTag);
        }
        tag.put("linkedModules", modulesTag);

        tag.putDouble("cachedTargetX", cachedTargetX);
        tag.putDouble("cachedTargetY", cachedTargetY);
        tag.putDouble("cachedTargetZ", cachedTargetZ);
        tag.putString("cachedTargetName", cachedTargetName);
        tag.putInt("cachedTracksCount", cachedTracksCount);
        net.minecraft.nbt.ListTag targetsTag = new net.minecraft.nbt.ListTag();
        for (com.radiologistics.create.compat.RadarIntegration.TargetInfo target : cachedTargets) {
            CompoundTag tTag = new CompoundTag();
            tTag.putDouble("x", target.x());
            tTag.putDouble("y", target.y());
            tTag.putDouble("z", target.z());
            tTag.putString("name", target.name());
            targetsTag.add(tTag);
        }
        tag.put("cachedTargets", targetsTag);
        tag.putInt("jammedMaxChannel", jammedMaxChannel);
        tag.putString("displayLinkText", displayLinkText);
        tag.putLong("lastDisplayLinkUpdate", lastDisplayLinkUpdate);
        if (displayLinkPos != null) {
            tag.putInt("displayLinkDx", displayLinkPos.getX() - worldPosition.getX());
            tag.putInt("displayLinkDy", displayLinkPos.getY() - worldPosition.getY());
            tag.putInt("displayLinkDz", displayLinkPos.getZ() - worldPosition.getZ());
        }

        CompoundTag forcedChunksTag = new CompoundTag();
        long[] forcedLongs = forcedChunks.stream().mapToLong(Long::longValue).toArray();
        forcedChunksTag.putLongArray("chunks", forcedLongs);
        if (forcedLevelKey != null) {
            forcedChunksTag.putString("level", forcedLevelKey.location().toString());
        }
        tag.put("forcedChunksData", forcedChunksTag);
        tag.putString("cameraGizmosJson", cameraGizmosJson);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("graph")) {
            graph.loadNBT(tag.getCompound("graph"));
        }
        if (tag.contains("cassette")) {
            this.cassette = net.minecraft.world.item.ItemStack.parse(registries, tag.getCompound("cassette")).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        } else {
            this.cassette = net.minecraft.world.item.ItemStack.EMPTY;
        }

        if (tag.contains("outputs")) {
            CompoundTag outputsTag = tag.getCompound("outputs");
            for (Direction dir : Direction.values()) {
                if (outputsTag.contains(dir.name())) {
                    redstoneOutputs.put(dir, outputsTag.getInt(dir.name()));
                }
            }
        }

        linkedModules.clear();
        if (tag.contains("linkedModules")) {
            CompoundTag modulesTag = tag.getCompound("linkedModules");
            for (String key : modulesTag.getAllKeys()) {
                if (modulesTag.getTagType(key) == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                    CompoundTag offsetTag = modulesTag.getCompound(key);
                    int dx = offsetTag.getInt("dx");
                    int dy = offsetTag.getInt("dy");
                    int dz = offsetTag.getInt("dz");
                    linkedModules.put(key, new BlockPos(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz));
                } else {
                    linkedModules.put(key, BlockPos.of(modulesTag.getLong(key)));
                }
            }
        }

        cachedTargetX = tag.getDouble("cachedTargetX");
        cachedTargetY = tag.getDouble("cachedTargetY");
        cachedTargetZ = tag.getDouble("cachedTargetZ");
        cachedTargetName = tag.getString("cachedTargetName");
        cachedTracksCount = tag.getInt("cachedTracksCount");
        cachedTargets.clear();
        if (tag.contains("cachedTargets", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag targetsTag = tag.getList("cachedTargets", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < targetsTag.size(); i++) {
                CompoundTag tTag = targetsTag.getCompound(i);
                cachedTargets.add(new com.radiologistics.create.compat.RadarIntegration.TargetInfo(
                    tTag.getDouble("x"),
                    tTag.getDouble("y"),
                    tTag.getDouble("z"),
                    tTag.getString("name")
                ));
            }
        }
        jammedMaxChannel = tag.getInt("jammedMaxChannel");
        displayLinkText = tag.getString("displayLinkText");
        lastDisplayLinkUpdate = tag.getLong("lastDisplayLinkUpdate");
        if (tag.contains("displayLinkDx") && tag.contains("displayLinkDy") && tag.contains("displayLinkDz")) {
            int dx = tag.getInt("displayLinkDx");
            int dy = tag.getInt("displayLinkDy");
            int dz = tag.getInt("displayLinkDz");
            displayLinkPos = new BlockPos(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
        } else {
            displayLinkPos = null;
        }

        if (tag.contains("forcedChunksData")) {
            CompoundTag forcedChunksTag = tag.getCompound("forcedChunksData");
            long[] forcedLongs = forcedChunksTag.getLongArray("chunks");
            forcedChunks.clear();
            for (long l : forcedLongs) {
                forcedChunks.add(l);
            }
            if (forcedChunksTag.contains("level")) {
                String levelLoc = forcedChunksTag.getString("level");
                forcedLevelKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(levelLoc));
            }
        }

        if (tag.contains("cameraGizmosJson")) {
            cameraGizmosJson = tag.getString("cameraGizmosJson");
        } else {
            cameraGizmosJson = "[]";
        }

        if (level != null && !level.isClientSide()) {
            updateListeners();
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        loadAdditional(tag, lookupProvider);
    }

    public void tick() {
        if (level != null && !level.isClientSide()) {
            if (level instanceof net.minecraft.server.level.ServerLevel sl && !sl.getServer().isRunning()) {
                return;
            }
            updateCachedTarget();

            if (level.getGameTime() % 10 == 0) {
                net.minecraft.world.phys.Vec3 computerWorldPos = getWorldPos(level, worldPosition);
                List<Map.Entry<String, BlockPos>> toUnlink = new ArrayList<>();
                for (Map.Entry<String, BlockPos> entry : new ArrayList<>(linkedModules.entrySet())) {
                    BlockPos mPos = entry.getValue();
                    net.minecraft.world.phys.Vec3 moduleWorldPos = getWorldPos(level, mPos);
                    if (computerWorldPos.distanceToSqr(moduleWorldPos) > 100.0) {
                        toUnlink.add(entry);
                    }
                }
                for (Map.Entry<String, BlockPos> entry : toUnlink) {
                    if (entry.getKey().startsWith("jammer_")) {
                        unlinkModule("jammer", entry.getValue());
                    } else {
                        unlinkModule(entry.getKey());
                    }
                }
            }

            if (level.getGameTime() % 40 == 0) {
                com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG COMPUTER: pos=" + worldPosition + " linkedModules=" + linkedModules);
            }

            boolean wasConnected = isModuleConnected("jammer");
            int wasJammersCount = getJammers().size();
            List<int[]> wasRanges = new ArrayList<>();
            for (int[] r : activeJammedRanges) {
                wasRanges.add(new int[]{r[0], r[1]});
            }

            evaluateGraph();

            boolean nowConnected = isModuleConnected("jammer");
            int nowJammersCount = getJammers().size();
            List<int[]> nowRanges = activeJammedRanges;

            boolean changed = (wasConnected != nowConnected)
                    || (wasJammersCount != nowJammersCount)
                    || !rangesEqual(wasRanges, nowRanges);

            if (changed) {
                com.radiologistics.create.radio.RadioNetworkManager.updateAllRedstoneLinks(level);
            }
            if (level.getGameTime() % 20 == 0) {

                if (!forcedChunks.isEmpty()) {
                    releaseAllForcedChunks();
                }
            }
        }
    }

    private void updateCachedTarget() {
        boolean targetChanged = false;
        if (isModuleConnected("network_controller")) {
            BlockPos pos = getModulePos("network_controller");
            BlockEntity be = resolveBlockEntity(level, pos);
            if (be != null && net.neoforged.fml.ModList.get().isLoaded("create_radar")) {
                try {
                    Class<?> integrationClass = Class.forName("com.radiologistics.create.compat.RadarIntegration");
                    java.lang.reflect.Method getDetectedTargetsMethod = integrationClass.getMethod("getDetectedTargets", BlockEntity.class);
                    List<?> targets = (List<?>) getDetectedTargetsMethod.invoke(null, be);

                    int count = targets.size();
                    double tx = 0.0;
                    double ty = 0.0;
                    double tz = 0.0;
                    String tname = "";

                    if (count > 0) {
                        Object first = targets.get(0);
                        tx = (double) first.getClass().getMethod("x").invoke(first);
                        ty = (double) first.getClass().getMethod("y").invoke(first);
                        tz = (double) first.getClass().getMethod("z").invoke(first);
                        tname = (String) first.getClass().getMethod("name").invoke(first);
                        if (tname == null) tname = "";
                    }

                    List<com.radiologistics.create.compat.RadarIntegration.TargetInfo> newTargets = new ArrayList<>();
                    int limit = Math.min(count, 16);
                    for (int i = 0; i < limit; i++) {
                        Object t = targets.get(i);
                        double xVal = (double) t.getClass().getMethod("x").invoke(t);
                        double yVal = (double) t.getClass().getMethod("y").invoke(t);
                        double zVal = (double) t.getClass().getMethod("z").invoke(t);
                        String nameVal = (String) t.getClass().getMethod("name").invoke(t);
                        if (nameVal == null) nameVal = "";
                        newTargets.add(new com.radiologistics.create.compat.RadarIntegration.TargetInfo(xVal, yVal, zVal, nameVal));
                    }

                    boolean listChanged = false;
                    if (newTargets.size() != cachedTargets.size()) {
                        listChanged = true;
                    } else {
                        for (int i = 0; i < newTargets.size(); i++) {
                            com.radiologistics.create.compat.RadarIntegration.TargetInfo oldT = cachedTargets.get(i);
                            com.radiologistics.create.compat.RadarIntegration.TargetInfo newT = newTargets.get(i);
                            if (oldT.x() != newT.x() || oldT.y() != newT.y() || oldT.z() != newT.z() || !oldT.name().equals(newT.name())) {
                                listChanged = true;
                                break;
                            }
                        }
                    }

                    if (tx != cachedTargetX || ty != cachedTargetY || tz != cachedTargetZ || !tname.equals(cachedTargetName) || count != cachedTracksCount || listChanged) {
                        cachedTargetX = tx;
                        cachedTargetY = ty;
                        cachedTargetZ = tz;
                        cachedTargetName = tname;
                        cachedTracksCount = count;
                        cachedTargets.clear();
                        cachedTargets.addAll(newTargets);
                        targetChanged = true;
                    }
                } catch (Exception ignored) {}
            } else {
                if (cachedTargetX != 0.0 || cachedTargetY != 0.0 || cachedTargetZ != 0.0 || !cachedTargetName.isEmpty() || cachedTracksCount != 0 || !cachedTargets.isEmpty()) {
                    cachedTargetX = 0.0;
                    cachedTargetY = 0.0;
                    cachedTargetZ = 0.0;
                    cachedTargetName = "";
                    cachedTracksCount = 0;
                    cachedTargets.clear();
                    targetChanged = true;
                }
            }
        } else {
            if (cachedTargetX != 0.0 || cachedTargetY != 0.0 || cachedTargetZ != 0.0 || !cachedTargetName.isEmpty() || cachedTracksCount != 0 || !cachedTargets.isEmpty()) {
                cachedTargetX = 0.0;
                cachedTargetY = 0.0;
                cachedTargetZ = 0.0;
                cachedTargetName = "";
                cachedTracksCount = 0;
                cachedTargets.clear();
                targetChanged = true;
            }
        }
        if (targetChanged) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    private String resolveStaticChannel(AlgoNode node) {
        if (node == null) return null;
        if (node instanceof TextNode tn) {
            return RadioNetworkManager.sanitizeChannel(tn.getText());
        }
        if (node instanceof NumberNode nn) {
            return RadioNetworkManager.sanitizeChannel(nn.getValueString());
        }
        if (node instanceof BoolNode bn) {
            return RadioNetworkManager.sanitizeChannel(bn.getValue() ? "1" : "0");
        }
        if (node instanceof VariableNode vn) {
            return RadioNetworkManager.sanitizeChannel(String.valueOf(graph.getVariables().getOrDefault(vn.getVariableName(), "")));
        }
        return null;
    }

    private boolean rangesEqual(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) return false;
        }
        return true;
    }
}
