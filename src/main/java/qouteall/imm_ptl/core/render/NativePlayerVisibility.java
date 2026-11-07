package qouteall.imm_ptl.core.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Geometric first-person visibility, independent of portal distance or camera detachment flags. */
final class NativePlayerVisibility {
    private NativePlayerVisibility() {}

    static boolean canShowBody(AABB renderedBox, Vec3 camera, double nearDistance) {
        return !renderedBox.inflate(nearDistance).contains(camera);
    }
}
