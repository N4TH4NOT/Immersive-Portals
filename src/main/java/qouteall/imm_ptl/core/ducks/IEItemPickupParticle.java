package qouteall.imm_ptl.core.ducks;

import qouteall.imm_ptl.core.portal.Portal;
import net.minecraft.world.phys.Vec3;

public interface IEItemPickupParticle {
    void ip_setPickupPortal(Portal portal);
    void ip_setPickupTarget(Vec3 target);
}
