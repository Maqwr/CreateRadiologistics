package com.radiologistics.create.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import com.radiologistics.create.block.BaseModuleBlock;
import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.RadioTransmitterBlockEntity;
import com.radiologistics.create.block.AntennaBlock;
import com.radiologistics.create.network.PlayerLinkManager;

public class ModBlockItem extends BlockItem {
    public ModBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Block block = this.getBlock();
        if (block instanceof BaseModuleBlock || block instanceof com.radiologistics.create.block.ServoMotorBlock) {
            Level level = context.getLevel();
            net.minecraft.world.entity.player.Player player = context.getPlayer();

            if (player != null) {
                BlockPlaceContext placeContext = new BlockPlaceContext(context);
                if (!placeContext.canPlace()) {
                    return super.useOn(context);
                }
                BlockPos placePos = placeContext.getClickedPos();

                boolean isAntenna = block instanceof AntennaBlock;
                if (isAntenna) {
                    boolean stackedOnAntenna = level.getBlockState(placePos.below()).getBlock() instanceof AntennaBlock;
                    if (stackedOnAntenna) {
                        return super.useOn(context);
                    }
                }

                BlockPos targetPos;
                if (level.isClientSide()) {
                    targetPos = PlayerLinkManager.getClientPendingLink();
                } else if (player instanceof ServerPlayer sp) {
                    targetPos = PlayerLinkManager.getPendingLink(sp.getUUID());
                } else {
                    targetPos = null;
                }

                if (targetPos == null) {
                    if (!level.isClientSide()) {
                        if (isAntenna) {
                            player.displayClientMessage(Component.literal("no transmitter or computer assigned").withStyle(ChatFormatting.RED), true);
                        } else {
                            player.displayClientMessage(Component.literal("no computer assigned").withStyle(ChatFormatting.RED), true);
                        }
                    }
                    return InteractionResult.FAIL;
                }

                net.minecraft.world.phys.Vec3 p1 = MainComputerBlockEntity.getWorldPos(level, placePos);
                net.minecraft.world.phys.Vec3 p2 = MainComputerBlockEntity.getWorldPos(level, targetPos);
                double distSq = p1.distanceToSqr(p2);
                if (distSq > 100.0) {
                    if (!level.isClientSide()) {
                        player.displayClientMessage(Component.literal("too far").withStyle(ChatFormatting.RED), true);
                    }
                    return InteractionResult.FAIL;
                }

                BlockEntity targetBE = BaseModuleBlock.resolveBlockEntity(level, targetPos);
                if (targetBE instanceof MainComputerBlockEntity computer) {
                    String moduleType = (block instanceof BaseModuleBlock mb) ? mb.getModuleType() : "servo_motor";
                    if (!moduleType.equals("jammer") && !moduleType.equals("gyroscope") && !moduleType.equals("screen") && !moduleType.equals("servo_motor") && computer.isModuleConnected(moduleType)) {
                        BlockPos existingPos = computer.getModulePos(moduleType);
                        if (existingPos != null && !placePos.equals(existingPos)) {
                            BlockState existingState = level.getBlockState(existingPos);
                            if (existingState.getBlock() instanceof BaseModuleBlock b && b.getModuleType().equals(moduleType)) {
                                if (!level.isClientSide()) {
                                    player.displayClientMessage(Component.literal("module already connected").withStyle(ChatFormatting.RED), true);
                                }
                                return InteractionResult.FAIL;
                            }
                        }
                    }
                } else if (isAntenna && targetBE instanceof RadioTransmitterBlockEntity) {

                } else {
                    if (!level.isClientSide()) {
                        if (isAntenna) {
                            player.displayClientMessage(Component.literal("no transmitter or computer assigned").withStyle(ChatFormatting.RED), true);
                        } else {
                            player.displayClientMessage(Component.literal("no computer assigned").withStyle(ChatFormatting.RED), true);
                        }
                    }
                    return InteractionResult.FAIL;
                }
            }
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        boolean isShiftDown = false;
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            isShiftDown = ClientHelper.isShiftDown();
        }

        if (isShiftDown) {
            String blockId = getBlock().getDescriptionId();
            if (blockId.startsWith("block.")) {
                String key = "tooltip." + blockId.substring(6);
                String translated = net.minecraft.network.chat.Component.translatable(key).getString();
                if (!translated.equals(key)) {
                    for (String line : translated.split("\n")) {
                        tooltipComponents.add(parseFormatting(line));
                    }
                }
            }
        } else {
            tooltipComponents.add(Component.translatable("tooltip.radiologistics.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static Component parseFormatting(String text) {
        if (!text.contains("§")) {
            return Component.literal(text).withStyle(ChatFormatting.GRAY);
        }
        MutableComponent root = Component.literal("");
        String[] parts = text.split("§");
        if (!parts[0].isEmpty()) {
            root.append(Component.literal(parts[0]).withStyle(ChatFormatting.GRAY));
        }
        ChatFormatting currentStyle = ChatFormatting.GRAY;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            char code = part.charAt(0);
            ChatFormatting format = getFormatByCode(code);
            if (format != null) {
                currentStyle = format;
            }
            if (part.length() > 1) {
                root.append(Component.literal(part.substring(1)).withStyle(currentStyle));
            }
        }
        return root;
    }

    private static ChatFormatting getFormatByCode(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }
}
