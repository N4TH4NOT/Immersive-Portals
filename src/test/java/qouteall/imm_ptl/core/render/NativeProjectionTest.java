package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import qouteall.q_misc_util.my_util.Plane;

class NativeProjectionTest {
    @Test
    void pitchedDestinationViewsKeepTheCorrectSideOfTheAperture() {
        for (boolean zeroToOne : new boolean[] {false, true})
            for (double distance : new double[] {.9, .3, .04})
                for (double pitch : new double[] {30, 45, 60, 90}) {
                    var view = new Matrix4f().rotateX((float) Math.toRadians(pitch));
                    var projection = new Matrix4f().perspective(
                            (float) Math.toRadians(70), 1.6f, 1024, .05f, zeroToOne);
                    NativeCamera.clipProjection(projection, view,
                            new Vec3(0, 0, distance),
                            new Plane(Vec3.ZERO, new Vec3(0, 0, -1)), zeroToOne);
                    var transform = projection.mul(view, new Matrix4f());
                    var on = transform.transform(new Vector4f(0, -1.4f, (float) -distance, 1));
                    var before = transform.transform(new Vector4f(
                            0, -1.4f, (float) (.01 - distance), 1));
                    var beyond = transform.transform(new Vector4f(
                            0, -1.4f, (float) (-.01 - distance), 1));
                    assertTrue(on.w > 0 && before.w > 0 && beyond.w > 0);
                    assertEquals(1, on.z / on.w, 2.0E-5);
                    assertTrue(before.z / before.w > 1, "Source-side geometry must be clipped");
                    assertTrue(beyond.z / beyond.w < 1, "Destination-side geometry must remain");
                }
    }

    @Test
    void obliqueClippingSupportsBothNativeDepthRanges() {
        for (boolean zeroToOne : new boolean[] {false, true}) {
            // Minecraft uses reverse Z by swapping the near and far parameters.
            Matrix4f parent =
                    new Matrix4f()
                            .perspective((float) Math.toRadians(70), 1.6f, 1000, .05f, zeroToOne);
            Matrix4f clipped = new Matrix4f(parent);
            NativeCamera.clipProjection(
                    clipped,
                    new Matrix4f(),
                    Vec3.ZERO,
                    new Plane(new Vec3(0, 0, -2), new Vec3(0, 0, -1)),
                    zeroToOne);
            Vector4f onPlane = clipped.transform(new Vector4f(.4f, .3f, -2, 1));
            assertEquals(1, onPlane.z / onPlane.w, 1e-5);
            Vector4f beyond = clipped.transform(new Vector4f(.2f, .1f, -3, 1));
            assertTrue(beyond.z / beyond.w < 1);
            Vector4f before = clipped.transform(new Vector4f(0, 0, -1, 1));
            assertTrue(before.z / before.w > 1);
            // Fused views must reconstruct the same parent depth, despite oblique clipping.
            Vector4f view = new Vector4f(.3f, .2f, -5, 1);
            Vector4f sample = clipped.transform(new Vector4f(view));
            sample.div(sample.w);
            Vector4f recovered = new Matrix4f(clipped).invert().transform(new Vector4f(sample));
            recovered.div(recovered.w);
            Vector4f depth = parent.transform(recovered),
                    expected = parent.transform(new Vector4f(view));
            assertEquals(expected.z / expected.w, depth.z / depth.w, 1e-5);
        }
    }

    @Test
    void clippingDoesNotMutateTheViewMatrix() {
        Matrix4f view = new Matrix4f().rotateY(.4f), copy = new Matrix4f(view);
        NativeCamera.clipProjection(
                new Matrix4f().perspective(1, 1, 1000, .05f, true),
                view,
                new Vec3(1, 2, 3),
                new Plane(new Vec3(0, 0, -3), new Vec3(0, 0, -1)),
                true);
        assertEquals(copy, view);
    }
}
