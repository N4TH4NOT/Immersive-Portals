package qouteall.imm_ptl.core.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidInteractionAccess;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidTrackerAccess;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;

/** Runs the native fluid sampler on the physical body parts on both sides of an opening. */
public final class PortalFluidInteraction {
    public record Frame(Entity entity, Level world, AABB body, @Nullable AABB sample,
            Vec3 feet, Vec3 eye) {}

    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final Map<Entity, EntityFluidInteraction> REMOTE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private PortalFluidInteraction() {}

    public static @Nullable Frame frame(Entity entity) {
        var frame = FRAME.get();
        return frame != null && frame.entity() == entity ? frame : null;
    }

    private static @Nullable Portal contact(Entity entity) {
        var handler = ((IEEntity) entity).ip_getPortalCollisionHandler();
        if (handler == null) return null;
        Portal best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Vec3 previous = entity.getEyePosition(0), current = entity.getEyePosition(1);
        for (Portal portal : handler.getCollidingPortals()) {
            if (portal.isRemoved() || portal.level() != entity.level()
                    || !portal.getHasCrossPortalCollision() || portal.getInnerClipping() == null)
                continue;
            double before = portal.getDistanceToPlane(previous);
            double now = portal.getDistanceToPlane(current);
            // Input and native fluid updates can precede the rendered crossing. Keep
            // that crossing's physical source half until the entity really transfers.
            if (before >= 0 && now < 0) return portal;
            if (now >= 0 && now < bestDistance) {
                best = portal;
                bestDistance = now;
            }
        }
        return best;
    }

    private static @Nullable AABB aperturePart(AABB box, Portal portal) {
        var state = portal.getThisSideState();
        AABB result = CollisionHelper.clipBox(box, portal.getOriginPos(), portal.getNormal().scale(-1));
        if (result == null) return null;
        Vec3 horizontal = state.getAxisW(), vertical = state.getAxisH();
        result = CollisionHelper.clipBox(result,
                portal.getOriginPos().subtract(horizontal.scale(state.width() / 2)), horizontal);
        if (result == null) return null;
        result = CollisionHelper.clipBox(result,
                portal.getOriginPos().add(horizontal.scale(state.width() / 2)), horizontal.scale(-1));
        if (result == null) return null;
        result = CollisionHelper.clipBox(result,
                portal.getOriginPos().subtract(vertical.scale(state.height() / 2)), vertical);
        if (result == null) return null;
        return CollisionHelper.clipBox(result,
                portal.getOriginPos().add(vertical.scale(state.height() / 2)), vertical.scale(-1));
    }

    public static void update(EntityFluidInteraction interaction, Entity entity,
            boolean ignoreCurrent, Runnable nativeUpdate) {
        if (frame(entity) != null) {
            nativeUpdate.run();
            return;
        }
        Portal portal = contact(entity);
        if (portal == null) {
            nativeUpdate.run();
            return;
        }
        AABB original = entity.getFluidInteractionBox();
        if (original == null) {
            nativeUpdate.run();
            return;
        }
        AABB physical = portal.getPortalShape().transformEntityActiveCollisionBox(portal, original, entity);
        Frame previous = FRAME.get();
        FRAME.set(new Frame(entity, entity.level(), entity.getBoundingBox(), physical,
                entity.position(), entity.getEyePosition()));
        try {
            nativeUpdate.run();
            restrictEye(interaction, physical, entity.getEyePosition());
            AABB projected = aperturePart(original, portal);
            Level destination = portal.getDestinationWorld();
            if (projected == null || destination == null) return;
            AABB remoteBox = Helper.transformBox(projected, portal::transformPoint);
            var plane = portal.getInnerClipping();
            remoteBox = CollisionHelper.clipBox(remoteBox, plane.pos(), plane.normal());
            if (remoteBox == null || PortalCollisionHandler.isBoxTooBig(remoteBox)
                    || !destination.hasChunkAt(
                    net.minecraft.core.BlockPos.containing(remoteBox.getCenter()))) return;
            var trackers = ((EntityFluidInteractionAccess) interaction).ip_trackers();
            EntityFluidInteraction remote = REMOTE.computeIfAbsent(entity,
                    ignored -> new EntityFluidInteraction(new HashSet<>(trackers.keySet())));
            Vec3 eye = portal.transformPoint(entity.getEyePosition());
            FRAME.set(new Frame(entity, destination,
                    Helper.transformBox(entity.getBoundingBox(), portal::transformPoint), remoteBox,
                    portal.transformPoint(entity.position()), eye));
            remote.update(entity, ignoreCurrent);
            restrictEye(remote, remoteBox, eye);
            merge(interaction, remote, portal);
        } finally {
            if (previous == null) FRAME.remove();
            else FRAME.set(previous);
        }
    }

    private static void restrictEye(EntityFluidInteraction interaction,
            @Nullable AABB sample, Vec3 eye) {
        if (sample != null && sample.contains(eye)) return;
        for (Object value : ((EntityFluidInteractionAccess) interaction).ip_trackers().values())
            ((EntityFluidTrackerAccess) value).ip_eyesInside(false);
    }

    private static void merge(EntityFluidInteraction physical, EntityFluidInteraction remote,
            Portal portal) {
        var source = ((EntityFluidInteractionAccess) physical).ip_trackers();
        for (var entry : ((EntityFluidInteractionAccess) remote).ip_trackers().entrySet()) {
            Object value = source.get(entry.getKey());
            if (value == null) continue;
            var from = (EntityFluidTrackerAccess) entry.getValue();
            var into = (EntityFluidTrackerAccess) value;
            into.ip_height(Math.max(into.ip_height(), from.ip_height() / portal.getScaling()));
            into.ip_eyesInside(into.ip_eyesInside() || from.ip_eyesInside());
            into.ip_current(into.ip_current().add(portal.inverseTransformLocalVec(from.ip_current())));
            into.ip_currentCount(into.ip_currentCount() + from.ip_currentCount());
        }
    }
}
