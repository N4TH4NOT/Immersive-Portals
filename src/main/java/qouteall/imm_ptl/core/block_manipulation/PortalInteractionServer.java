package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Revalidates claimed portal paths against loaded server geometry and normal player reach. */
public final class PortalInteractionServer {
    private record ContainerTarget(ResourceKey<Level> world, BlockPos block, UUID[] path, Vec3 point) {}
    private record EntityContainerTarget(Entity entity, UUID[] path, UUID[] projection, Vec3 offset) {}
    private static final Map<Player, ContainerTarget> CONTAINERS = new WeakHashMap<>();
    private static final Map<Player, EntityContainerTarget> ENTITY_CONTAINERS = new WeakHashMap<>();
    private static final ThreadLocal<PortalInteractionTarget.Target> ENTITY_ACTION = new ThreadLocal<>();

    private PortalInteractionServer() {}

    @Nullable
    private static PortalInteractionTarget.Target traceClaim(Player player, UUID[] ids,
            Vec3 targetPoint, double blockReach, double entityReach) {
        return traceClaim(player, ids, targetPoint, blockReach, entityReach, null, new UUID[0]);
    }

    @Nullable
    private static PortalInteractionTarget.Target traceClaim(Player player, UUID[] ids,
            Vec3 targetPoint, double blockReach, double entityReach,
            @Nullable ResourceKey<Level> actualWorld, UUID[] projectionIds) {
        if (targetPoint == null || ids == null || projectionIds == null || projectionIds.length > 1
                || (ids.length == 0 && projectionIds.length == 0)
                || ids.length > PortalInteractionTarget.MAX_PORTALS
                || !Double.isFinite(targetPoint.x) || !Double.isFinite(targetPoint.y)
                || !Double.isFinite(targetPoint.z)
                || !BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker().test(player))
            return null;
        var world = player.level();
        var portals = new ArrayList<Portal>();
        for (UUID id : ids) {
            Portal portal = findPortal(world, id);
            if (portal == null || !portal.isInteractableBy(player)) return null;
            portals.add(portal);
            world = portal.getDestinationWorld();
            if (world == null) return null;
        }
        Vec3 sourcePoint = targetPoint;
        Portal projection = null;
        if (projectionIds.length == 1) {
            var sourceWorld = actualWorld == null ? null : player.level().getServer().getLevel(actualWorld);
            if (sourceWorld == null) return null;
            projection = findPortal(sourceWorld, projectionIds[0]);
            if (projection == null || !projection.isInteractableBy(player)
                    || projection.getDestDim() != world.dimension()) return null;
            sourcePoint = projection.transformPoint(sourcePoint);
        }
        for (int i = portals.size() - 1; i >= 0; i--)
            sourcePoint = portals.get(i).inverseTransformPoint(sourcePoint);
        var trace = PortalInteractionTarget.traceToward(player, sourcePoint, blockReach, entityReach);
        if (trace == null || trace.portals().size() != ids.length) return null;
        for (int i = 0; i < ids.length; i++)
            if (!trace.portals().get(i).getUUID().equals(ids[i])) return null;
        if (projection == null ? trace.projectionPortal() != null
                : trace.projectionPortal() == null
                        || !projection.getUUID().equals(trace.projectionPortal().getUUID())) return null;
        return trace;
    }

    @Nullable
    private static Portal findPortal(Level world, UUID id) {
        if (id == null) return null;
        if (world instanceof ServerLevel serverWorld
                && serverWorld.getEntity(id) instanceof Portal found) return found;
        return GlobalPortalStorage.getGlobalPortals(world).stream()
                .filter(candidate -> candidate.getUUID().equals(id)).findFirst().orElse(null);
    }

