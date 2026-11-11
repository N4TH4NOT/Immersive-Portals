package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.common.interaction.EntityInteractionMountAccess;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

import java.util.function.Supplier;
import java.util.function.Function;

/** Keeps remote action geometry scoped, while allowing an actual mount to transfer the rider. */
public final class PortalEntityActionContext {
    private static final class UseScope {
        final Player player;
        boolean mounted;

        UseScope(Player player) { this.player = player; }
    }

    private static final ThreadLocal<UseScope> USE_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<AttackScope> ATTACK_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<DamagePosition> DAMAGE_POSITION = new ThreadLocal<>();

    private record DamagePosition(DamageSource source, Vec3 position) {}

    private static final class AttackScope {
        final Player player;
        final PortalInteractionTarget.Target target;
        final Geometry physical;
        boolean handlingPhysicalDamage;

        AttackScope(Player player, PortalInteractionTarget.Target target, Geometry physical) {
            this.player = player;
            this.target = target;
            this.physical = physical;
        }
    }

    private record Geometry(Level world, Vec3 position, Vec3 oldPosition, Vec3 interpolationPosition,
            AABB box, Vec3 velocity, float yaw, float pitch, float oldYaw, float oldPitch,
            float body, float oldBody, float head, float oldHead) {
        static Geometry capture(Player player) {
            return new Geometry(player.level(), player.position(),
                    new Vec3(player.xOld, player.yOld, player.zOld),
                    new Vec3(player.xo, player.yo, player.zo), player.getBoundingBox(),
                    player.getDeltaMovement(), player.getYRot(), player.getXRot(),
                    player.yRotO, player.xRotO, player.yBodyRot, player.yBodyRotO,
                    player.yHeadRot, player.yHeadRotO);
        }

        void apply(Player player, Vec3 newVelocity) {
            ((IEEntity) player).ip_setWorld(world);
            ((IEEntity) player).ip_setPositionWithoutTriggeringCallback(position);
            player.xOld = oldPosition.x; player.yOld = oldPosition.y; player.zOld = oldPosition.z;
            player.xo = interpolationPosition.x; player.yo = interpolationPosition.y;
            player.zo = interpolationPosition.z;
            player.setBoundingBox(box);
            player.setYRot(yaw); player.setXRot(pitch);
            player.yRotO = oldYaw; player.xRotO = oldPitch;
            player.yBodyRot = body; player.yBodyRotO = oldBody;
            player.yHeadRot = head; player.yHeadRotO = oldHead;
            player.setDeltaMovement(newVelocity);
        }

        Geometry transformed(PortalInteractionTarget.Target target) {
            Vec3 look = toTargetDirection(target, Vec3.directionFromRotation(pitch, yaw));
            Vec3 oldLook = toTargetDirection(target, Vec3.directionFromRotation(oldPitch, oldYaw));
            return new Geometry(target.world(), toTargetPoint(target, position),
                    toTargetPoint(target, oldPosition), toTargetPoint(target, interpolationPosition),
                    Helper.transformBox(box, point -> toTargetPoint(target, point)),
                    toTargetVector(target, velocity), vectorYaw(look), vectorPitch(look),
                    vectorYaw(oldLook), vectorPitch(oldLook),
                    vectorYaw(toTargetDirection(target, Vec3.directionFromRotation(0, body))),
                    vectorYaw(toTargetDirection(target, Vec3.directionFromRotation(0, oldBody))),
                    vectorYaw(toTargetDirection(target, Vec3.directionFromRotation(0, head))),
                    vectorYaw(toTargetDirection(target, Vec3.directionFromRotation(0, oldHead))));
        }
    }

    private PortalEntityActionContext() {}

