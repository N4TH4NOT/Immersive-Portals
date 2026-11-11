package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/** Adapts existing portal geometry emitters to Minecraft's native gizmo extraction. */
public final class NativeLineBuffer implements AutoCloseable {
    public enum Mode {
        LINES,
        LINE_STRIP;

        public static Mode lines() {
            return LINES;
        }

        public static Mode debugLineStrip(double width) {
            return LINE_STRIP;
        }
    }

    private final Vec3 camera;
    private final ArrayList<Lines> buffers = new ArrayList<>();

    public NativeLineBuffer(Vec3 camera) {
        this.camera = camera;
    }

    public VertexConsumer getBuffer(Mode mode) {
        Lines buffer = new Lines(mode);
        buffers.add(buffer);
        return buffer;
    }

    @Override
    public void close() {
        buffers.forEach(Lines::emit);
        buffers.clear();
    }

    private static final class Point {
        Vec3 position;
        int color = -1;
        float width = 1;

        Point(float x, float y, float z) {
            position = new Vec3(x, y, z);
        }
    }

    private final class Lines implements VertexConsumer {
        final Mode mode;
        final ArrayList<Point> points = new ArrayList<>();
        Point last;

        Lines(Mode mode) {
            this.mode = mode;
        }

        void emit() {
            int stride = mode == Mode.LINES ? 2 : 1;
            for (int i = 0; i + 1 < points.size(); i += stride) {
                Point a = points.get(i), b = points.get(i + 1);
                Gizmos.line(a.position.add(camera), b.position.add(camera), a.color, a.width);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            last = new Point(x, y, z);
            points.add(last);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a << 24) | (r << 16) | (g << 8) | b);
        }

        @Override
        public VertexConsumer setColor(int color) {
            last.color = color;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            last.width = width;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
