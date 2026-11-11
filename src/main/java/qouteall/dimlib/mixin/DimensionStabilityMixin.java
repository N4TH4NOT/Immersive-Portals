package qouteall.dimlib.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.dimlib.DimensionImpl;

/** Suppress warnings only for explicitly registered stable namespaces. */
@Mixin(WorldDimensions.class)
public abstract class DimensionStabilityMixin {
    @Inject(method = "isVanillaLike", at = @At("RETURN"), cancellable = true)
    private static void dimlib_stableNamespace(
        ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Boolean> result
    ) {
        if (DimensionImpl.isStableNamespace(key.identifier().getNamespace())) result.setReturnValue(true);
    }
}
