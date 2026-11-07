package qouteall.imm_ptl.core.teleportation;

import com.google.common.collect.MapMaker;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.q_misc_util.my_util.RayTraceResult;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Splits a native projectile tick at portal boundaries without integrating its velocity twice. */
public final class ProjectilePortalTraversal {
    private static final int MAX_CROSSINGS = 8;
    private static final Map<Projectile, Tick> TRANSFERRED = new MapMaker().weakKeys().makeMap();

    private record Tick(long clock, int age, Vec3 previous, Vec3 interpolation,
            float previousYaw, float previousPitch) {}

    private record Crossing(Portal portal, RayTraceResult hit) {}

    public record Trace(HitResult hit, Vec3 endpoint, boolean crossed) {}

    private ProjectilePortalTraversal() {}

    public static boolean handles(Entity entity) {
        return entity instanceof AbstractArrow || entity instanceof ThrowableProjectile
                || entity instanceof AbstractHurtingProjectile;
    }

    /** A transferred object can also be present in a world that has not ticked yet. */
    public static boolean skipDuplicateTick(Projectile projectile) {
        Tick tick = TRANSFERRED.get(projectile);
        if (tick == null || tick.clock() != clock(projectile)) return false;
        projectile.tickCount = tick.age();
        projectile.xo = tick.previous().x;
        projectile.yo = tick.previous().y;
        projectile.zo = tick.previous().z;
        projectile.xOld = tick.interpolation().x;
        projectile.yOld = tick.interpolation().y;
        projectile.zOld = tick.interpolation().z;
        projectile.yRotO = tick.previousYaw();
        projectile.xRotO = tick.previousPitch();
        return true;
    }

