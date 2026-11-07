package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.junit.jupiter.api.Test;

class NativePortalDepthTest {
    private static final int WIDTH = 854, HEIGHT = 480;

    private static Matrix4f projection(boolean zeroToOne) {
        return new Matrix4f().perspective(
                (float) Math.toRadians(70), (float) WIDTH / HEIGHT, 1024, .05f, zeroToOne);
    }

    private static float[] triangle(Matrix4f model) {
        float[] result = new float[9];
        float[] base = {2, 2, 0, -2, 2, 0, 2, -2, 0};
        for (int i = 0; i < 9; i += 3) {
            var point = model.transformPosition(new Vector3f(base[i], base[i + 1], base[i + 2]));
            result[i] = point.x;
            result[i + 1] = point.y;
            result[i + 2] = point.z;
        }
        return result;
    }

    private static void matchesProjectedPoint(Matrix4f transform, float[] vertices,
            Vector3d point, boolean zeroToOne) {
        var equation = NativePortalDepth.equation(
                transform, vertices, WIDTH, HEIGHT, zeroToOne);
        assertEquals(1, equation.w, "A valid planar aperture must enable analytic depth");
        // The oracle projects a known point on the aperture directly, independently
        // of the plane inversion and affine-coefficient construction under test.
        var clip = new Matrix4d(transform).transform(new Vector4d(point, 1));
        assertTrue(clip.w > 0, "The oracle must lie in front of the eye");
        double pixelX = (clip.x / clip.w + 1) * WIDTH / 2;
        double pixelY = (clip.y / clip.w + 1) * HEIGHT / 2;
        double expected = clip.z / clip.w;
        if (!zeroToOne) expected = expected * .5 + .5;
        double actual = equation.x * pixelX + equation.y * pixelY + equation.z;
        assertEquals(Math.clamp(expected, 0, 1), Math.clamp(actual, 0, 1), 2.0E-6);
    }

    @Test
    void closeObliqueTrianglesMatchProjectedDepthAcrossTheEyePlane() {
        for (boolean zeroToOne : new boolean[] {false, true})
            for (float distance : new float[] {.04f, .01f, .001f}) {
                var vertices = triangle(new Matrix4f().translation(0, 0, -distance));
                var transform = projection(zeroToOne).rotateY((float) Math.toRadians(45));
                var matrix = new Matrix4d(transform);
                double firstW = matrix.transform(new Vector4d(
                        vertices[0], vertices[1], vertices[2], 1)).w;
                double secondW = matrix.transform(new Vector4d(
                        vertices[3], vertices[4], vertices[5], 1)).w;
                assertTrue(firstW * secondW < 0, "The aperture must cross behind the eye");
                for (double factor : new double[] {.5, 1, 2})
                    matchesProjectedPoint(transform, vertices,
                            new Vector3d(distance * factor, .02, vertices[2]), zeroToOne);
            }
    }

    @Test
    void translatedScaledAndMirroredPlanesMatchIndependentProjection() {
        for (boolean zeroToOne : new boolean[] {false, true})
            for (float scale : new float[] {2, -2}) {
                var vertices = triangle(new Matrix4f().translation(4, 3, -64)
                        .rotateX(.37f).rotateY(-.23f).scale(scale, 1.5f, .5f));
                // Barycentric interior points stay on the actual uploaded float triangle.
                for (double weight : new double[] {.2, .4, .7}) {
                    var point = new Vector3d();
                    for (int axis = 0; axis < 3; axis++)
                        point.setComponent(axis, vertices[axis] * weight
                                + vertices[3 + axis] * (1 - weight) * .6
                                + vertices[6 + axis] * (1 - weight) * .4);
                    matchesProjectedPoint(projection(zeroToOne).rotateY(.16f),
                            vertices, point, zeroToOne);
                }
            }
    }

    @Test
    void invalidGeometryAndViewportKeepTheGeneralDepthFallback() {
        assertEquals(0, NativePortalDepth.equation(new Matrix4f(), new float[9],
                WIDTH, HEIGHT, true).w);
        assertEquals(0, NativePortalDepth.equation(new Matrix4f(), new float[0],
                WIDTH, HEIGHT, true).w);
        assertEquals(0, NativePortalDepth.equation(new Matrix4f(), new float[9],
                0, HEIGHT, true).w);
        assertEquals(0, NativePortalDepth.equation(new Matrix4f().zero(),
                triangle(new Matrix4f()), WIDTH, HEIGHT, false).w);
    }
}
