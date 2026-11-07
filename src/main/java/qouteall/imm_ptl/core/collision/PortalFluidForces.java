package qouteall.imm_ptl.core.collision;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidInteractionAccess;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidTrackerAccess;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Captures native per-world forces after eligibility, normalization and minimum-current rules. */
public final class PortalFluidForces {
    public record Result(Vec3 physical, Vec3 remote, Vec3 applied, int sourceCells,
            int destinationCells) {}

    public static final class Capture {
        public final Entity entity;
        public final @Nullable Portal portal;
        public final int localCells;
        public final int totalCells;
        public final Vec3 combinedCurrent;
        private Vec3 force = Vec3.ZERO;

        private Capture(Entity entity, @Nullable Portal portal, Vec3 combinedCurrent,
                int localCells, int totalCells) {
            this.entity = entity;
            this.portal = portal;
            this.localCells = localCells;
            this.totalCells = totalCells;
            this.combinedCurrent = portal == null ? combinedCurrent
                    : portal.transformLocalVecNonScale(combinedCurrent);
        }

        public Vec3 velocity(Vec3 physical) {
            return portal == null ? physical : portal.transformLocalVec(physical);
        }

        public void accept(Vec3 value) {
            force = force.add(portal == null ? value : portal.inverseTransformLocalVec(value));
        }
    }

    private record Samples(EntityFluidInteraction physical, EntityFluidInteraction remote,
            Portal portal, Map<TagKey<Fluid>, Result> forces) {}

    private static final Map<Entity, Samples> SAMPLES =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();

    private PortalFluidForces() {}

    public static @Nullable Capture capture(Entity entity) {
        Capture capture = CAPTURE.get();
        return capture != null && capture.entity == entity ? capture : null;
    }

    public static @Nullable Capture capture() { return CAPTURE.get(); }

    public static void clear(Entity entity) { SAMPLES.remove(entity); }

    public static void prepare(Entity entity, EntityFluidInteraction physical,
            EntityFluidInteraction remote, Portal portal) {
        SAMPLES.put(entity, new Samples(physical, remote, portal, new HashMap<>()));
    }

    public static @Nullable Result lastForce(Entity entity, TagKey<Fluid> fluid) {
        Samples sample = SAMPLES.get(entity);
        return sample == null ? null : sample.forces().get(fluid);
    }

    private static @Nullable EntityFluidTrackerAccess tracker(EntityFluidInteraction interaction,
            TagKey<Fluid> fluid) {
        return (EntityFluidTrackerAccess) ((EntityFluidInteractionAccess) interaction)
                .ip_trackers().get(fluid);
    }

    public static void apply(EntityFluidInteraction interaction, TagKey<Fluid> fluid,
            Entity entity, double strength, Runnable nativeApply) {
        Samples sample = SAMPLES.get(entity);
        if (CAPTURE.get() != null || sample == null || sample.physical() != interaction) {
            nativeApply.run();
            return;
        }
        var source = tracker(interaction, fluid);
        var destination = tracker(sample.remote(), fluid);
        if (source == null || destination == null) {
            nativeApply.run();
            return;
        }
        int sourceCells = source.ip_currentCount(), destinationCells = destination.ip_currentCount();
        int cells = sourceCells + destinationCells;
        if (cells == 0) return;
        // Both native pipelines receive the same sum/count in their rotational basis,
        // before cutoff, averaging, normalization and minimum-force rules. Strength
        // and scale never alter raw eligibility or normalize each half separately.
        Vec3 eligible = source.ip_current().add(sample.portal()
                .inverseTransformLocalVecNonScale(destination.ip_current()));
        Capture physical = new Capture(entity, null, eligible, sourceCells, cells);
        Capture remote = new Capture(entity, sample.portal(), eligible, destinationCells, cells);
        try {
            CAPTURE.set(physical);
            nativeApply.run();
            double remoteStrength = strength;
            if (fluid.equals(FluidTags.LAVA)) {
                double sourceStrength = sample.portal().level().environmentAttributes()
                        .getDimensionValue(EnvironmentAttributes.FAST_LAVA) ? .007 : .007 / 3;
                double destinationStrength = sample.portal().getDestinationWorld().environmentAttributes()
                        .getDimensionValue(EnvironmentAttributes.FAST_LAVA) ? .007 : .007 / 3;
                remoteStrength *= destinationStrength / sourceStrength;
            }
            CAPTURE.set(remote);
            sample.remote().applyCurrentTo(fluid, entity, remoteStrength);
        } finally {
            CAPTURE.remove();
        }
        // Apply once. Identical fluid on both halves retains one native force, while
        // destination normalization and minimum-force tests occur in destination units.
        Vec3 force = physical.force.scale((double) sourceCells / cells)
                .add(remote.force.scale((double) destinationCells / cells));
        sample.forces().put(fluid, new Result(physical.force, remote.force,
                force, sourceCells, destinationCells));
        if (force.lengthSqr() > 0) entity.addDeltaMovement(force);
    }
}
