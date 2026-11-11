package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(PipelineManager.class)
public interface IrisPipelineManagerAccess {
    @Accessor("pipelineFactory")
    Function<NamespacedId, WorldRenderingPipeline> ip_factory();
}
