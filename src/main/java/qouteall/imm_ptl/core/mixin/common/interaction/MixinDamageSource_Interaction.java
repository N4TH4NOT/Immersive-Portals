package qouteall.imm_ptl.core.mixin.common.interaction;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.PortalEntityActionContext;

@Mixin(DamageSource.class)
public abstract class MixinDamageSource_Interaction {
    @Inject(method = "getSourcePosition", at = @At("HEAD"), cancellable = true)
    private void ip_reflectedSourcePosition(CallbackInfoReturnable<Vec3> ci) {
        Vec3 position = PortalEntityActionContext.damageSourcePosition((DamageSource) (Object) this);
        if (position != null) ci.setReturnValue(position);
    }
}
