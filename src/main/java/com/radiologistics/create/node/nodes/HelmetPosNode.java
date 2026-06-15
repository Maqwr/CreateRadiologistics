package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.item.PilotHelmetItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.*;

public class HelmetPosNode extends AlgoNode {
    public HelmetPosNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "helmet_pos"; }

    @Override
    public List<String> getInputPorts() { return Collections.emptyList(); }

    @Override
    public List<String> getOutputPorts() { return List.of("x", "y", "z"); }

    @Override
    public CompoundTag saveProperties() { return new CompoundTag(); }

    @Override
    public void loadProperties(CompoundTag tag) {}

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (context.getComputer() != null) {
            BlockPos computerPos = context.getComputer().getBlockPos();
            Player player = getLinkedWearer(context.getLevel(), computerPos);
            if (player != null) {
                if (outputPort.equals("x")) return player.getX();
                if (outputPort.equals("y")) return player.getY();
                if (outputPort.equals("z")) return player.getZ();
            }
        }
        return 0.0;
    }

    private Player getLinkedWearer(net.minecraft.world.level.Level level, BlockPos computerPos) {
        if (level == null || computerPos == null) return null;
        if (level.isClientSide()) {
            Player lp = net.minecraft.client.Minecraft.getInstance().player;
            if (lp != null) {
                net.minecraft.world.item.ItemStack head = lp.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                if (head.getItem() instanceof PilotHelmetItem && isLinked(head, computerPos)) {
                    return lp;
                }
            }
            return null;
        }
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server != null) {
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                net.minecraft.world.item.ItemStack head = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                if (head.getItem() instanceof PilotHelmetItem && isLinked(head, computerPos)) {
                    return p;
                }
            }
        }
        return null;
    }

    private boolean isLinked(net.minecraft.world.item.ItemStack head, BlockPos computerPos) {
        net.minecraft.world.item.component.CustomData customData = head.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("LinkedComputer")) {
                return BlockPos.of(tag.getLong("LinkedComputer")).equals(computerPos);
            }
        }
        return false;
    }
}
