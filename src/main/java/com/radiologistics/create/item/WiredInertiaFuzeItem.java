package com.radiologistics.create.item;

import rbasamoyai.createbigcannons.munitions.fuzes.InertiaFuzeItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;
import java.util.List;

public class WiredInertiaFuzeItem extends InertiaFuzeItem {

    public WiredInertiaFuzeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean onRedstoneSignal(ItemStack stack, Level level, BlockPos pos, BlockState state, int signalStrength, Direction direction) {
        if (signalStrength > 0 && !level.isClientSide()) {
            if (state.getBlock() instanceof FuzedProjectileBlock<?, ?> fuzedBlock) {
                AbstractBigCannonProjectile projectile = fuzedBlock.getProjectile(level, pos, state);
                if (projectile != null) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    
                    Direction facing = state.hasProperty(FuzedProjectileBlock.FACING) ? state.getValue(FuzedProjectileBlock.FACING) : Direction.UP;
                    Vec3 orientation = new Vec3(facing.step());
                    
                    try {
                        orientation = rbasamoyai.createbigcannons.CBCCompatTransformers.transformLocationNormal(level, pos, orientation);
                    } catch (Throwable ignored) {}

                    projectile.setOrientation(orientation);

                    Vec3 center = Vec3.atCenterOf(pos);
                    try {
                        center = rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level, center);
                    } catch (Throwable ignored) {}
                    projectile.setPos(center.x, center.y, center.z);
                    
                    projectile.setDeltaMovement(orientation.scale(1.5));
                    
                    level.addFreshEntity(projectile);
                }
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        
        boolean isShiftDown = false;
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            isShiftDown = com.radiologistics.create.item.ClientHelper.isShiftDown();
        }

        if (isShiftDown) {
            String key = "tooltip.radiologistics.wired_inertia_fuze";
            String translated = net.minecraft.network.chat.Component.translatable(key).getString();
            if (!translated.equals(key)) {
                for (String line : translated.split("\n")) {
                    tooltipComponents.add(com.radiologistics.create.item.ModBlockItem.parseFormatting(line));
                }
            }
        } else {
            tooltipComponents.add(Component.translatable("tooltip.radiologistics.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
