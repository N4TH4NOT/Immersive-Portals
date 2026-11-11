package qouteall.imm_ptl.core.collision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Prepares native collision context from each authoritative player's actual packet movement. */
public final class PortalPlayerMovement {
    private PortalPlayerMovement() {}

    public static boolean prepare(ServerPlayer player, Vec3 requestedMove) {
        return PortalEntityMovement.prepare(player, requestedMove);
    }
}
