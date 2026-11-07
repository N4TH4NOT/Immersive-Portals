package qouteall.dimlib.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.dimlib.DimensionImpl;

@Mixin(PrimaryLevelData.class)
public abstract class DimensionGlobalStabilityMixin {
    @Inject(method = "worldGenSettingsLifecycle", at = @At("HEAD"), cancellable = true)
    private void dimlib_globalWarningPolicy(CallbackInfoReturnable<Lifecycle> result) {
        if (DimensionImpl.suppressExperimentalWarning) result.setReturnValue(Lifecycle.stable());
    }
}
