package qouteall.imm_ptl.core.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidInteractionAccess;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidTrackerAccess;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Runs the native fluid sampler on the physical body parts on both sides of an opening. */
public final class PortalFluidInteraction {
    public record Frame(Entity entity, Level world, AABB body, @Nullable AABB sample,
            Vec3 feet, Vec3 eye, @Nullable List<AABB> physicalParts) {}

    private record BodyParts(List<AABB> physical, @Nullable AABB projected) {}

    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final Map<Entity, EntityFluidInteraction> REMOTE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private PortalFluidInteraction() {}

    public static @Nullable Frame frame(Entity entity) {
        var frame = FRAME.get();
        return frame != null && frame.entity() == entity ? frame : null;
    }

    public static @Nullable Frame currentFrame() { return FRAME.get(); }

    public static @Nullable PortalFluidForces.Result lastForce(Entity entity, TagKey<Fluid> fluid) {
        return PortalFluidForces.lastForce(entity, fluid);
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

    private static void keep(List<AABB> parts, @Nullable AABB box) {
        if (box != null && box.getXsize() > 1.0E-10 && box.getYsize() > 1.0E-10
                && box.getZsize() > 1.0E-10) parts.add(box);
    }

    private static BodyParts bodyParts(AABB box, Portal portal) {
        List<AABB> physical = new ArrayList<>();
        keep(physical, CollisionHelper.clipBox(box, portal.getOriginPos(), portal.getNormal()));
        var state = portal.getThisSideState();
        AABB result = CollisionHelper.clipBox(box, portal.getOriginPos(), portal.getNormal().scale(-1));
        if (result == null) return new BodyParts(physical, null);
        Vec3 horizontal = state.getAxisW(), vertical = state.getAxisH();
        Vec3 edge = portal.getOriginPos().subtract(horizontal.scale(state.width() / 2));
        keep(physical, CollisionHelper.clipBox(result, edge, horizontal.scale(-1)));
        result = CollisionHelper.clipBox(result, edge, horizontal);
        if (result == null) return new BodyParts(physical, null);
        edge = portal.getOriginPos().add(horizontal.scale(state.width() / 2));
        keep(physical, CollisionHelper.clipBox(result, edge, horizontal));
        result = CollisionHelper.clipBox(result, edge, horizontal.scale(-1));
        if (result == null) return new BodyParts(physical, null);
        edge = portal.getOriginPos().subtract(vertical.scale(state.height() / 2));
        keep(physical, CollisionHelper.clipBox(result, edge, vertical.scale(-1)));
        result = CollisionHelper.clipBox(result, edge, vertical);
        if (result == null) return new BodyParts(physical, null);
        edge = portal.getOriginPos().add(vertical.scale(state.height() / 2));
        keep(physical, CollisionHelper.clipBox(result, edge, vertical));
        result = CollisionHelper.clipBox(result, edge, vertical.scale(-1));
        // Only the finite opening removes a source part. The remaining disjoint
        // width/height residuals keep real source fluids beside the portal frame.
        return new BodyParts(physical, result);
    }

    public static void update(EntityFluidInteraction interaction, Entity entity,
            boolean ignoreCurrent, Runnable nativeUpdate) {
        if (frame(entity) != null) {
            nativeUpdate.run();
            return;
        }
        PortalFluidForces.clear(entity);
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
        BodyParts parts = bodyParts(original, portal);
        List<AABB> physical = parts.physical();
        Frame previous = FRAME.get();
        FRAME.set(new Frame(entity, entity.level(), entity.getBoundingBox(),
                physical.isEmpty() ? null : original,
                entity.position(), entity.getEyePosition(), physical));
        try {
            nativeUpdate.run();
            var trackers = ((EntityFluidInteractionAccess) interaction).ip_trackers();
            updateEye(interaction, entity.level(), physical, entity.getEyePosition());
            AABB projected = parts.projected();
            Level destination = portal.getDestinationWorld();
            if (projected == null || destination == null) return;
            AABB remoteBox = Helper.transformBox(projected, portal::transformPoint);
            var plane = portal.getInnerClipping();
            remoteBox = CollisionHelper.clipBox(remoteBox, plane.pos(), plane.normal());
            if (remoteBox == null || PortalCollisionHandler.isBoxTooBig(remoteBox)
                    || !destination.hasChunkAt(
                    net.minecraft.core.BlockPos.containing(remoteBox.getCenter()))) return;
            EntityFluidInteraction remote = REMOTE.computeIfAbsent(entity,
                    ignored -> new EntityFluidInteraction(new HashSet<>(trackers.keySet())));
            Vec3 eye = portal.transformPoint(entity.getEyePosition());
            FRAME.set(new Frame(entity, destination,
                    Helper.transformBox(entity.getBoundingBox(), portal::transformPoint), remoteBox,
                    portal.transformPoint(entity.position()), eye, null));
            remote.update(entity, ignoreCurrent);
            updateEye(remote, destination, List.of(remoteBox), eye);
            PortalFluidForces.prepare(entity, interaction, remote, portal);
            merge(interaction, remote, portal.getScaling());
        } finally {
            if (previous == null) FRAME.remove();
            else FRAME.set(previous);
        }
    }

    private static void updateEye(EntityFluidInteraction interaction, Level world,
            List<AABB> samples, Vec3 eye) {
        boolean kept = samples.stream().anyMatch(box -> box.contains(eye));
        var position = net.minecraft.core.BlockPos.containing(eye);
        var fluid = world.getFluidState(position);
        boolean immersed = kept && !fluid.isEmpty()
                && eye.y >= position.getY()
                && eye.y <= position.getY() + fluid.getHeight(world, position);
        for (var entry : ((EntityFluidInteractionAccess) interaction).ip_trackers().entrySet()) {
            var tracker = (EntityFluidTrackerAccess) entry.getValue();
            tracker.ip_eyesInside(immersed && tracker.ip_height() > 0 && fluid.is(entry.getKey()));
        }
    }

    private static void merge(EntityFluidInteraction physical, EntityFluidInteraction remote,
            double scale) {
        var source = ((EntityFluidInteractionAccess) physical).ip_trackers();
        for (var entry : ((EntityFluidInteractionAccess) remote).ip_trackers().entrySet()) {
            Object value = source.get(entry.getKey());
            if (value == null) continue;
            var from = (EntityFluidTrackerAccess) entry.getValue();
            var into = (EntityFluidTrackerAccess) value;
            into.ip_height(Math.max(into.ip_height(), from.ip_height() / scale));
            into.ip_eyesInside(into.ip_eyesInside() || from.ip_eyesInside());
        }
    }
}
