package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.teleportation.ProjectilePortalTraversal;

@Mixin(AbstractArrow.class)
public class MixinAbstractArrow {
    @Unique
    private boolean ip_crossedPortal;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ip_beginProjectileTick(CallbackInfo ci) {
        var arrow = (AbstractArrow) (Object) this;
        ip_crossedPortal = false;
        if (ProjectilePortalTraversal.skipDuplicateTick(arrow)) {
            ci.cancel();
            return;
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;stepMoveAndHit(Lnet/minecraft/world/phys/BlockHitResult;)V"))
    private void ip_stepThroughPortals(AbstractArrow arrow, BlockHitResult hit, Operation<Void> original) {
        ip_crossedPortal |= ProjectilePortalTraversal.moveArrow(arrow, hit,
                segment -> original.call(arrow, segment));
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;setPos(Lnet/minecraft/world/phys/Vec3;)V"), index = 0)
    private Vec3 ip_moveNoPhysicsArrow(Vec3 endpoint) {
        var arrow = (AbstractArrow) (Object) this;
        var trace = ProjectilePortalTraversal.traceUnobstructed(arrow);
        ip_crossedPortal |= trace.crossed();
        return trace.crossed() ? trace.endpoint() : endpoint;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ip_finishProjectileTick(CallbackInfo ci) {
        if (ip_crossedPortal) ProjectilePortalTraversal.finish((AbstractArrow) (Object) this);
    }
}
