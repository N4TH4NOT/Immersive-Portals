package qouteall.imm_ptl.core.compat.mixin.iris;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris binds programs outside Blaze3D between nested world passes. Rebind at native pass
 * boundaries.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class MixinIrisNativeCommandEncoder {
    @Shadow private GlProgram lastProgram;
    @Shadow private RenderPipeline lastPipeline;

    @Inject(method = "createRenderPass", at = @At("HEAD"))
    private void ip_externalState(CallbackInfoReturnable<?> cir) {
        lastProgram = null;
        lastPipeline = null;
    }
}
