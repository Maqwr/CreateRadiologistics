package com.radiologistics.create.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import com.radiologistics.create.block.MainComputerBlock;

public class PilotHelmetItem extends com.simibubi.create.content.equipment.armor.DivingHelmetItem {
    public PilotHelmetItem(Item.Properties properties) {
        super(ArmorMaterials.NETHERITE, properties, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("radiologistics", "textures/models/armor/pilot_helmet.png"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        
        boolean isShiftDown = false;
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            isShiftDown = ClientHelper.isShiftDown();
        }

        if (isShiftDown) {
            String key = "tooltip.radiologistics.pilot_helmet";
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
    public net.minecraft.resources.ResourceLocation getArmorTexture(
        ItemStack stack,
        net.minecraft.world.entity.Entity entity,
        net.minecraft.world.entity.EquipmentSlot slot,
        net.minecraft.world.item.ArmorMaterial.Layer layer,
        boolean inner
    ) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            "radiologistics", "textures/models/armor/pilot_helmet.png"
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null) {
            BlockPos targetPos = context.getClickedPos();
            if (context.getLevel().getBlockState(targetPos).getBlock() instanceof MainComputerBlock) {
                if (!context.getLevel().isClientSide()) {
                    ItemStack stack = context.getItemInHand();
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> {
                        tag.putLong("LinkedComputer", targetPos.asLong());
                    });
                    player.displayClientMessage(Component.translatable("tooltip.radiologistics.helmet_linked", targetPos.toShortString()).withStyle(ChatFormatting.GREEN), true);
                }
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
            }
        }
        return super.useOn(context);
    }
}
