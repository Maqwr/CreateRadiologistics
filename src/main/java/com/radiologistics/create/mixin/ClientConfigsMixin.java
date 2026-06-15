package com.radiologistics.create.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.mehvahdjukaar.vista.configs.ClientConfigs;

@Mixin(value = ClientConfigs.class, remap = false)
public class ClientConfigsMixin {
    static {
        System.out.println("[DEBUG_CLIENT_CONFIGS_MIXIN] ClientConfigsMixin class loaded!");
    }

    @Inject(method = "rendersDebug", at = @At("HEAD"), cancellable = true)

    private static void onRendersDebug(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
