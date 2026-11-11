package qouteall.imm_ptl.core.render;

import net.minecraft.world.phys.Vec3;

import org.joml.Vector4f;

import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.Plane;

/** Independent fragment clipping is needed when depth clamping bypasses an oblique near plane. */
public final class NativePortalClipping {
    private NativePortalClipping() {}

    public static Plane activePlane() {
        return IPGlobal.enableClippingMechanism
                        && IPCGlobal.useFrontClipping
                        && PortalRendering.isRendering()
                ? PortalRendering.getActiveClippingPlane()
                : null;
    }

    public static Vector4f equation(Plane plane, Vec3 camera) {
        if (plane == null) return null;
        var n = plane.normal();
        return new Vector4f(
                (float) n.x,
                (float) n.y,
                (float) n.z,
                (float) -n.dot(plane.pos().subtract(camera)));
    }
}
