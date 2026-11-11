package validation.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import validation.PortalItemUseTrace;

/** Observes native cancellation without changing item-use behavior. */
@Mixin(LivingEntity.class)
public abstract class PortalItemUseTraceMixin {
    @Inject(method = {"stopUsingItem", "releaseUsingItem"}, at = @At("HEAD"))
    private void portalSmoke_traceItemUse(CallbackInfo callback) {
        PortalItemUseTrace.capture((LivingEntity) (Object) this, callback.getId());
    }
}
