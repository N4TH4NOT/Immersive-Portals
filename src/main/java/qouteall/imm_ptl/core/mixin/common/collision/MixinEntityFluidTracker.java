package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.collision.PortalFluidForces;

/** Keeps the native force pipeline intact before mapping its final result across a portal. */
@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$Tracker")
public abstract class MixinEntityFluidTracker {
    @ModifyExpressionValue(method = "applyCurrentTo", at = @At(value = "FIELD", target =
            "Lnet/minecraft/world/entity/EntityFluidInteraction$Tracker;accumulatedCurrent:Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ip_combinedNativeCurrent(Vec3 original) {
        var capture = PortalFluidForces.capture();
        return capture == null || capture.localCells == 0 ? original : capture.combinedCurrent;
    }

    @ModifyExpressionValue(method = "applyCurrentTo", at = @At(value = "FIELD", target =
            "Lnet/minecraft/world/entity/EntityFluidInteraction$Tracker;currentCount:I"))
    private int ip_combinedNativeCount(int original) {
        var capture = PortalFluidForces.capture();
        return capture == null || capture.localCells == 0 ? original : capture.totalCells;
    }

    @WrapOperation(method = "applyCurrentTo", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ip_localVelocity(Entity entity, Operation<Vec3> original) {
        Vec3 velocity = original.call(entity);
        var capture = PortalFluidForces.capture(entity);
        return capture == null ? velocity : capture.velocity(velocity);
    }

    @WrapOperation(method = "applyCurrentTo", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void ip_captureFinalForce(Entity entity, Vec3 force, Operation<Void> original) {
        var capture = PortalFluidForces.capture(entity);
        if (capture == null) original.call(entity, force);
        else capture.accept(force);
    }
}