    public static <T> T withUseRotation(Player player, PortalInteractionTarget.Target target,
            Supplier<T> action) {
        UseScope previous = USE_SCOPE.get();
        UseScope scope = new UseScope(player);
        USE_SCOPE.set(scope);
        float[] mountedRotation = new float[2];
        try {
            T result = PortalInteractionTarget.withRotation(player, target.portals(),
                    target.projectionPortal(), () -> {
                        try { return action.get(); }
                        finally {
                            mountedRotation[0] = player.getYRot();
                            mountedRotation[1] = player.getXRot();
                        }
                    });
            // A successful mount is a real movement action. Its new orientation belongs
            // to the destination, unlike ordinary right-click prediction or menu use.
            if (scope.mounted) {
                player.setYRot(mountedRotation[0]);
                player.setXRot(mountedRotation[1]);
            }
            return result;
        } finally { USE_SCOPE.set(previous); }
    }

    public static void withAttackGeometry(Player player, PortalInteractionTarget.Target target,
            Runnable action) {
        Geometry physical = Geometry.capture(player);
        Geometry virtual = physical.transformed(target);
        AttackScope previous = ATTACK_SCOPE.get();
        ATTACK_SCOPE.set(new AttackScope(player, target, physical));
        try {
            virtual.apply(player, virtual.velocity());
            action.run();
        } finally {
            // Preserve legitimate recoil or reflected knockback, expressed back in the
            // physical player's coordinates. Inventory, damage and cooldowns stay vanilla.
            physical.apply(player, toSourceVector(target, player.getDeltaMovement()));
            ATTACK_SCOPE.set(previous);
        }
    }

    public static boolean withPhysicalDamage(ServerPlayer player, ServerLevel world,
            DamageSource source, Function<ServerLevel, Boolean> action) {
        AttackScope scope = ATTACK_SCOPE.get();
        if (scope == null || scope.player != player || scope.handlingPhysicalDamage)
            return action.apply(world);
        Geometry virtual = Geometry.capture(player);
        Vec3 sourcePosition = source.getSourcePosition();
        DamagePosition previousPosition = DAMAGE_POSITION.get();
        if (sourcePosition != null)
            DAMAGE_POSITION.set(new DamagePosition(source, toSourcePoint(scope.target, sourcePosition)));
        scope.handlingPhysicalDamage = true;
        scope.physical.apply(player, toSourceVector(scope.target, virtual.velocity()));
        var previousContext = BlockManipulationServer.REDIRECT_CONTEXT.get();
        BlockManipulationServer.REDIRECT_CONTEXT.set(null);
        try {
            // Death, drops, combat callbacks and player control packets belong to the
            // physical world even when Thorns reflects damage during a remote attack.
            return PacketRedirection.withForceRedirectAndGet((ServerLevel) scope.physical.world(),
                    () -> action.apply((ServerLevel) scope.physical.world()));
        } finally {
            Vec3 resultingVelocity = toTargetVector(scope.target, player.getDeltaMovement());
            virtual.apply(player, resultingVelocity);
            scope.handlingPhysicalDamage = false;
            DAMAGE_POSITION.set(previousPosition);
            BlockManipulationServer.REDIRECT_CONTEXT.set(previousContext);
        }
    }

    public static Vec3 damageSourcePosition(DamageSource source) {
        DamagePosition override = DAMAGE_POSITION.get();
        return override != null && override.source() == source ? override.position() : null;
    }

    public static boolean mount(ServerPlayer player, Entity vehicle, boolean force,
            Supplier<Boolean> original) {
        var target = PortalInteractionServer.currentEntityTarget();
        if (target == null || !PortalInteractionServer.isValidatedEntity(vehicle)) return original.get();
        if (!canStartRiding(player, vehicle, force)) return false;
        var previousContext = BlockManipulationServer.REDIRECT_CONTEXT.get();
        BlockManipulationServer.REDIRECT_CONTEXT.set(null);
        try {
            return PacketRedirection.withoutForceRedirect(
                    () -> mountInPhysicalContext(player, vehicle, target, original));
        } finally { BlockManipulationServer.REDIRECT_CONTEXT.set(previousContext); }
    }

