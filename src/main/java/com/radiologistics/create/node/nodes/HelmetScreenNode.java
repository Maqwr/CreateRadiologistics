package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.item.PilotHelmetItem;
import com.radiologistics.create.network.SyncHelmetGizmosPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public class HelmetScreenNode extends AlgoNode {
    private String lastGizmosJson = "[]";

    public HelmetScreenNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "helmet_screen"; }

    @Override
    public List<String> getInputPorts() { return List.of("gizmos"); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    public String getLastGizmosJson() {
        return lastGizmosJson;
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putString("lastGizmosJson", lastGizmosJson);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("lastGizmosJson")) {
            lastGizmosJson = tag.getString("lastGizmosJson");
        } else {
            lastGizmosJson = "[]";
        }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("gizmos");
        lastGizmosJson = (val != null) ? String.valueOf(val) : "[]";

        if (context.getComputer() != null && context.getLevel() != null && !context.getLevel().isClientSide()) {
            BlockPos computerPos = context.getComputer().getBlockPos();
            Player wearer = getLinkedWearer(context.getLevel(), computerPos);
            if (wearer instanceof net.minecraft.server.level.ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new SyncHelmetGizmosPacket(computerPos, lastGizmosJson));
            }
        }
        return null;
    }

    private Player getLinkedWearer(net.minecraft.world.level.Level level, BlockPos computerPos) {
        if (level == null || computerPos == null) return null;
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
