package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

class NativePlayerVisibilityTest {
    @Test
    void aCameraInsideTheRenderedBodyCannotSeeItsOwnHeadInterior() {
        AABB body = new AABB(-.3, 0, -.3, .3, 1.8, .3);
        assertFalse(NativePlayerVisibility.canShowBody(body, new Vec3(0, 1.62, 0), .05));
        assertFalse(NativePlayerVisibility.canShowBody(body, new Vec3(0, 1.62, -.34), .05));
    }

    @Test
    void theSamePlayerRemainsVisibleFromASeparatePortalOrThirdPersonCamera() {
        AABB body = new AABB(-.3, 0, -.3, .3, 1.8, .3);
        assertTrue(NativePlayerVisibility.canShowBody(body, new Vec3(0, 1.62, -4), .05));
        assertTrue(NativePlayerVisibility.canShowBody(body.move(64, 0, 0),
            new Vec3(0, 1.62, 0), .05));
    }

    @Test
    void theNearDistanceAndBodyBoundsScaleTogether() {
        for (double scale : new double[] {.01, .5, 1, 10}) {
            AABB body = new AABB(-.3 * scale, 0, -.3 * scale,
                .3 * scale, 1.8 * scale, .3 * scale);
            assertFalse(NativePlayerVisibility.canShowBody(body,
                new Vec3(0, 1.62 * scale, 0), .05 * scale));
            assertTrue(NativePlayerVisibility.canShowBody(body,
                new Vec3(0, 1.62 * scale, -4 * scale), .05 * scale));
        }
    }
}
