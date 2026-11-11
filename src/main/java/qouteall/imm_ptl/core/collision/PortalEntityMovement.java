package qouteall.imm_ptl.core.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

/** Discovers finite portal contacts before native movement can hit a backing wall. */
public final class PortalEntityMovement {
    private PortalEntityMovement() {}

    public static boolean prepare(Entity entity, Vec3 requestedMove) {
        if (!IPGlobal.crossPortalCollision
                || !entity.level().isClientSide() && !IPGlobal.enableServerCollision
                || entity instanceof Portal || entity.isSpectator()
                || requestedMove.lengthSqr() > 60 * 60)
            return false;
        var access = (IEEntity) entity;
        var existing = access.ip_getPortalCollisionHandler();
        if (existing != null) existing.update(entity);
        var sweptBox = entity.getBoundingBox().expandTowards(requestedMove).inflate(1.0E-5);
        var sourceEye = entity.getEyePosition(1);
        for (var portal : McHelper.findEntitiesByBox(Portal.class, entity.level(), sweptBox,
                IPGlobal.maxNormalPortalRadius, candidate -> candidate.isPortalValid()
                        && CollisionHelper.mayEntityCollideWithPortal(
                                entity, candidate, sourceEye, sweptBox)))
            access.ip_notifyCollidingWithPortal(portal);
        for (var portal : GlobalPortalStorage.getGlobalPortals(entity.level()))
            if (portal.isPortalValid() && sweptBox.intersects(portal.getBoundingBox())
                    && CollisionHelper.mayEntityCollideWithPortal(entity, portal, sourceEye, sweptBox))
                access.ip_notifyCollidingWithPortal(portal);
        var handler = access.ip_getPortalCollisionHandler();
        return handler != null && handler.hasCollisionEntry();
    }
}
