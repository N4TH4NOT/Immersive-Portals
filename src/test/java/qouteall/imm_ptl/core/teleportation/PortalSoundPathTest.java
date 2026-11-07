package qouteall.imm_ptl.core.teleportation;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PortalSoundPathTest {
    @Test
    void nativeAttenuationReceivesBothLegsWithoutChangingTheApertureDirection() {
        var listener = new Vec3(10, 70, 20);
        var exit = listener.add(3, 0, 4);
        var virtual = PortalSoundPath.virtualSource(listener, exit, 7, Vec3.Y_AXIS);
        assertEquals(12, listener.distanceTo(virtual), 1e-12);
        assertEquals(1, virtual.subtract(listener).normalize()
                .dot(exit.subtract(listener).normalize()), 1e-12);
    }

    @Test
    void aListenerAtTheOpeningRetainsTheMappedSourceDirection() {
        var listener = new Vec3(64, 70, 0);
        var virtual = PortalSoundPath.virtualSource(listener, listener, 2,
                new Vec3(-4, 0, 0));
        assertEquals(new Vec3(62, 70, 0), virtual);
        assertEquals(listener, PortalSoundPath.virtualSource(
                listener, listener, 0, Vec3.ZERO));
    }

    @Test
    void aSourceOnTheOpeningKeepsTheNativePosition() {
        var exit = new Vec3(2, 3, 4);
        assertEquals(0, PortalSoundPath.virtualSource(Vec3.ZERO, exit, 0, Vec3.ZERO)
                .distanceTo(exit), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> PortalSoundPath.virtualSource(
                Vec3.ZERO, exit, -1, Vec3.ZERO));
    }
}