    @Nullable
    public static PortalInteractionTarget.Target validateBlock(ServerPlayer player,
            ResourceKey<Level> dimension, BlockPos position, UUID[] path, Vec3 point,
            boolean placing) {
        var target = traceClaim(player, path, point, player.blockInteractionRange() + 1e-4, 0);
        if (target == null || !(target.hit() instanceof BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        if (target.world().dimension() == dimension && hit.getBlockPos().equals(position)) return target;
        if (placing) {
            var placement = BlockManipulationServer.getHitResultForPlacing(target.world(), hit);
            if (placement.getB() == dimension && placement.getA().getBlockPos().equals(position))
                return target;
        }
        return null;
    }

    @Nullable
    public static PortalInteractionTarget.Target validateEntity(ServerPlayer player,
            ResourceKey<Level> dimension, Entity entity, UUID[] path, UUID[] projection,
            Vec3 point, boolean attack) {
        if (entity == null || entity == player || entity.isRemoved() || point == null
                || !entity.getBoundingBox().inflate(.25).contains(point)) return null;
        double range = attack ? player.getAttackRangeWith(player.getMainHandItem())
                .effectiveMaxRange(player) : player.entityInteractionRange();
        var target = traceClaim(player, path, point, range + 1e-4, range + 1e-4, dimension, projection);
        if (target == null || target.world().dimension() != dimension
                || !(target.hit() instanceof EntityHitResult hit) || hit.getEntity() != entity)
            return null;
        if (attack && target.distance() + 1e-4 < player.getAttackRangeWith(player.getMainHandItem())
                .effectiveMinRange(player)) return null;
        return target;
    }

    public static void rememberContainer(Player player, PortalInteractionTarget.Target target) {
        if (target.hit() instanceof BlockHitResult hit)
            CONTAINERS.put(player, new ContainerTarget(target.world().dimension(), hit.getBlockPos(),
                    target.portals().stream().map(Portal::getUUID).toArray(UUID[]::new), hit.getLocation()));
    }

    public static boolean canKeepContainer(Player player, Level world, BlockPos block) {
        var remembered = CONTAINERS.get(player);
        if (remembered == null || remembered.world() != world.dimension()
                || remembered.block().distManhattan(block) > 1) return false;
        var target = traceClaim(player, remembered.path(), remembered.point(),
                player.getContainerInteractionRange() + 3, 0);
        return target != null && target.world() == world
                && target.hit() instanceof BlockHitResult hit
                && hit.getBlockPos().distManhattan(block) <= 1;
    }

    public static void rememberEntityContainer(Player player, PortalInteractionTarget.Target target) {
        if (target.hit() instanceof EntityHitResult hit) {
            Portal projection = target.projectionPortal();
            ENTITY_CONTAINERS.put(player, new EntityContainerTarget(hit.getEntity(),
                    target.portals().stream().map(Portal::getUUID).toArray(UUID[]::new),
                    projection == null ? new UUID[0] : new UUID[] {projection.getUUID()},
                    hit.getLocation().subtract(hit.getEntity().position())));
        }
    }

    public static boolean canKeepEntityContainer(Player player, Entity entity, double maxRange) {
        var remembered = ENTITY_CONTAINERS.get(player);
        if (remembered == null || remembered.entity() != entity || entity.isRemoved()) return false;
        var target = traceClaim(player, remembered.path(), entity.position().add(remembered.offset()),
                maxRange, maxRange, entity.level().dimension(), remembered.projection());
        return target != null && target.hit() instanceof EntityHitResult hit && hit.getEntity() == entity;
    }

    @Nullable
    public static PortalInteractionTarget.Target currentEntityTarget() {
        return ENTITY_ACTION.get();
    }

    public static void withEntityAction(PortalInteractionTarget.Target target, Runnable action) {
        var previous = ENTITY_ACTION.get();
        ENTITY_ACTION.set(target);
        try { action.run(); }
        finally { ENTITY_ACTION.set(previous); }
    }

    @Nullable
    public static ServerLevel entityWorld() {
        var target = ENTITY_ACTION.get();
        return target != null && target.world() instanceof ServerLevel level ? level : null;
    }

    public static boolean isValidatedEntity(Entity entity) {
        var target = ENTITY_ACTION.get();
        return target != null && target.hit() instanceof EntityHitResult hit && hit.getEntity() == entity;
    }

    public static boolean hasValidatedEntity() {
        var target = ENTITY_ACTION.get();
        return target != null && target.hit() instanceof EntityHitResult;
    }

    public static boolean hasValidatedBlock() {
        var target = ENTITY_ACTION.get();
        return target != null && target.hit() instanceof BlockHitResult;
    }

    public static void withAttackRotation(Player player, Runnable action) {
        var target = ENTITY_ACTION.get();
        if (target == null) { action.run(); return; }
        PortalEntityActionContext.withAttackGeometry(player, target, action);
    }
}
