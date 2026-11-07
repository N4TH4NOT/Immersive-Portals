package qouteall.imm_ptl.core.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Geometric first-person visibility, independent of portal distance or camera detachment flags. */
final class NativePlayerVisibility {
    private NativePlayerVisibility() {}

    static boolean canShowBody(AABB renderedBox, Vec3 camera, double nearDistance) {
        return !renderedBox.inflate(nearDistance).contains(camera);
    }

    static boolean canShowBody(AABB renderedBox, Vec3 camera, double nearDistance, Vec3 attachedEye) {
        // The collision pose changes immediately, while the attached camera's eye
        // height interpolates. Its old eye may temporarily lie above the new box.
        return camera.distanceToSqr(attachedEye) > nearDistance * nearDistance
            && canShowBody(renderedBox, camera, nearDistance);
    }
}
