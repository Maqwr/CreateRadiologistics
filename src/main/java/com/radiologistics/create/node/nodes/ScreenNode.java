package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import com.radiologistics.create.block.ScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

public class ScreenNode extends AlgoNode {
    private int screenIndex = 0;

    public ScreenNode(String id, double x, double y) {
        super(id, x, y);
    }

    public int getScreenIndex() { return screenIndex; }
    public void setScreenIndex(int index) { this.screenIndex = index; }

    @Override
    public String getType() { return "screen"; }

    @Override
    public List<String> getInputPorts() { return List.of("gizmos"); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("screenIndex", screenIndex);
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("screenIndex")) {
            screenIndex = tag.getInt("screenIndex");
        } else {
            screenIndex = 0;
        }
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("gizmos");
        String gizmosJson = (val != null) ? String.valueOf(val) : "[]";

        if (context.getComputer() != null && context.getLevel() != null && !context.getLevel().isClientSide()) {
            BlockPos screenPos = context.getComputer().getModulePos("screen_" + screenIndex);
            if (screenPos != null) {
                BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity.resolveBlockEntity(context.getLevel(), screenPos);
                if (be instanceof ScreenBlockEntity screen) {
                    screen.setGizmosJson(gizmosJson);
                }
            }
        }
        return null;
    }
}
