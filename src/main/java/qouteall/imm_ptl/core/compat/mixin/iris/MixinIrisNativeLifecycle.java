package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.Iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.render.NativeWorldRenderer;

@Mixin(Iris.class)
public abstract class MixinIrisNativeLifecycle {
    @Inject(method = "reload", at = @At("HEAD"))
    private static void ip_reload(CallbackInfo ci) {
        NativeWorldRenderer.resetOffscreen();
    }
}
