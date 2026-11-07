package qouteall.imm_ptl.core.compat.sodium_compatibility;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

class SodiumCameraPositionTest {
    @Test
    void splitPositionRetainsFractionalPrecisionAtBothWorldBorders() {
        var camera = SodiumInterface.fromSplitCamera(
                30_000_000, -30_000_000, 0, .25f, -.25f, .0399932861328125f);
        assertEquals(30_000_000.25, camera.x);
        assertEquals(-30_000_000.25, camera.y);
        assertEquals(.0399932861328125, camera.z);
    }

    @Test
    void vanillaKeepsTheActualCameraOrigin() {
        var camera = new Vec3(1.23, 72.34, .04);
        assertSame(camera, new SodiumInterface.Invoker().getTerrainRenderCamera(camera));
    }
}
