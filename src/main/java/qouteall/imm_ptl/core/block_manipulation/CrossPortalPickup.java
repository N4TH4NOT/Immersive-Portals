package qouteall.imm_ptl.core.block_manipulation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Extends vanilla's pickup volume through an accessible portal opening. */
public final class CrossPortalPickup {
    private static final ThreadLocal<PickupContext> ACTIVE_PICKUP = new ThreadLocal<>();

    private CrossPortalPickup() {}

    public record PickupContext(ServerPlayer player, Portal portal, ItemEntity item) {}

    public static @Nullable PickupContext currentPickup() {
        return ACTIVE_PICKUP.get();
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) collectForPlayer(player);
        });
    }

    public static AABB pickupBox(Player player) {
        var vehicle = player.getVehicle();
        return vehicle != null && !vehicle.isRemoved()
                ? player.getBoundingBox().minmax(vehicle.getBoundingBox()).inflate(1, 0, 1)
                : player.getBoundingBox().inflate(1, .5, 1);
    }

    private static List<Portal> nearbyPortals(Player player, AABB pickup) {
        double size = Math.max(pickup.getXsize(),
                Math.max(pickup.getYsize(), pickup.getZsize()));
        return IPMcHelper.getNearbyPortalList(player,
                IPGlobal.maxNormalPortalRadius + size + 2,
                portal -> portal.isInteractableBy(player)
                        && portal.getBoundingBox().intersects(pickup));
    }

    /** Returns the actual opening used by a pickup, also for its client-side animation. */
    public static @Nullable Portal findPortalForPickup(
            Player player, Level itemWorld, AABB itemBox) {
        var pickup = pickupBox(player);
        if (itemWorld == player.level() && pickup.intersects(itemBox)) return null;
        for (Portal portal : nearbyPortals(player, pickup))
            if (portal.getDestDim() == itemWorld.dimension()
                    && reachesItem(player, portal, pickup, itemBox)) return portal;
        return null;
    }

    private static boolean reachesItem(Player player, Portal portal, AABB pickup, AABB itemBox) {
        var point = visiblePickupPoint(portal, itemBox);
        if (point == null) return false;
        AABB unfolded = Helper.transformBox(itemBox, portal::inverseTransformPoint);
        if (!pickup.intersects(unfolded)) return false;
        // An inflated box alone would collect around the aperture's edges. Require the
        // path to cross its real shape, preserving the ordinary range in source space.
        return portal.generalRayTrace(player.getEyePosition(),
                portal.inverseTransformPoint(point), 0) != null;
    }

    private static @Nullable Vec3 visiblePickupPoint(Portal portal, AABB box) {
        Vec3 center = box.getCenter();
        var plane = portal.getInnerClipping();
        if (plane == null) return center;
        Vec3 normal = plane.normal();
        double distance = center.subtract(plane.pos()).dot(normal);
        if (distance > 0) return center;
        Vec3 support = new Vec3(normal.x >= 0 ? box.maxX : box.minX,
                normal.y >= 0 ? box.maxY : box.minY,
                normal.z >= 0 ? box.maxZ : box.minZ);
        double supportDistance = support.subtract(plane.pos()).dot(normal);
        if (supportDistance <= 0) return null;
        // An item can straddle the plane before its center crosses it. Select a
        // point inside its visible portion rather than extending pickup behind it.
        return center.lerp(support,
                (supportDistance * .5 - distance) / (supportDistance - distance));
    }

    /** Applies the real vanilla ownership, delay, inventory and pickup/statistics rules. */
    public static int collectForPlayer(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return 0;
        var pickup = pickupBox(player);
        var attempted = new HashSet<UUID>();
        int collected = 0;
        for (Portal portal : nearbyPortals(player, pickup)) {
            if (!(portal.getDestWorld() instanceof ServerLevel destination)) continue;
            AABB transformed = CollisionHelper.transformBox(portal, pickup);
            for (ItemEntity item : destination.getEntitiesOfClass(ItemEntity.class, transformed,
                    entity -> !entity.isRemoved())) {
                if (item.level() == player.level() && pickup.intersects(item.getBoundingBox()))
                    continue;
                if (!reachesItem(player, portal, pickup, item.getBoundingBox())
                        || !attempted.add(item.getUUID())) continue;
                int previous = item.getItem().getCount();
                PickupContext old = ACTIVE_PICKUP.get();
                ACTIVE_PICKUP.set(new PickupContext(player, portal, item));
                try {
                    item.playerTouch(player);
                } finally {
                    if (old == null) ACTIVE_PICKUP.remove();
                    else ACTIVE_PICKUP.set(old);
                }
                int received = item.isRemoved() ? previous : previous - item.getItem().getCount();
                if (received > 0) {
                    collected += received;
                    // The normal player inventory broadcast ran earlier in this tick.
                    player.containerMenu.broadcastChanges();
                }
            }
        }
        return collected;
    }

    public static Vec3 pickupTarget(EntityTarget target, @Nullable Portal portal) {
        Vec3 position = target.position();
        if (portal == null) return position;
        if (portal.getOriginDim() != portal.getDestDim())
            return target.world() == portal.getOriginDim() ? portal.transformPoint(position) : position;
        return position.distanceToSqr(portal.getOriginPos())
                        < position.distanceToSqr(portal.getDestPos())
                ? portal.transformPoint(position) : position;
    }

    public record EntityTarget(net.minecraft.resources.ResourceKey<Level> world, Vec3 position) {}
}
