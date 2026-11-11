package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep dimension-specific material IDs and vertex formats with their owning renderer. */
@Mixin(WorldRenderingSettings.class)
public interface IrisWorldSettingsAccess {
    @Mutable
    @Accessor("INSTANCE")
    static void ip_set(WorldRenderingSettings settings) {
        throw new AssertionError();
    }
}
