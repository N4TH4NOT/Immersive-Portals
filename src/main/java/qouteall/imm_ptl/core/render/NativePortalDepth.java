package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4d;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.joml.Vector4f;

import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;

/** Stable window-depth equations for planar apertures, including triangles crossing the eye. */
final class NativePortalDepth {
    private NativePortalDepth() {}

    static Vector4f equation(Portal portal, Matrix4fc transform, List<Float> vertices,
            int width, int height) {
        if (!portal.getPortalShape().isPlanar() || vertices.size() < 9) return new Vector4f();
        float[] triangle = new float[9];
        for (int i = 0; i < 9; i++) triangle[i] = vertices.get(i);
        return equation(portal, transform, triangle, width, height);
    }

    static Vector4f equation(Portal portal, Matrix4fc transform, float[] vertices,
            int width, int height) {
        if (!portal.getPortalShape().isPlanar() || vertices.length < 9) return new Vector4f();
        return equation(transform, vertices, width, height,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
    }

    static Vector4f equation(Matrix4fc transform, float[] vertices,
            int width, int height, boolean zeroToOne) {
        if (vertices.length < 9 || width <= 0 || height <= 0) return new Vector4f();
        var a = new Vector3d(vertices[0], vertices[1], vertices[2]);
        var b = new Vector3d(vertices[3], vertices[4], vertices[5]).sub(a);
        var c = new Vector3d(vertices[6], vertices[7], vertices[8]).sub(a);
        var normal = b.cross(c);
        var plane = new Vector4d(normal.x, normal.y, normal.z, -normal.dot(a));
        new Matrix4d(transform).invert().transpose().transform(plane);
        if (!Double.isFinite(plane.z) || Math.abs(plane.z) < 1.0E-12)
            return new Vector4f();
        // A clip-space plane gives NDC Z = ax + by + c. Evaluating that affine
        // equation avoids ill-conditioned reciprocal-W interpolation near the eye.
        double x = -plane.x / plane.z;
        double y = -plane.y / plane.z;
        double constant = -plane.w / plane.z;
        double scale = zeroToOne ? 1 : .5;
        var result = new Vector4f((float) (x * 2 * scale / width),
                (float) (y * 2 * scale / height),
                (float) ((constant - x - y) * scale + (zeroToOne ? 0 : .5)), 1);
        return Float.isFinite(result.x) && Float.isFinite(result.y) && Float.isFinite(result.z)
                ? result : new Vector4f();
    }
}
