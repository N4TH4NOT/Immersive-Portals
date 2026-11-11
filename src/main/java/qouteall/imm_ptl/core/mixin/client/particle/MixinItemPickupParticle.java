package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickup;
import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickupClient;
import qouteall.imm_ptl.core.ducks.IEItemPickupParticle;
import qouteall.imm_ptl.core.portal.Portal;

@Mixin(ItemPickupParticle.class)
public abstract class MixinItemPickupParticle implements IEItemPickupParticle {
    @Shadow @Final private Entity target;
    @Shadow @Final protected EntityRenderState itemRenderState;
    @Shadow protected double targetX;
    @Shadow protected double targetY;
    @Shadow protected double targetZ;
    @Shadow protected double targetXOld;
    @Shadow protected double targetYOld;
    @Shadow protected double targetZOld;
    @Unique private Portal ip_pickupPortal;
    @Unique private Vec3 ip_fixedTarget;
    @Unique private Vec3 ip_lastKnownTarget;

    @Override
    public void ip_setPickupPortal(Portal portal) {
        ip_pickupPortal = portal;
        var anchor = CrossPortalPickupClient.currentAnchor();
        if (anchor != null) ip_lastKnownTarget = anchor.position();
        ip_updateTarget();
        targetXOld = targetX;
        targetYOld = targetY;
        targetZOld = targetZ;
        CrossPortalPickupClient.recordAnimation(target.getId(), portal.getDestDim(),
                new Vec3(itemRenderState.x, itemRenderState.y, itemRenderState.z),
                new Vec3(targetX, targetY, targetZ));
    }

    @Override
    public void ip_setPickupTarget(Vec3 position) {
        ip_fixedTarget = position;
        ip_updateTarget();
        targetXOld = targetX;
        targetYOld = targetY;
        targetZOld = targetZ;
        var anchor = CrossPortalPickupClient.currentAnchor();
        if (anchor != null)
            CrossPortalPickupClient.recordAnimation(anchor.collector(), anchor.world(),
                    new Vec3(itemRenderState.x, itemRenderState.y, itemRenderState.z), position);
    }

    @Inject(method = "updatePosition", at = @At("TAIL"))
    private void ip_transformPickupTarget(CallbackInfo ci) {
        if (ip_pickupPortal != null || ip_fixedTarget != null) ip_updateTarget();
    }

    @Unique
    private void ip_updateTarget() {
        var dimension = target.level().dimension();
        if (ip_fixedTarget == null && ip_pickupPortal != null
                && dimension != ip_pickupPortal.getOriginDim()
                && dimension != ip_pickupPortal.getDestDim()) {
            // The collector may enter a third world during the three-tick flight.
            // Its new coordinates no longer describe any point in this particle's world.
            if (ip_lastKnownTarget != null) {
                targetX = ip_lastKnownTarget.x;
                targetY = ip_lastKnownTarget.y;
                targetZ = ip_lastKnownTarget.z;
            }
            return;
        }
        Vec3 position = ip_fixedTarget != null ? ip_fixedTarget
                : CrossPortalPickup.pickupTarget(new CrossPortalPickup.EntityTarget(
                dimension,
                new Vec3(target.getX(), (target.getY() + target.getEyeY()) / 2, target.getZ())),
                ip_pickupPortal);
        ip_lastKnownTarget = position;
        targetX = position.x;
        targetY = position.y;
        targetZ = position.z;
    }
}
