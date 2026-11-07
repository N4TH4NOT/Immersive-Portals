package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.ducks.IEEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A shared, distance-bounded targeting ray for native clients and server validation. */
public final class PortalInteractionTarget {
    public static final int MAX_PORTALS = 5;

    public record Target(Level world, HitResult hit, List<Portal> portals,
            double distance, double scale, Vec3 firstPortalHit, @Nullable Portal projectionPortal) {
        public Target(Level world, HitResult hit, List<Portal> portals,
                double distance, double scale, Vec3 firstPortalHit) {
            this(world, hit, portals, distance, scale, firstPortalHit, null);
        }
    }

    private PortalInteractionTarget() {}

    public static <T> T withRotation(Player player, List<Portal> path, Supplier<T> action) {
        return withRotation(player, path, null, action);
    }

    public static <T> T withRotation(Player player, List<Portal> path,
            @Nullable Portal projection, Supplier<T> action) {
        if (path.isEmpty() && projection == null) return action.get();
        Vec3 look = player.getViewVector(1);
        for (Portal portal : path) look = portal.transformLocalVecNonScale(look);
        if (projection != null) look = projection.inverseTransformLocalVecNonScale(look);
        float yaw = player.getYRot(), pitch = player.getXRot();
        player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())));
        try { return action.get(); }
        finally { player.setYRot(yaw); player.setXRot(pitch); }
    }

    @Nullable
    public static Target trace(Player player, float partial, double blockReach, double entityReach) {
        return trace(player, player.getEyePosition(partial), player.getViewVector(partial),
                blockReach, entityReach);
    }

    @Nullable
    public static Target traceToward(Player player, Vec3 sourceTarget,
            double blockReach, double entityReach) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = sourceTarget.subtract(eye);
        if (direction.lengthSqr() < 1e-20) return null;
        return trace(player, eye, direction, blockReach, entityReach);
    }

    @Nullable
    private static Target trace(Player player, Vec3 start, Vec3 look,
            double blockReach, double entityReach) {
        return trace(player, start, look, blockReach, entityReach, ClipContext.Fluid.NONE, true);
    }

    /** Native fluid items use the same portal geometry and normal block reach as the picker. */
    @Nullable
    public static Target traceBlocks(Player player, ClipContext.Fluid fluid) {
        return trace(player, player.getEyePosition(), player.getViewVector(1),
                player.blockInteractionRange(), 0, fluid, false);
    }

    @Nullable
    private static Target trace(Player player, Vec3 start, Vec3 look,
            double blockReach, double entityReach, ClipContext.Fluid fluid, boolean remoteOnly) {
        Level world = player.level();
        Vec3 from = start;
        Vec3 direction = look.normalize();
        double reach = Math.max(blockReach, entityReach);
        double travelled = 0, scale = 1;
        Vec3 firstPortalHit = null;
        var path = new ArrayList<Portal>();
        var contacts = ((IEEntity) player).ip_getPortalCollisionHandler();
        if (contacts != null) for (Portal crossing : contacts.getCollidingPortals()) {
            if (crossing instanceof Mirror || crossing.level() != world
                    || !crossing.isPortalValid() || !crossing.isInteractableBy(player)
                    || !BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker().test(player))
                continue;
            double side = start.subtract(crossing.getOriginPos()).dot(crossing.getNormal());
            if (side < 0) {
                // A tick may pick again after movement crossed the plane but before the
                // physical world transfer. Keep targeting from the already crossed eye.
                Vec3 front = start.add(crossing.getNormal().scale(-side + .001));
                var contact = crossing.generalRayTrace(front, start, 0);
                if (contact != null && crossing.getDestinationWorld() != null) {
                    firstPortalHit = contact.hitPos();
                    from = crossing.transformPoint(start);
                    direction = crossing.transformLocalVecNonScale(direction).normalize();
                    scale = crossing.getScale();
                    if (!(scale > 0) || !Double.isFinite(scale)) return null;
                    world = crossing.getDestinationWorld();
                    path.add(crossing);
                    break;
                }
            }
        }
        while (travelled < reach) {
            double length = Math.min(4096, (reach - travelled) * scale);
            Vec3 end = from.add(direction.scale(length));
            BlockHitResult block = clipBlocks(world, player, from, end, fluid);
            double blockDistance = block.getType() == HitResult.Type.BLOCK
                    ? from.distanceTo(block.getLocation()) : Double.POSITIVE_INFINITY;
            var entity = PortalInteractionEntities.pick(world, player, from, direction,
                    Math.min(length, Math.max(0, entityReach - travelled) * scale), blockDistance);
            double entityDistance = entity == null
                    ? Double.POSITIVE_INFINITY : from.distanceTo(entity.visualHit());
            var portalHit = PortalUtils.lenientRayTracePortals(world, from, end, true,
                    portal -> !(portal instanceof Mirror) && portal.isInteractableBy(player)
                            && BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker()
                                    .test(player), 0);
            double portalDistance = portalHit.isEmpty() ? Double.POSITIVE_INFINITY
                    : from.distanceTo(portalHit.get().getSecond().hitPos());
            if (portalHit.isPresent()
                    && portalDistance <= Math.min(blockDistance, entityDistance) + 1e-5) {
                if (firstPortalHit == null) firstPortalHit = portalHit.get().getSecond().hitPos();
                if (path.size() == MAX_PORTALS)
                    return new Target(world, BlockHitResult.miss(portalHit.get().getSecond().hitPos(),
                            Direction.getApproximateNearest(direction.x, direction.y, direction.z),
                            BlockPos.containing(portalHit.get().getSecond().hitPos())),
                            List.copyOf(path), reach, scale, firstPortalHit);
                Portal portal = portalHit.get().getFirst();
                travelled += portalDistance / scale;
                scale *= portal.getScale();
                if (!(scale > 0) || !Double.isFinite(scale)) return null;
                direction = portal.transformLocalVecNonScale(direction).normalize();
                // Start inside the visible half-space. Starting exactly on a block face
                // makes DDA visit the destination's hidden backing-wall block first.
                double step = Math.min(1e-5 * scale, Math.max(0, reach - travelled) * scale);
                from = portal.transformPoint(portalHit.get().getSecond().hitPos())
                        .add(direction.scale(step));
                travelled += step / scale;
                world = portal.getDestinationWorld();
                if (world == null) return null;
                path.add(portal);
                continue;
            }
            if (entity != null && entityDistance < blockDistance) {
                if (remoteOnly && path.isEmpty() && entity.projectionPortal() == null) return null;
                return new Target(entity.actualHit().getEntity().level(), entity.actualHit(),
                        List.copyOf(path), travelled + entityDistance / scale, scale,
                        firstPortalHit == null ? entity.visualHit() : firstPortalHit,
                        entity.projectionPortal());
            }
            if (remoteOnly && path.isEmpty()) return null;
            HitResult hit = blockDistance / scale + travelled <= blockReach + 1e-6 ? block
                    : BlockHitResult.miss(end, Direction.getApproximateNearest(
                            direction.x, direction.y, direction.z), BlockPos.containing(end));
            double distance = hit.getType() == HitResult.Type.MISS ? reach
                    : travelled + from.distanceTo(hit.getLocation()) / scale;
            return new Target(world, hit, List.copyOf(path), distance, scale, firstPortalHit);
        }
        return remoteOnly && path.isEmpty() ? null : new Target(world, BlockHitResult.miss(from,
                Direction.getApproximateNearest(direction.x, direction.y, direction.z),
                BlockPos.containing(from)), List.copyOf(path), reach, scale, firstPortalHit);
    }

    static BlockHitResult clipBlocks(Level world, Entity player, Vec3 from, Vec3 to) {
        return clipBlocks(world, player, from, to, ClipContext.Fluid.NONE);
    }

    private static BlockHitResult clipBlocks(Level world, Entity player, Vec3 from, Vec3 to,
            ClipContext.Fluid fluid) {
        Vec3 direction = to.subtract(from);
        var context = new ClipContext(from, to, ClipContext.Block.OUTLINE, fluid, player);
        return BlockGetter.traverseBlocks(from, to, context, (ray, position) -> {
            BlockState state = world.getBlockState(position);
            if (state.getBlock() == PortalPlaceholderBlock.instance) return null;
            var block = world.clipWithInteractionOverride(ray.getFrom(), ray.getTo(), position,
                    ray.getBlockShape(state, world, position), state);
            var liquid = ray.getFluidShape(world.getFluidState(position), world, position)
                    .clip(ray.getFrom(), ray.getTo(), position);
            if (block == null) return liquid;
            return liquid == null || from.distanceToSqr(block.getLocation())
                    <= from.distanceToSqr(liquid.getLocation()) ? block : liquid;
        }, ray -> BlockHitResult.miss(ray.getTo(), Direction.getApproximateNearest(
                direction.x, direction.y, direction.z), BlockPos.containing(ray.getTo())));
    }

}
