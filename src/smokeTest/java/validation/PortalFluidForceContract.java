package validation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.collision.PortalFluidForces;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidInteractionAccess;
import qouteall.imm_ptl.core.mixin.common.collision.EntityFluidTrackerAccess;
import qouteall.imm_ptl.core.portal.Portal;

/** Compares portal force composition with an independent, unscoped native tracker. */
final class PortalFluidForceContract {
    private PortalFluidForceContract() {}

    static void verify(ServerPlayer player, Portal portal) {
        require(portal.getScaling() == 1
                        && portal.transformLocalVecNonScale(new Vec3(1, 0, 0))
                                .distanceTo(new Vec3(1, 0, 0)) < 1.0E-8
                        && portal.transformLocalVecNonScale(new Vec3(0, 0, 1))
                                .distanceTo(new Vec3(0, 0, 1)) < 1.0E-8,
                "The force contract requires its identity-rotation scale-one fixture");
        Vec3 playerVelocity = player.getDeltaMovement();
        var item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                new ItemStack(Items.NETHERITE_SCRAP));
        try {
            Vec3 moving = new Vec3(.01, 0, 0);
            Vec3 expected = nativeForce(item, moving, new Vec3(1, 0, 1), 2, .014);
            Vec3 actual = portalForce(item, moving, new Vec3(1, 0, 0), 1,
                    new Vec3(0, 0, 1), 1, .014, portal);
            same(actual, expected, "Non-player currents were normalized separately");

            expected = nativeForce(player, Vec3.ZERO, new Vec3(1.1, 0, 0), 2, .014);
            actual = portalForce(player, Vec3.ZERO, new Vec3(.1, 0, 0), 1,
                    new Vec3(1, 0, 0), 1, .014, portal);
            same(actual, expected, "Player minimum-current rules were applied separately");

            var scaled = new Portal(Portal.ENTITY_TYPE, portal.level());
            scaled.setOriginPos(portal.getOriginPos());
            scaled.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 12, 12);
            scaled.setDestinationDimension(portal.getDestDim());
            scaled.setDestination(portal.getDestPos());
            scaled.setScaling(2);
            expected = nativeForce(item, Vec3.ZERO, new Vec3(1, 0, 0), 1, .001).scale(.5);
            actual = portalForce(item, Vec3.ZERO, Vec3.ZERO, 0,
                    new Vec3(1, 0, 0), 1, .001, scaled);
            same(actual, expected, "The destination minimum force was clamped in source units");
            System.out.println("PORTAL_FLUID_FORCE_CONTRACT_OK cases=3");
        } finally {
            player.setDeltaMovement(playerVelocity);
            PortalFluidForces.clear(player);
            PortalFluidForces.clear(item);
        }
    }

    private static EntityFluidInteraction tracker(Vec3 raw, int count) {
        var sensor = new EntityFluidInteraction(java.util.Set.of(FluidTags.WATER));
        var tracker = (EntityFluidTrackerAccess) ((EntityFluidInteractionAccess) sensor)
                .ip_trackers().get(FluidTags.WATER);
        tracker.ip_current(raw);
        tracker.ip_currentCount(count);
        return sensor;
    }

    private static Vec3 nativeForce(Entity entity, Vec3 velocity, Vec3 raw, int count, double strength) {
        PortalFluidForces.clear(entity);
        entity.setDeltaMovement(velocity);
        tracker(raw, count).applyCurrentTo(FluidTags.WATER, entity, strength);
        Vec3 force = entity.getDeltaMovement().subtract(velocity);
        entity.setDeltaMovement(velocity);
        return force;
    }

    private static Vec3 portalForce(Entity entity, Vec3 velocity,
            Vec3 source, int sourceCount, Vec3 destination, int destinationCount,
            double strength, Portal portal) {
        entity.setDeltaMovement(velocity);
        var physical = tracker(source, sourceCount);
        var remote = tracker(destination, destinationCount);
        PortalFluidForces.prepare(entity, physical, remote, portal);
        physical.applyCurrentTo(FluidTags.WATER, entity, strength);
        Vec3 force = entity.getDeltaMovement().subtract(velocity);
        entity.setDeltaMovement(velocity);
        PortalFluidForces.clear(entity);
        return force;
    }

    private static void same(Vec3 actual, Vec3 expected, String description) {
        require(actual.distanceTo(expected) < 1.0E-10,
                description + ": expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
