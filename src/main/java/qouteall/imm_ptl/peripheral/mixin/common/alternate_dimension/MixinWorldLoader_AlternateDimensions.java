package qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension;

import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions;

/** Register native dimensions for dedicated servers, integrated servers and world creation. */
@Mixin(WorldLoader.class)
public abstract class MixinWorldLoader_AlternateDimensions {
    @ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
    private static <D> WorldLoader.WorldDataSupplier<D> ip_addAlternateDimensions(
        WorldLoader.WorldDataSupplier<D> supplier
    ) {
        return AlternateDimensions.withAlternateDimensions(supplier);
    }
}
