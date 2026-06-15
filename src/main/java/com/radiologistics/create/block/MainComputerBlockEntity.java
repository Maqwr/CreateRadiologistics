package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.NodeGraph;
import com.radiologistics.create.node.nodes.*;
import com.radiologistics.create.radio.RadioNetworkManager;

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

    /** Diagnostic helper — used by shutdown logging in Radiologistics.java */
    public int getForcedChunksCount() { return forcedChunks.size(); }

    public void releaseAllForcedChunks() {
        if (forcedChunks.isEmpty() || forcedLevelKey == null) {
            forcedChunks.clear();
            forcedLevelKey = null;
            return;
        }
        // During shutdown the ChunkHolders are already being deallocated;
        // calling setChunkForced() at that point causes a NullPointerException
        // in ChunkMap.acquireGeneration which crashes the save sequence.
        // If the server is stopping or already stopped, just clear our bookkeeping.
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
            } else {
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                if (subLevelClass.isInstance(level)) {
                    java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                    net.minecraft.world.level.Level parentLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                    if (parentLevel instanceof net.minecraft.server.level.ServerLevel sl) {
                        serverLevel = sl;
                    }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        if (linkedModules.containsKey(type)) {
            BlockPos oldPos = linkedModules.get(type);
            if (level != null && level.hasChunkAt(oldPos)) {
                net.minecraft.world.level.block.state.BlockState oldState = level.getBlockState(oldPos);
                if (oldState.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals(type)) {
                    return false;
                }
            }
        }
        linkedModules.put(type, pos);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateListeners(); // Update listeners when module is linked
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
                if (moduleBE instanceof BaseModuleBlockEntity module) {
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
                updateListeners(); // Update listeners when module is unlinked
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
        unlinkModule(type);
    }

    public boolean isModuleConnected(String type) {
        if (type.equals("jammer")) {
            return !getJammers().isEmpty();
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

        if (be instanceof BaseModuleBlockEntity module) {
            net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
            if (state.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals(type)) {
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
                if (be instanceof BaseModuleBlockEntity module) {
                    net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                    if (state.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals("jammer")) {
                        list.add(pos);
                    }
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

    public static BlockEntity resolveBlockEntity(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        
        // 1. Check local level first (if chunk is loaded)
        if (level.hasChunkAt(pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) return be;
        }
        
        // 2. If server is stopping, bypass reflection/sublevel lookups
        if (com.radiologistics.create.Radiologistics.isServerStopping) {
            return null;
        }

        // 3. Initialize reflection
        initReflection();

        // 4. Try sublevel parent check
        if (subLevelClass != null && getLevelMethod != null && subLevelClass.isInstance(level)) {
            try {
                net.minecraft.world.level.Level parentLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                if (parentLevel != null && parentLevel.hasChunkAt(pos)) {
                    BlockEntity be = parentLevel.getBlockEntity(pos);
                    if (be != null) return be;
                }
            } catch (Throwable ignored) {}
        }

        // 5. Try SableCompanion containing check
        if (companionInstance != null && getContainingMethod != null) {
            try {
                Object subLevelAccess = getContainingMethod.invoke(companionInstance, level, pos);
                if (subLevelAccess instanceof net.minecraft.world.level.Level subLevel) {
                    if (subLevel.hasChunkAt(pos)) {
                        BlockEntity be = subLevel.getBlockEntity(pos);
                        if (be != null) return be;
                    }
                }
            } catch (Throwable ignored) {}
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

        if (!cassette.isEmpty()) {
            // 1. Track at computer position
            Object computerMock = com.radiologistics.create.compat.VistaIntegrationHelper.createMockTv(level, worldPosition, getBlockState(), cassette);
            if (computerMock != null) {
                com.radiologistics.create.compat.VistaIntegrationHelper.trackTv(computerMock);
                trackedTvMocks.add(computerMock);
            }

            // 2. Track at screen position if connected
            if (isModuleConnected("screen")) {
                BlockPos screenPos = linkedModules.get("screen");
                if (screenPos != null) {
                    Object screenMock = com.radiologistics.create.compat.VistaIntegrationHelper.createMockTv(level, screenPos, getBlockState(), cassette);
                    if (screenMock != null) {
                        com.radiologistics.create.compat.VistaIntegrationHelper.trackTv(screenMock);
                        trackedTvMocks.add(screenMock);
                    }
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
        for (String type : new String[]{"redstone_link", "memory", "gyroscope", "antenna", "network_controller", "jammer", "audio", "screen", "cannon_mount"}) {
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
            if (key.startsWith("display_board_") || key.startsWith("jammer_")) {
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
        if (type.equals("jammer")) {
            for (Map.Entry<String, BlockPos> entry : linkedModules.entrySet()) {
                if (entry.getKey().startsWith("jammer_")) {
                    return entry.getValue();
                }
            }
            return null;
        }
        if (isModuleConnected(type)) {
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
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        RadioNetworkManager.activeComputers.remove(this);
        if (level != null && !level.isClientSide()) {
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

            // Disconnect all linked modules when computer is broken or removed
            for (BlockPos modulePos : new ArrayList<>(linkedModules.values())) {
                BlockEntity moduleBE = resolveBlockEntity(level, modulePos);
                if (moduleBE instanceof BaseModuleBlockEntity module) {
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
            // During shutdown ChunkHolders may already be null — just discard bookkeeping.
            trackedTvMocks.clear();
            forcedChunks.clear();
            forcedLevelKey = null;
            return;
        }

        untrackCassette();

        // Release forced chunks synchronously on the server tick thread (we are already on it).
        if (level != null && !level.isClientSide() && !forcedChunks.isEmpty() && forcedLevelKey != null) {
            try {
                net.minecraft.server.level.ServerLevel serverLevel = null;
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    serverLevel = sl;
                }
                if (serverLevel != null) {
                    net.minecraft.server.level.ServerLevel targetLevel = serverLevel.getServer().getLevel(forcedLevelKey);
                    if (targetLevel != null) {
                        for (long chunkPosLong : new java.util.ArrayList<>(forcedChunks)) {
                            int cx = net.minecraft.world.level.ChunkPos.getX(chunkPosLong);
                            int cz = net.minecraft.world.level.ChunkPos.getZ(chunkPosLong);
                            try { targetLevel.setChunkForced(cx, cz, false); } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
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
                // Try to find the channel from a connected Node (static/inline wiring)
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

        // Update transmitting linkables
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
                // Notify the immediate neighbors adjacent to the computer pos
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                // Notify the secondary neighbors of the changed direction
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

    public void tick() {
        if (level != null && !level.isClientSide()) {
            if (level instanceof net.minecraft.server.level.ServerLevel sl && !sl.getServer().isRunning()) {
                return;
            }
            updateCachedTarget();
            
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
                try {
                    Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                    if (subLevelClass.isInstance(level)) {
                        java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                        net.minecraft.world.level.Level parentLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                        if (parentLevel instanceof net.minecraft.server.level.ServerLevel serverParentLevel) {
                            Set<Long> targets = new HashSet<>();
                            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                            Object companion = companionClass.getField("INSTANCE").get(null);
                            java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
                            net.minecraft.world.phys.Vec3 projected = (net.minecraft.world.phys.Vec3) projectMethod.invoke(companion, level, new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5));
                            if (projected != null) {
                                int pX = ((int) Math.floor(projected.x)) >> 4;
                                int pZ = ((int) Math.floor(projected.z)) >> 4;
                                // Force a 3x3 chunk grid around the ship's parent-world position
                                for (int dx = -1; dx <= 1; dx++) {
                                    for (int dz = -1; dz <= 1; dz++) {
                                        targets.add(net.minecraft.world.level.ChunkPos.asLong(pX + dx, pZ + dz));
                                    }
                                }
                            }
                            updateForcedChunks(targets, serverParentLevel);
                            return;
                        }
                    }

                } catch (Exception ignored) {}
                
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    Set<Long> targets = new HashSet<>();
                    int cx = worldPosition.getX() >> 4;
                    int cz = worldPosition.getZ() >> 4;
                    targets.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                    updateForcedChunks(targets, serverLevel);
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
                    java.lang.reflect.Method getTargetInfoMethod = integrationClass.getMethod("getTargetInfo", BlockEntity.class);
                    Object info = getTargetInfoMethod.invoke(null, be);
                    double tx = 0.0;
                    double ty = 0.0;
                    double tz = 0.0;
                    String tname = "";
                    if (info != null) {
                        tx = (double) info.getClass().getMethod("x").invoke(info);
                        ty = (double) info.getClass().getMethod("y").invoke(info);
                        tz = (double) info.getClass().getMethod("z").invoke(info);
                        tname = (String) info.getClass().getMethod("name").invoke(info);
                        if (tname == null) tname = "";
                    }
                    if (tx != cachedTargetX || ty != cachedTargetY || tz != cachedTargetZ || !tname.equals(cachedTargetName)) {
                        cachedTargetX = tx;
                        cachedTargetY = ty;
                        cachedTargetZ = tz;
                        cachedTargetName = tname;
                        targetChanged = true;
                    }
                } catch (Exception ignored) {}
            } else {
                if (cachedTargetX != 0.0 || cachedTargetY != 0.0 || cachedTargetZ != 0.0 || !cachedTargetName.isEmpty()) {
                    cachedTargetX = 0.0;
                    cachedTargetY = 0.0;
                    cachedTargetZ = 0.0;
                    cachedTargetName = "";
                    targetChanged = true;
                }
            }
        } else {
            if (cachedTargetX != 0.0 || cachedTargetY != 0.0 || cachedTargetZ != 0.0 || !cachedTargetName.isEmpty()) {
                cachedTargetX = 0.0;
                cachedTargetY = 0.0;
                cachedTargetZ = 0.0;
                cachedTargetName = "";
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
