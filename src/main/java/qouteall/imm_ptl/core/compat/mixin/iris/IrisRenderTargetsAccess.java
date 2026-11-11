package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IrisRenderingPipeline.class)
public interface IrisRenderTargetsAccess {
    @Accessor("renderTargets")
    RenderTargets ip_targets();
}
