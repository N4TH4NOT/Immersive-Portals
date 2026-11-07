package qouteall.imm_ptl.core.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;

import java.util.ArrayList;
import java.util.List;

/** Portal-aware versions of the native third-person camera's eight visual collision rays. */
public final class PortalCameraCollision {
    private static final int MAX_PORTALS = 5;

    public record CameraPath(Level world, Vec3 position, List<Portal> portals) {}

    private PortalCameraCollision() {}

    public static BlockHitResult clip(Level world, Vec3 eye, ClipContext context, Entity entity) {
        Vec3 from = context.getFrom(), to = context.getTo();
        // Near a flush aperture, a 0.1-block camera corner can already be across
        // the plane while the eye remains in front. Start that corner in its actual
        // destination instead of testing it against the hidden source backing wall.
        var prefix = portalHit(world, eye, from, entity);
        if (prefix != null) {
            var obstruction = visualClip(world, eye, from, entity);
            if (precedes(prefix.getSecond().hitPos(), obstruction, eye)) {
                Portal portal = prefix.getFirst();
                if (!destinationReady(portal, prefix.getSecond().hitPos()))
                    return block(prefix.getSecond().hitPos());
                var result = trace(portal.getDestinationWorld(), portal.transformPoint(from),
                    portal.transformPoint(to), entity, 1);
                return unfold(portal, result, to);
            }
        }
        return trace(world, from, to, entity, 0);
    }

    private static BlockHitResult trace(Level world, Vec3 from, Vec3 to, Entity entity, int depth) {
        var obstruction = visualClip(world, from, to, entity);
        var crossing = portalHit(world, from, to, entity);
        if (crossing == null || !precedes(crossing.getSecond().hitPos(), obstruction, from))
            return obstruction;
        Portal portal = crossing.getFirst();
        Vec3 hit = crossing.getSecond().hitPos();
        if (depth >= MAX_PORTALS || !destinationReady(portal, hit)) return block(hit);
        Vec3 start = portal.transformPoint(hit).add(portal.transformLocalVecNonScale(
            crossing.getSecond().surfaceNormal()).scale(-1.0E-5 * portal.getScale()));
        var result = trace(portal.getDestinationWorld(), start, portal.transformPoint(to),
            entity, depth + 1);
        return unfold(portal, result, to);
    }

    /** Resolve the actual, already collision-limited eye endpoint without extending it again. */
    static CameraPath cameraPath(Level world, Vec3 from, Vec3 to, Entity entity) {
        List<Portal> portals = new ArrayList<>();
        for (int depth = 0; depth < MAX_PORTALS; depth++) {
            var crossing = portalHit(world, from, to, entity);
            if (crossing == null) break;
            Portal portal = crossing.getFirst();
            portals.add(portal);
            from = portal.transformPoint(crossing.getSecond().hitPos()).add(
                portal.transformLocalVecNonScale(crossing.getSecond().surfaceNormal())
                    .scale(-1.0E-5 * portal.getScale()));
            to = portal.transformPoint(to);
            world = portal.getDestinationWorld();
        }
        return new CameraPath(world, to, List.copyOf(portals));
    }

    private static com.mojang.datafixers.util.Pair<Portal, qouteall.q_misc_util.my_util.RayTraceResult>
            portalHit(Level world, Vec3 from, Vec3 to, Entity entity) {
        return PortalUtils.lenientRayTracePortals(world, from, to, true,
            portal -> portal.isPortalValid() && portal.canTeleportEntity(entity), 0).orElse(null);
    }

    private static BlockHitResult visualClip(Level world, Vec3 from, Vec3 to, Entity entity) {
        return world.clip(new ClipContext(from, to, ClipContext.Block.VISUAL,
            ClipContext.Fluid.NONE, entity));
    }

    private static boolean precedes(Vec3 portalHit, BlockHitResult block, Vec3 from) {
        return block.getType() == HitResult.Type.MISS
            || portalHit.distanceTo(from) <= block.getLocation().distanceTo(from) + 1.0E-7;
    }

    private static boolean destinationReady(Portal portal, Vec3 hit) {
        return portal.getDestinationWorld().hasChunkAt(BlockPos.containing(portal.transformPoint(hit)));
    }

    private static BlockHitResult unfold(Portal portal, BlockHitResult result, Vec3 end) {
        if (result.getType() == HitResult.Type.MISS)
            return BlockHitResult.miss(end, Direction.DOWN, BlockPos.containing(end));
        return block(portal.inverseTransformPoint(result.getLocation()));
    }

    private static BlockHitResult block(Vec3 position) {
        return new BlockHitResult(position, Direction.DOWN, BlockPos.containing(position), false);
    }
}