    private static boolean mountInPhysicalContext(ServerPlayer player, Entity vehicle,
            PortalInteractionTarget.Target target, Supplier<Boolean> original) {
        Level sourceWorld = player.level();
        Vec3 sourcePosition = player.position(), sourceVelocity = player.getDeltaMovement();
        float sourceYaw = player.getYRot(), sourcePitch = player.getXRot();
        Entity oldVehicle = player.getVehicle();
        UseScope scope = USE_SCOPE.get();
        boolean rotationScoped = scope != null && scope.player == player;
        if (!rotationScoped) {
            Vec3 direction = toTargetDirection(target, player.getViewVector(1));
            player.setYRot(vectorYaw(direction)); player.setXRot(vectorPitch(direction));
        }
        if (oldVehicle != null) player.stopRiding();
        if (sourceWorld != vehicle.level())
            ServerTeleportationManager.of(player.level().getServer()).forceTeleportPlayer(
                    player, vehicle.level().dimension(), vehicle.position());
        player.setDeltaMovement(toTargetVector(target, sourceVelocity));
        boolean mounted = false;
        try {
            mounted = original.get();
            if (mounted && rotationScoped) scope.mounted = true;
            return mounted;
        } finally {
            if (!mounted) {
                player.setYRot(sourceYaw); player.setXRot(sourcePitch);
                ServerTeleportationManager.of(player.level().getServer()).forceTeleportPlayer(
                        player, sourceWorld.dimension(), sourcePosition);
                player.setDeltaMovement(sourceVelocity);
                if (oldVehicle != null && !oldVehicle.isRemoved()) player.startRiding(oldVehicle);
            }
        }
    }

    private static boolean canStartRiding(Player player, Entity vehicle, boolean force) {
        var rider = (EntityInteractionMountAccess) player;
        var mount = (EntityInteractionMountAccess) vehicle;
        if (vehicle == player.getVehicle() || vehicle.isRemoved()
                || !mount.ip_couldAcceptPassenger() || !vehicle.getType().canSerialize()) return false;
        for (Entity ancestor = vehicle.getVehicle(); ancestor != null; ancestor = ancestor.getVehicle())
            if (ancestor == player) return false;
        return force || (rider.ip_canRide(vehicle) && mount.ip_canAddPassenger(player));
    }

    private static Vec3 toTargetPoint(PortalInteractionTarget.Target target, Vec3 point) {
        for (Portal portal : target.portals()) point = portal.transformPoint(point);
        return target.projectionPortal() == null ? point
                : target.projectionPortal().inverseTransformPoint(point);
    }

    private static Vec3 toTargetDirection(PortalInteractionTarget.Target target, Vec3 direction) {
        for (Portal portal : target.portals()) direction = portal.transformLocalVecNonScale(direction);
        return target.projectionPortal() == null ? direction
                : target.projectionPortal().inverseTransformLocalVecNonScale(direction);
    }

    private static Vec3 toSourcePoint(PortalInteractionTarget.Target target, Vec3 point) {
        if (target.projectionPortal() != null) point = target.projectionPortal().transformPoint(point);
        for (int i = target.portals().size() - 1; i >= 0; i--)
            point = target.portals().get(i).inverseTransformPoint(point);
        return point;
    }

    private static Vec3 toTargetVector(PortalInteractionTarget.Target target, Vec3 vector) {
        for (Portal portal : target.portals()) vector = portal.transformLocalVec(vector);
        return target.projectionPortal() == null ? vector
                : target.projectionPortal().inverseTransformLocalVec(vector);
    }

    private static Vec3 toSourceVector(PortalInteractionTarget.Target target, Vec3 vector) {
        if (target.projectionPortal() != null) vector = target.projectionPortal().transformLocalVec(vector);
        for (int i = target.portals().size() - 1; i >= 0; i--)
            vector = target.portals().get(i).inverseTransformLocalVec(vector);
        return vector;
    }

    private static float vectorYaw(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    private static float vectorPitch(Vec3 direction) {
        return (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
    }
}
