package qouteall.imm_ptl.core.collision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

/** Prepares native collision context from each authoritative player's actual packet movement. */
public final class PortalPlayerMovement {
    private PortalPlayerMovement() {}

    public static boolean prepare(ServerPlayer player, Vec3 requestedMove) {
        if (!IPGlobal.crossPortalCollision || !IPGlobal.enableServerCollision
                || player.isSpectator() || requestedMove.lengthSqr() > 60 * 60)
            return false;
        var access = (IEEntity) player;
        var existing = access.ip_getPortalCollisionHandler();
        if (existing != null) existing.update(player);
        var sweptBox = player.getBoundingBox().expandTowards(requestedMove).inflate(1.0E-5);
        var sourceEye = player.getEyePosition(1);
        for (var portal : McHelper.findEntitiesByBox(Portal.class, player.level(), sweptBox,
                IPGlobal.maxNormalPortalRadius, candidate -> candidate.isPortalValid()
                        && CollisionHelper.mayEntityCollideWithPortal(
                                player, candidate, sourceEye, sweptBox)))
            access.ip_notifyCollidingWithPortal(portal);
        for (var portal : GlobalPortalStorage.getGlobalPortals(player.level()))
            if (portal.isPortalValid() && sweptBox.intersects(portal.getBoundingBox())
                    && CollisionHelper.mayEntityCollideWithPortal(player, portal, sourceEye, sweptBox))
                access.ip_notifyCollidingWithPortal(portal);
        var handler = access.ip_getPortalCollisionHandler();
        return handler != null && handler.hasCollisionEntry();
    }
}
