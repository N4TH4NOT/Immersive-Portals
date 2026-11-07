package qouteall.imm_ptl.core.teleportation;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.function.UnaryOperator;

/** Keeps a crossing's physical anchor independent of later server pose updates. */
public final class PlayerTeleportAnchor {
    private PlayerTeleportAnchor() {}

    public static Vec3 eyeOffset(Player player, Pose pose) {
        var gravity = GravityChangerInterface.invoker.getGravityDirection(player);
        return GravityChangerInterface.invoker.transformPlayerToWorld(gravity,
                new Vec3(0, player.getEyeHeight(pose), 0));
    }

    public static Vec3 destinationEyeOffset(Player player, Pose pose, Portal portal) {
        var gravity = GravityChangerInterface.invoker.getGravityDirection(player);
        if (portal.getTeleportChangesGravity()) {
            var base = GravityChangerInterface.invoker.getBaseGravityDirection(player);
            gravity = GravityChangerInterface.invoker.getGravityDirectionWithBase(player,
                    portal.getTransformedGravityDirection(base));
        }
        float height = pose == Pose.SLEEPING ? player.getEyeHeight(pose)
                : player.getDefaultDimensions(pose)
                        .scale(ScaleUtils.getScaleAfterTeleportation(player, portal)).eyeHeight();
        return GravityChangerInterface.invoker.transformPlayerToWorld(gravity, new Vec3(0, height, 0));
    }

    public static boolean isPlayerPose(Player player, Pose pose) {
        return switch (pose) {
            case STANDING, CROUCHING, SWIMMING, FALL_FLYING, SPIN_ATTACK -> true;
            case SLEEPING -> player.isSleeping();
            case DYING -> player.isDeadOrDying();
            default -> false;
        };
    }

    public static boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    public static Vec3 transformFeet(Vec3 feet, Vec3 sourceEyeOffset,
            Vec3 destinationEyeOffset, UnaryOperator<Vec3> transformPoint) {
        return transformPoint.apply(feet.add(sourceEyeOffset)).subtract(destinationEyeOffset);
    }
}
