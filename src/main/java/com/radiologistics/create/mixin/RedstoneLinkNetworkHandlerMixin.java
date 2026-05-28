package com.radiologistics.create.mixin;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.radiologistics.create.block.VirtualLinkable;
import com.radiologistics.create.radio.RadioNetworkManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RedstoneLinkNetworkHandler.class, remap = false)
public class RedstoneLinkNetworkHandlerMixin {

    @Inject(method = "withinRange", at = @At("HEAD"), cancellable = true)
    private static void onWithinRange(IRedstoneLinkable from, IRedstoneLinkable to, CallbackInfoReturnable<Boolean> cir) {
        if (from == null || to == null) return;
        
        // Resolve level for from
        Level fromLevel = getLevelOf(from);
        if (fromLevel != null && RadioNetworkManager.isRedstoneLinkJammedAt(fromLevel, from.getLocation())) {
            cir.setReturnValue(false);
            return;
        }

        // Resolve level for to
        Level toLevel = getLevelOf(to);
        if (toLevel != null && RadioNetworkManager.isRedstoneLinkJammedAt(toLevel, to.getLocation())) {
            cir.setReturnValue(false);
            return;
        }
    }

    private static Level getLevelOf(IRedstoneLinkable linkable) {
        if (linkable instanceof LinkBehaviour lb) {
            return lb.getWorld();
        }
        if (linkable instanceof VirtualLinkable vl) {
            return vl.getLevel();
        }
        return null;
    }
}
