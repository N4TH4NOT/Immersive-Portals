package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import qouteall.q_misc_util.my_util.Plane;

class NativeBodyClippingTest {
    @Test
    void aBodyPlaneCanKeepTheCameraSideWithoutChangingPerspective() {
        Vec3 camera = new Vec3(10, 20, 30);
        var plane = new Plane(new Vec3(10, 20, 28), Vec3.Z_AXIS);
        var equation = NativeBodyCompositor.planeEquation(plane, camera);
        assertTrue(equation.dot(new Vector4f(0, 0, -1, 1)) > 0);
        assertTrue(equation.dot(new Vector4f(0, 0, -3, 1)) < 0);
        assertEquals(0, equation.dot(new Vector4f(0, 0, -2, 1)), 0);
    }

    @Test
    void bodyAndViewPlanesClipIndependentHalfSpacesAfterDepthReconstruction() {
        var body = NativeBodyCompositor.planeEquation(new Plane(Vec3.ZERO, Vec3.X_AXIS), Vec3.ZERO);
        var view = NativeBodyCompositor.planeEquation(
            new Plane(new Vec3(0, 0, -2), new Vec3(0, 0, -1)), Vec3.ZERO);
        for (boolean zeroToOne : new boolean[] {false, true}) {
            var projection = new Matrix4f().perspective(1, 1.6f, 1000, .05f, zeroToOne);
            var inverse = new Matrix4f(projection).invert();
            for (Vector4f point : new Vector4f[] {
                    new Vector4f(.4f, 0, -3, 1), new Vector4f(-.4f, 0, -3, 1),
                    new Vector4f(.4f, 0, -1, 1)}) {
                var clip = projection.transform(new Vector4f(point));
                clip.div(clip.w);
                var reconstructed = inverse.transform(clip);
                reconstructed.div(reconstructed.w);
                assertEquals(body.dot(point) >= 0 && view.dot(point) >= 0,
                    body.dot(reconstructed) >= 0 && view.dot(reconstructed) >= 0);
            }
        }
    }
}
