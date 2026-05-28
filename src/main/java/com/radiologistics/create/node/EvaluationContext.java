package com.radiologistics.create.node;

import com.radiologistics.create.block.MainComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class EvaluationContext {
    private final Level level;
    private final BlockPos pos;
    private final MainComputerBlockEntity computer;
    private final NodeGraph graph;

    public EvaluationContext(Level level, BlockPos pos, MainComputerBlockEntity computer, NodeGraph graph) {
        this.level = level;
        this.pos = pos;
        this.computer = computer;
        this.graph = graph;
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public MainComputerBlockEntity getComputer() {
        return computer;
    }

    public NodeGraph getGraph() {
        return graph;
    }
}
