package com.radiologistics.create.mixin;

import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WeaponFiringControl.class, remap = false)
public class WeaponFiringControlMixin {

    @Shadow
    private RadarTrack activetrack;

    @Shadow
    private Vec3 target;

    @Shadow
    private boolean binoMode;

    @Shadow
    private BlockPos binoTargetPos;

    private boolean rl$savedBinoMode;
    private BlockPos rl$savedBinoTargetPos;
    private boolean rl$customActive;

    @Inject(method = "tick", at = @At("HEAD"))
    private void rl$injectCustomBinoHead(CallbackInfo ci) {
        rl$customActive = false;
        if (activetrack != null && activetrack.getId() != null
                && activetrack.getId().startsWith("custom_target")
                && activetrack.position() != null) {
            rl$savedBinoMode = binoMode;
            rl$savedBinoTargetPos = binoTargetPos;
            rl$customActive = true;
            binoMode = true;
            binoTargetPos = BlockPos.containing(activetrack.position());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void rl$injectCustomBinoReturn(CallbackInfo ci) {
        if (rl$customActive) {
            binoMode = rl$savedBinoMode;
            binoTargetPos = rl$savedBinoTargetPos;
            rl$customActive = false;
        }
    }
}
