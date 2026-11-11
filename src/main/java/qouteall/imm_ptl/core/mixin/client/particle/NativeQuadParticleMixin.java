package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(QuadParticleGroup.class)
public class NativeQuadParticleMixin {
    @WrapWithCondition(
            method = "extractRenderState",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/particle/SingleQuadParticle;extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"))
    private boolean ip_particle(
            SingleQuadParticle particle,
            QuadParticleRenderState state,
            Camera camera,
            float partial) {
        return RenderStates.shouldRenderParticle(particle);
    }
}
