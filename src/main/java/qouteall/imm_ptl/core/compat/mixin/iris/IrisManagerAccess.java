package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.PipelineManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Iris.class)
public interface IrisManagerAccess {
    @Accessor("pipelineManager")
    static void ip_set(PipelineManager manager) {
        throw new AssertionError();
    }
}
