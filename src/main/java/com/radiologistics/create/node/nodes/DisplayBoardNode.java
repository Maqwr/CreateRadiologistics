package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import java.util.*;

public class DisplayBoardNode extends AlgoNode {
    private BlockPos boardPos = BlockPos.ZERO;

    /** Last text we successfully sent to the board — skip update if unchanged */
    private String lastSentText = null;

    public DisplayBoardNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() { return "display_board"; }

    @Override
    public List<String> getInputPorts() { return List.of("text"); }

    @Override
    public List<String> getOutputPorts() { return Collections.emptyList(); }

    public BlockPos getBoardPos() { return boardPos; }

    public void setBoardPos(BlockPos boardPos) {
        this.boardPos = boardPos != null ? boardPos : BlockPos.ZERO;
    }

    @Override
    public CompoundTag saveProperties() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("boardPos", boardPos.asLong());
        return tag;
    }

    @Override
    public void loadProperties(CompoundTag tag) {
        if (tag.contains("boardPos")) {
            boardPos = BlockPos.of(tag.getLong("boardPos"));
        } else {
            boardPos = BlockPos.ZERO;
        }
        lastSentText = null; // reset cache when loading
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("text");
        String text = (val != null) ? String.valueOf(val) : "";

        // Skip update if text has not changed — avoids flap animation spam every tick
        if (Objects.equals(text, lastSentText)) return null;

        if (context.getLevel() != null && !context.getLevel().isClientSide()
                && !boardPos.equals(BlockPos.ZERO)) {
            BlockEntity be = com.radiologistics.create.block.MainComputerBlockEntity
                    .resolveBlockEntity(context.getLevel(), boardPos);
            if (be instanceof FlapDisplayBlockEntity displayBoard) {
                FlapDisplayBlockEntity controller = displayBoard.getController();
                if (controller != null) {
                    String[] lines = text.split("\n", -1);
                    for (int i = 0; i < lines.length; i++) {
                        controller.applyTextManually(i, Component.literal(lines[i]));
                    }
                    controller.setChanged();
                    context.getLevel().sendBlockUpdated(
                            controller.getBlockPos(),
                            controller.getBlockState(),
                            controller.getBlockState(), 3);
                    lastSentText = text; // update cache only on success
                }
            } else if (be instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity nixie) {
                String jsonText = Component.Serializer.toJson(Component.literal(text), context.getLevel().registryAccess());
                com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock.walkNixies(
                    context.getLevel(), 
                    boardPos, 
                    false, 
                    (nixiePos, idx) -> {
                        BlockEntity tubeBe = context.getLevel().getBlockEntity(nixiePos);
                        if (tubeBe instanceof com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity tube) {
                            tube.displayCustomText(jsonText, idx);
                            tube.setChanged();
                            context.getLevel().sendBlockUpdated(tube.getBlockPos(), tube.getBlockState(), tube.getBlockState(), 3);
                        }
                    }
                );
                lastSentText = text;
            }
        }
        return null;
    }
}
