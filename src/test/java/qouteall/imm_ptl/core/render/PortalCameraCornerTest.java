package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.RectangularPortalShape;
import qouteall.q_misc_util.my_util.DQuaternion;

class PortalCameraCornerTest {
    @Test
    void aNearEyeCanHaveFourCameraCornersAlreadyBeyondAFlushAperture() {
        var portal = new UnilateralPortalState(
            Level.OVERWORLD, Vec3.ZERO, DQuaternion.identity, 4, 4);
        Vec3 eye = new Vec3(0, 0, .04);
        int prefixed = 0, ahead = 0;
        for (int x : new int[] {-1, 1})
            for (int y : new int[] {-1, 1})
                for (int z : new int[] {-1, 1}) {
                    Vec3 corner = eye.add(x * .1, y * .1, z * .1);
                    Vec3 end = corner.add(0, 0, -4);
                    var prefix = RectangularPortalShape.INSTANCE.raytracePortalShape(
                        portal, eye, corner, 0);
                    var crossing = RectangularPortalShape.INSTANCE.raytracePortalShape(
                        portal, corner, end, 0);
                    if (z < 0) {
                        assertNotNull(prefix);
                        assertEquals(0, prefix.hitPos().z, 1e-12);
                        assertNull(crossing);
                        prefixed++;
                    } else {
                        assertNull(prefix);
                        assertNotNull(crossing);
                        ahead++;
                    }
                }
        assertEquals(4, prefixed);
        assertEquals(4, ahead);
    }

    @Test
    void cameraCornersOutsideTheApertureCannotBypassItsPhysicalWall() {
        var portal = new UnilateralPortalState(
            Level.OVERWORLD, Vec3.ZERO, DQuaternion.identity, 4, 4);
        Vec3 eye = new Vec3(2.2, 0, .04);
        Vec3 corner = eye.add(.1, .1, -.1);
        assertNull(RectangularPortalShape.INSTANCE.raytracePortalShape(portal, eye, corner, 0));
    }
}
