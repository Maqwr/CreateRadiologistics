package com.radiologistics.create.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.radiologistics.create.block.MainComputerBlockEntity;
import com.radiologistics.create.block.BaseModuleBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

public class WireItem extends Item {
    public WireItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        boolean isShiftDown = false;
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            isShiftDown = ClientHelper.isShiftDown();
        }

        if (isShiftDown) {
            String key = "tooltip.radiologistics.wire";
            String translated = net.minecraft.network.chat.Component.translatable(key).getString();
            if (!translated.equals(key)) {
                for (String line : translated.split("\n")) {
                    tooltipComponents.add(ModBlockItem.parseFormatting(line));
                }
            }
        } else {
            tooltipComponents.add(Component.translatable("tooltip.radiologistics.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return super.useOn(context);
        }

        BlockPos targetPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        BlockEntity targetBe = context.getLevel().getBlockEntity(targetPos);

        boolean isCannonMount = false;
        try {
            Class<?> cmClass = Class.forName("rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity");
            if (cmClass.isInstance(targetBe)) {
                isCannonMount = true;
            }
        } catch (ClassNotFoundException ignored) {}

        boolean isComputer = targetBe instanceof MainComputerBlockEntity;
        boolean isModule = targetBe instanceof BaseModuleBlockEntity || targetBe instanceof com.radiologistics.create.block.ServoMotorBlockEntity || isCannonMount;
        boolean isDisplay = targetBe instanceof FlapDisplayBlockEntity;
        boolean isNixie = targetBe instanceof NixieTubeBlockEntity;
        boolean isDisplayLink = targetBe instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;

        if (!isComputer && !isModule && !isDisplay && !isNixie && !isDisplayLink) {
            return super.useOn(context);
        }

        net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();

        if (!tag.contains("SelectedPos")) {
            BlockPos selectPos = null;
            if (isComputer) {
                selectPos = targetPos;
            } else if (targetBe instanceof BaseModuleBlockEntity) {
                selectPos = ((BaseModuleBlockEntity) targetBe).getComputerPos();
            } else if (targetBe instanceof com.radiologistics.create.block.ServoMotorBlockEntity) {
                selectPos = ((com.radiologistics.create.block.ServoMotorBlockEntity) targetBe).getComputerPos();
            } else if (isCannonMount) {
                selectPos = findComputerLinkedTo(targetPos);
            } else {
                selectPos = findLinkingComputer(context.getLevel(), targetPos);
            }

            if (selectPos == null) {
                selectPos = targetPos;
            }

            final BlockPos toSelect = selectPos;
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> {
                t.putLong("SelectedPos", toSelect.asLong());
            });
            if (!context.getLevel().isClientSide()) {
                player.displayClientMessage(Component.literal("Computer selected"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        BlockPos selectedPos = BlockPos.of(tag.getLong("SelectedPos"));

        if (selectedPos.equals(targetPos)) {

            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> {
                t.remove("SelectedPos");
                t.remove("LinkedComputer");
            });
            if (!context.getLevel().isClientSide()) {
                player.displayClientMessage(Component.literal("Selection cleared"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        BlockEntity selectedBe = context.getLevel().getBlockEntity(selectedPos);
        if (!(selectedBe instanceof MainComputerBlockEntity computer)) {

            BlockPos selectPos = isComputer ? targetPos :
                (targetBe instanceof BaseModuleBlockEntity ? ((BaseModuleBlockEntity) targetBe).getComputerPos() :
                (targetBe instanceof com.radiologistics.create.block.ServoMotorBlockEntity ? ((com.radiologistics.create.block.ServoMotorBlockEntity) targetBe).getComputerPos() :
                findLinkingComputer(context.getLevel(), targetPos)));
            if (selectPos == null) {
                selectPos = targetPos;
            }
            final BlockPos toSelect = selectPos;
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> {
                t.putLong("SelectedPos", toSelect.asLong());
            });
            if (!context.getLevel().isClientSide()) {
                player.displayClientMessage(Component.literal("Computer selected"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        if (isComputer) {
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> {
                t.putLong("SelectedPos", targetPos.asLong());
            });
            if (!context.getLevel().isClientSide()) {
                player.displayClientMessage(Component.literal("Computer selected"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        net.minecraft.world.phys.Vec3 computerWorldPos = MainComputerBlockEntity.getWorldPos(context.getLevel(), computer.getBlockPos());
        net.minecraft.world.phys.Vec3 targetWorldPos = MainComputerBlockEntity.getWorldPos(context.getLevel(), targetPos);
        double distSq = computerWorldPos.distanceToSqr(targetWorldPos);
        if (distSq > 100.0) {
            return InteractionResult.FAIL;
        }

        if (!context.getLevel().isClientSide()) {
            if (targetBe instanceof BaseModuleBlockEntity module) {
                net.minecraft.world.level.block.state.BlockState mState = context.getLevel().getBlockState(targetPos);
                if (mState.getBlock() instanceof com.radiologistics.create.block.BaseModuleBlock moduleBlock) {
                    String type = moduleBlock.getModuleType();
                    if (computer.getBlockPos().equals(module.getComputerPos())) {

                        if (type.equals("jammer") || type.equals("gyroscope") || type.equals("screen")) {
                            computer.unlinkModule(type, targetPos);
                        } else {
                            computer.unlinkModule(type);
                        }
                        module.setComputerPos(null);
                        if (module instanceof com.radiologistics.create.block.ScreenBlockEntity screen) {
                            screen.setGizmosJson("[]");
                        }
                        module.setChanged();
                        player.displayClientMessage(Component.literal("Module disconnected"), true);
                    } else {

                        if (module.getComputerPos() != null) {
                            BlockEntity prevCompBe = context.getLevel().getBlockEntity(module.getComputerPos());
                            if (prevCompBe instanceof MainComputerBlockEntity prevComputer) {
                                if (type.equals("jammer") || type.equals("gyroscope") || type.equals("screen")) {
                                    prevComputer.unlinkModule(type, targetPos);
                                } else {
                                    prevComputer.unlinkModule(type);
                                }
                            }
                        }
                        boolean success = computer.linkModule(type, targetPos);
                        if (success) {
                            module.setComputerPos(computer.getBlockPos());
                            module.setChanged();
                            player.displayClientMessage(Component.literal("Module connected"), true);
                        }
                    }
                }
            } else if (targetBe instanceof com.radiologistics.create.block.ServoMotorBlockEntity module) {
                if (computer.getBlockPos().equals(module.getComputerPos())) {

                    computer.unlinkModule("servo_motor", targetPos);
                    module.setComputerPos(null);
                    module.setChanged();
                    player.displayClientMessage(Component.literal("Module disconnected"), true);
                } else {

                    if (module.getComputerPos() != null) {
                        BlockEntity prevCompBe = context.getLevel().getBlockEntity(module.getComputerPos());
                        if (prevCompBe instanceof MainComputerBlockEntity prevComputer) {
                            prevComputer.unlinkModule("servo_motor", targetPos);
                        }
                    }
                    boolean success = computer.linkModule("servo_motor", targetPos);
                    if (success) {
                        module.setComputerPos(computer.getBlockPos());
                        module.setChanged();
                        player.displayClientMessage(Component.literal("Module connected"), true);
                    }
                }
            } else if (isCannonMount) {
                BlockPos existingPos = computer.getModulePos("cannon_mount");
                if (targetPos.equals(existingPos)) {
                    computer.unlinkModule("cannon_mount");
                    player.displayClientMessage(Component.literal("Module disconnected"), true);
                } else {
                    for (MainComputerBlockEntity activeComp : com.radiologistics.create.radio.RadioNetworkManager.activeComputers) {
                        if (targetPos.equals(activeComp.getModulePos("cannon_mount"))) {
                            activeComp.unlinkModule("cannon_mount");
                        }
                    }
                    boolean success = computer.linkModule("cannon_mount", targetPos);
                    if (success) {
                        player.displayClientMessage(Component.literal("Module connected"), true);
                    }
                }
            } else if (targetBe instanceof FlapDisplayBlockEntity || targetBe instanceof NixieTubeBlockEntity) {
                if (computer.isDisplayBoardLinked(targetPos)) {
                    computer.unlinkDisplayBoard(targetPos);
                    player.displayClientMessage(Component.literal("Module disconnected"), true);
                } else {
                    computer.linkDisplayBoard(targetPos);
                    player.displayClientMessage(Component.literal("Module connected"), true);
                }
            } else if (targetBe instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity dl) {
                if (targetPos.equals(computer.getDisplayLinkPos())) {
                    dl.target(targetPos);
                    dl.setChanged();
                    dl.getLevel().sendBlockUpdated(targetPos, dl.getBlockState(), dl.getBlockState(), 3);
                    computer.registerDisplayLinkUpdate(null);
                    player.displayClientMessage(Component.literal("Module disconnected"), true);
                } else {
                    dl.target(computer.getBlockPos());
                    dl.setChanged();
                    dl.getLevel().sendBlockUpdated(targetPos, dl.getBlockState(), dl.getBlockState(), 3);
                    computer.registerDisplayLinkUpdate(targetPos);
                    player.displayClientMessage(Component.literal("Module connected"), true);
                }
            }
        }

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private BlockPos findLinkingComputer(net.minecraft.world.level.Level level, BlockPos targetPos) {
        int radius = 16;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = targetPos.offset(x, y, z);
                    BlockEntity checkBe = level.getBlockEntity(checkPos);
                    if (checkBe instanceof MainComputerBlockEntity computer) {
                        if (computer.isDisplayBoardLinked(targetPos)) {
                            return checkPos;
                        }
                        if (targetPos.equals(computer.getDisplayLinkPos())) {
                            return checkPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findComputerLinkedTo(BlockPos pos) {
        for (MainComputerBlockEntity computer : com.radiologistics.create.radio.RadioNetworkManager.activeComputers) {
            if (pos.equals(computer.getModulePos("cannon_mount"))) {
                return computer.getBlockPos();
            }
        }
        return null;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        if (tag.contains("SelectedPos") || tag.contains("LinkedComputer")) {
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> {
                t.remove("SelectedPos");
                t.remove("LinkedComputer");
            });
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("Selection cleared"), true);
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }
}
