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

import java.util.*;

public class MainComputerBlockEntity extends BlockEntity {
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
            evaluateGraph();
        }
        return true;
    }

    public void unlinkModule(String type) {
        if (linkedModules.remove(type) != null) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                updateListeners(); // Update listeners when module is unlinked
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

        BlockPos pos = linkedModules.get(type);
        if (pos == null) return false;
        
        BlockEntity be = resolveBlockEntity(level, pos);
        if (type.equals("network_controller")) {
            if (be != null && be.getClass().getName().equals("com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity")) {
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

    public static BlockEntity resolveBlockEntity(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) return be;
        try {
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            if (subLevelClass.isInstance(level)) {
                java.lang.reflect.Method getLevelMethod = subLevelClass.getMethod("getLevel");
                net.minecraft.world.level.Level parentLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(level);
                if (parentLevel != null) {
                    be = parentLevel.getBlockEntity(pos);
                    if (be != null) return be;
                }
            }
        } catch (Exception ignored) {}
        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Object companion = companionClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method getContainingMethod = companionClass.getMethod("getContaining", net.minecraft.world.level.Level.class, net.minecraft.core.Vec3i.class);
            Object subLevelAccess = getContainingMethod.invoke(companion, level, pos);
            if (subLevelAccess instanceof net.minecraft.world.level.Level subLevel) {
                be = subLevel.getBlockEntity(pos);
                if (be != null) return be;
            }
        } catch (Exception ignored) {}
        return null;
    }


    public java.util.Set<String> getConnectedModuleTypes() {
        java.util.Set<String> connected = new HashSet<>();
        for (String type : new String[]{"redstone_link", "memory", "gyroscope", "antenna", "network_controller", "jammer", "audio"}) {
            if (isModuleConnected(type)) {
                connected.add(type);
            }
        }
        return connected;
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
            evaluateGraph();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        RadioNetworkManager.activeComputers.remove(this);
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

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        RadioNetworkManager.activeComputers.remove(this);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("graph")) {
            graph.loadNBT(tag.getCompound("graph"));
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
            updateCachedTarget();
            evaluateGraph();
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
}
