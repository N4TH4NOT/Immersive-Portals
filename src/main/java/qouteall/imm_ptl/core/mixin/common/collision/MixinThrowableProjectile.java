package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.teleportation.ProjectilePortalTraversal;

import java.util.function.Predicate;

@Mixin(ThrowableProjectile.class)
public class MixinThrowableProjectile {
    @Unique
    private ProjectilePortalTraversal.Trace ip_portalTrace;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ip_beginProjectileTick(CallbackInfo ci) {
        ip_portalTrace = null;
        if (ProjectilePortalTraversal.skipDuplicateTick((ThrowableProjectile) (Object) this)) {
            ci.cancel();
            return;
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult ip_traceThroughPortals(Entity entity, Predicate<Entity> canHit,
            Operation<HitResult> original) {
        ip_portalTrace = ProjectilePortalTraversal.traceThrowable((ThrowableProjectile) entity,
                () -> original.call(entity, canHit));
        return ip_portalTrace.hit();
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;setPos(Lnet/minecraft/world/phys/Vec3;)V"), index = 0)
    private Vec3 ip_keepRemainingEndpoint(Vec3 endpoint) {
        return ip_portalTrace != null && ip_portalTrace.crossed()
                && ip_portalTrace.hit().getType() == HitResult.Type.MISS
                ? ip_portalTrace.endpoint() : endpoint;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;applyEffectsFromBlocks()V"))
    private void ip_effectsInProjectileWorld(ThrowableProjectile projectile, Operation<Void> original) {
        ProjectilePortalTraversal.inEntityWorld(projectile, () -> original.call(projectile));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection ip_hitInProjectileWorld(ThrowableProjectile projectile, HitResult hit,
            Operation<ProjectileDeflection> original) {
        return ProjectilePortalTraversal.inEntityWorld(projectile, () -> original.call(projectile, hit));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ip_finishProjectileTick(CallbackInfo ci) {
        if (ip_portalTrace != null && ip_portalTrace.crossed())
            ProjectilePortalTraversal.finish((ThrowableProjectile) (Object) this);
    }
}