    public static boolean moveArrow(AbstractArrow arrow, BlockHitResult firstHit,
            Consumer<BlockHitResult> nativeStep) {
        Vec3 endpoint = arrow.position().add(arrow.getDeltaMovement());
        BlockHitResult blockHit = firstHit;
        boolean crossed = false;
        for (int layer = 0; layer < MAX_CROSSINGS; layer++) {
            Crossing crossing = nearest(arrow, endpoint, blockHit, crossed);
            if (crossing == null) {
                step(arrow, blockHit, nativeStep);
                return crossed;
            }
            Vec3 boundary = crossing.hit().hitPos();
            ((IEEntity) arrow).ip_notifyCollidingWithPortal(crossing.portal());
            // Vanilla still resolves all source-side entity hits, including piercing.
            step(arrow, miss(boundary, boundary.subtract(arrow.position())), nativeStep);
            double precision = Math.ulp(Math.max(boundary.length(), arrow.position().length())) * 16;
            if (!arrow.isAlive() || arrow.position().distanceTo(boundary) > precision) return crossed;
            if (arrow.getDeltaMovement().dot(endpoint.subtract(boundary)) <= 0) return crossed;
            Vec3 nextEndpoint = crossing.portal().transformPoint(endpoint);
            if (!crossBoundary(arrow, crossing.portal(), boundary)) return crossed;
            crossed = true;
            endpoint = nextEndpoint;
            blockHit = arrow.level().clipIncludingBorder(new ClipContext(arrow.position(), endpoint,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
        }
        // Keep native collisions even for a cyclic arrangement that exhausts the path budget.
        step(arrow, blockHit, nativeStep);
        return crossed;
    }

    public static Trace traceThrowable(Projectile projectile, Supplier<HitResult> nativeQuery) {
        Vec3 endpoint = projectile.position().add(projectile.getDeltaMovement());
        HitResult hit = inEntityWorld(projectile, nativeQuery);
        boolean crossed = false;
        for (int layer = 0; layer < MAX_CROSSINGS; layer++) {
            Crossing crossing = nearest(projectile, endpoint, hit, crossed);
            if (crossing == null) return new Trace(hit, endpoint, crossed);
            Vec3 nextEndpoint = crossing.portal().transformPoint(endpoint);
            if (!crossBoundary(projectile, crossing.portal(), crossing.hit().hitPos()))
                return new Trace(hit, endpoint, crossed);
            crossed = true;
            endpoint = nextEndpoint;
            Vec3 velocity = projectile.getDeltaMovement();
            try {
                // ProjectileUtil reads this vector to query the remaining segment. The
                // native tick retains the complete transformed velocity for its effects.
                projectile.setDeltaMovement(endpoint.subtract(projectile.position()));
                hit = inEntityWorld(projectile, nativeQuery);
            } finally {
                projectile.setDeltaMovement(velocity);
            }
        }
        return new Trace(hit, endpoint, crossed);
    }

    public static Trace traceUnobstructed(Projectile projectile) {
        return traceThrowable(projectile, () -> miss(
                projectile.position().add(projectile.getDeltaMovement()), projectile.getDeltaMovement()));
    }

    private static void step(Projectile projectile, BlockHitResult hit, Consumer<BlockHitResult> nativeStep) {
        inEntityWorld(projectile, () -> {
            nativeStep.accept(hit);
            return null;
        });
    }

    public static <T> T inEntityWorld(Projectile projectile, Supplier<T> action) {
        return projectile.level().isClientSide() ? inClientWorld(projectile, action) : action.get();
    }

    @Environment(EnvType.CLIENT)
    private static <T> T inClientWorld(Projectile projectile, Supplier<T> action) {
        if (net.minecraft.client.Minecraft.getInstance().level == projectile.level()) return action.get();
        return ClientWorldLoader.withSwitchedWorld(
                (net.minecraft.client.multiplayer.ClientLevel) projectile.level(), action);
    }

    private static Crossing nearest(Projectile projectile, Vec3 endpoint, HitResult nativeHit,
            boolean skipExitPlane) {
        if (!projectile.isAlive() || projectile.isPassenger() || !projectile.getPassengers().isEmpty())
            return null;
        Vec3 start = projectile.position();
        var crossing = PortalUtils.lenientRayTracePortals(projectile.level(), start, endpoint, true,
                portal -> {
                    if (!portal.isPortalValid() || !portal.canTeleportEntity(projectile)
                            || !projectile.canTeleport(projectile.level(), portal.getDestinationWorld()))
                        return false;
                    // The opposite faces of a wand cluster share this exact exit plane.
                    // Resume the remaining segment without crossing the same surface again.
                    var hit = portal.generalRayTrace(start, endpoint, 0);
                    return hit != null && (!skipExitPlane || hit.t() > 0);
                }, 0);
        if (crossing.isEmpty()) return null;
        var candidate = crossing.get();
        if (nativeHit.getType() != HitResult.Type.MISS) {
            double portalDistance = start.distanceToSqr(candidate.getSecond().hitPos());
            double nativeDistance = start.distanceToSqr(nativeHit.getLocation());
            double uncertainty = Math.ulp(Math.max(portalDistance, nativeDistance)) * 16;
            if (portalDistance > nativeDistance + uncertainty) return null;
        }
        return new Crossing(candidate.getFirst(), candidate.getSecond());
    }

    private static boolean crossBoundary(Projectile projectile, Portal portal, Vec3 boundary) {
        Vec3 lastTick = portal.transformPoint(McHelper.lastTickPosOf(projectile));
        Vec3 target = portal.transformPoint(boundary);
        Vec3 oldPosition = projectile.position();
        if (portal.getTeleportChangesGravity()) {
            var gravity = GravityChangerInterface.invoker.getBaseGravityDirection(projectile);
            GravityChangerInterface.invoker.setBaseGravityDirectionServer(projectile,
                    portal.getTransformedGravityDirection(gravity));
        }
        TeleportationUtil.transformEntityVelocity(portal, projectile,
                TeleportationUtil.PortalPointVelocity.ZERO, oldPosition);
        rotate(projectile, portal);
        if (projectile.level().dimension() != portal.getDestDim()) {
            if (projectile.level().isClientSide()) {
                if (!moveClient(projectile, portal, target)) return false;
            } else {
                // Projectiles have no mob AI world references. Keep the ticking object so
                // the native collision routine can continue its remaining destination ray.
                ServerTeleportationManager.of(projectile.level().getServer()).changeEntityDimension(
                        projectile, portal.getDestDim(), target.add(
                                GravityChangerInterface.invoker.getEyeOffset(projectile)), false);
            }
        }
        McHelper.setPosAndLastTickPos(projectile, target, lastTick);
        McHelper.updateBoundingBox(projectile);
        if (!projectile.level().isClientSide()) {
            portal.onEntityTeleportedOnServer(projectile);
            ScaleUtils.onServerEntityTeleported(projectile, portal);
        }
        PortalCollisionHandler.updateCollidingPortalAfterTeleportation(projectile,
                McHelper.getEyePos(projectile), McHelper.getLastTickEyePos(projectile), 1);
        return projectile.isAlive();
    }

    public static void finish(Projectile projectile) {
        if (projectile.isAlive()) {
            PortalCollisionHandler.updateCollidingPortalAfterTeleportation(projectile,
                    McHelper.getEyePos(projectile), McHelper.getLastTickEyePos(projectile), 1);
            if (!projectile.level().isClientSide())
                ServerTeleportationManager.of(projectile.level().getServer())
                        .finishProjectilePortalTraversal(projectile);
        }
        TRANSFERRED.put(projectile, new Tick(clock(projectile), projectile.tickCount,
                McHelper.lastTickPosOf(projectile),
                new Vec3(projectile.xOld, projectile.yOld, projectile.zOld),
                projectile.yRotO, projectile.xRotO));
    }

    private static long clock(Projectile projectile) {
        return projectile.level().isClientSide() ? clientClock()
                : projectile.level().getServer().getTickCount();
    }

    @Environment(EnvType.CLIENT)
    private static long clientClock() {
        return ClientTeleportationManager.tickTimeForTeleportation;
    }

    @Environment(EnvType.CLIENT)
    private static boolean moveClient(Projectile projectile, Portal portal, Vec3 position) {
        var destination = ClientWorldLoader.getWorld(portal.getDestDim());
        var existing = destination.getEntity(projectile.getId());
        if (existing != null && existing != projectile) {
            if (existing.getUUID().equals(projectile.getUUID())) projectile.discard();
            return false;
        }
        ClientTeleportationManager.moveClientEntityAcrossDimension(projectile, destination, position);
        return true;
    }

    private static void rotate(Projectile projectile, Portal portal) {
        Vec3 previous = portal.transformLocalVecNonScale(direction(projectile.yRotO, projectile.xRotO));
        Vec3 current = portal.transformLocalVecNonScale(direction(projectile.getYRot(), projectile.getXRot()));
        projectile.yRotO = yaw(previous);
        projectile.xRotO = pitch(previous);
        projectile.setYRot(yaw(current));
        projectile.setXRot(pitch(current));
    }

    private static Vec3 direction(float yaw, float pitch) {
        double horizontal = Math.cos(Math.toRadians(pitch));
        return new Vec3(Math.sin(Math.toRadians(yaw)) * horizontal, Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * horizontal);
    }

    private static float yaw(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
    }

    private static float pitch(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
    }

    private static BlockHitResult miss(Vec3 position, Vec3 direction) {
        return BlockHitResult.miss(position,
                Direction.getApproximateNearest(direction.x, direction.y, direction.z),
                BlockPos.containing(position));
    }
}
