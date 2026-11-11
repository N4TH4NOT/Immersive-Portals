package qouteall.imm_ptl.core.compat.mixin.sodium;

import com.llamalad7.mixinextras.sugar.Local;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

import qouteall.imm_ptl.core.compat.sodium_compatibility.PortalViewport;

@Mixin(OcclusionCuller.class)
public abstract class MixinSodiumOcclusionCuller {
    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean ip_caves(boolean original, @Local(argsOnly = true) Viewport viewport) {
        return original && ((PortalViewport) (Object) viewport).ip_allowCaveCulling();
    }
}
