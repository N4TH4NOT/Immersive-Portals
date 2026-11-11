package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ParticleEngine.class)
public interface NativeParticleEngineAccess {
    @Accessor("resourceManager")
    ParticleResources ip_resources();
}
