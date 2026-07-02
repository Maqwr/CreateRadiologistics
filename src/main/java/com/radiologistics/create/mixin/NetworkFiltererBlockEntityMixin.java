package com.radiologistics.create.mixin;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkFiltererBlockEntity.class, remap = false)
public class NetworkFiltererBlockEntityMixin {
    @Inject(method = "resolveSelectedTrack", at = @At("HEAD"), cancellable = true)
    private void onResolveSelectedTrack(String id, CallbackInfoReturnable<RadarTrack> cir) {
        if (id != null && id.startsWith("custom_target")) {
            RadarTrack custom = com.radiologistics.create.compat.RadarIntegration.getCustomTarget((NetworkFiltererBlockEntity) (Object) this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }
}
