package qouteall.imm_ptl.core.teleportation;

import net.minecraft.world.phys.Vec3;

/** Encodes a folded acoustic distance while keeping its direction at the aperture. */
public final class PortalSoundPath {
    private PortalSoundPath() {}

    public static Vec3 virtualSource(
            Vec3 listener, Vec3 exit, double sourceDistance, Vec3 sourceDirection) {
        if (!Double.isFinite(sourceDistance) || sourceDistance < 0)
            throw new IllegalArgumentException("Invalid source-to-aperture sound distance");
        Vec3 direction = exit.subtract(listener);
        double exitDistance = direction.length();
        if (exitDistance > 1.0E-8) direction = direction.scale(1 / exitDistance);
        else direction = sourceDirection.lengthSqr() > 1.0E-16
                ? sourceDirection.normalize() : Vec3.Z_AXIS;
        return listener.add(direction.scale(sourceDistance + exitDistance));
    }
}
