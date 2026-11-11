package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.Plane;

/** Picks the same clipped source bodies and projected bodies that native rendering displays. */
final class PortalInteractionEntities {
    record Pick(EntityHitResult actualHit, Vec3 visualHit, @Nullable Portal projectionPortal) {}
    private record Segment(Vec3 from, Vec3 to) {}

    private PortalInteractionEntities() {}

    @Nullable
    static Pick pick(Level viewWorld, Player player, Vec3 from, Vec3 direction,
            double reach, double blockDistance) {
        if (reach <= 0) return null;
        Vec3 end = from.add(direction.scale(Math.min(reach, blockDistance)));
        Pick nearest = null;
        double nearestDistance = Math.min(reach, blockDistance);
        for (Entity entity : viewWorld.getEntities(player, new AABB(from, end).inflate(1),
                entity -> eligible(entity, player))) {
            Portal crossing = IPGlobal.correctCrossPortalEntityRendering
                    ? ((IEEntity) entity).ip_getCollidingPortal() : null;
            Plane clip = crossing != null && crossing.isPortalValid()
                    ? crossing.getPortalShape().getOuterClipping(crossing.getThisSideState()) : null;
            Vec3 point = hit(entity, clipSegment(from, end, clip));
            if (point != null && from.distanceTo(point) <= nearestDistance) {
                nearestDistance = from.distanceTo(point);
                nearest = new Pick(new EntityHitResult(entity, point), point, null);
            }
        }
        if (!IPGlobal.correctCrossPortalEntityRendering) return nearest;
        Iterable<? extends Level> worlds;
        if (!viewWorld.isClientSide()) worlds = viewWorld.getServer().getAllLevels();
        else {
            if (!ClientWorldLoader.getIsInitialized()) return nearest;
            worlds = ClientWorldLoader.getClientWorlds();
        }
        for (Level actualWorld : worlds)
            for (Entity entity : ((IEWorld) actualWorld).portal_getEntityLookup().getAll()) {
                if (!eligible(entity, player)) continue;
                Portal portal = ((IEEntity) entity).ip_getCollidingPortal();
                if (portal == null || !portal.isPortalValid()
                        || portal.level() != actualWorld || portal.getDestDim() != viewWorld.dimension()
                        || !portal.isInteractableBy(player)) continue;
                Segment visible = clipSegment(from, end, portal.getInnerClipping());
                if (visible == null) continue;
                // Inverse-transform the ray, rather than testing a transformed AABB: rotated
                // projections have empty corners in that AABB which must remain untargetable.
                Vec3 actualPoint = hit(entity, new Segment(
                        portal.inverseTransformPoint(visible.from()),
                        portal.inverseTransformPoint(visible.to())));
                if (actualPoint == null) continue;
                Vec3 visualPoint = portal.transformPoint(actualPoint);
                double distance = from.distanceTo(visualPoint);
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = new Pick(new EntityHitResult(entity, actualPoint), visualPoint, portal);
                }
            }
        return nearest;
    }

    private static boolean eligible(Entity entity, Player player) {
        return entity != player && !(entity instanceof Portal) && !entity.isRemoved()
                && !entity.isSpectator() && entity.isPickable();
    }

    @Nullable
    private static Vec3 hit(Entity entity, @Nullable Segment segment) {
        if (segment == null) return null;
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        return box.contains(segment.from()) ? segment.from()
                : box.clip(segment.from(), segment.to()).orElse(null);
    }

    @Nullable
    private static Segment clipSegment(Vec3 from, Vec3 to, @Nullable Plane clip) {
        if (clip == null) return new Segment(from, to);
        double start = clip.getDistanceTo(from), end = clip.getDistanceTo(to);
        if (start < 0 && end < 0) return null;
        if (start >= 0 && end >= 0) return new Segment(from, to);
        Vec3 intersection = from.lerp(to, start / (start - end));
        return start < 0 ? new Segment(intersection, to) : new Segment(from, intersection);
    }
}
