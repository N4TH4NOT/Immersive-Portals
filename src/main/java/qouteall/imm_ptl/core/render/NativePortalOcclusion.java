package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.joml.*;
import org.lwjgl.system.MemoryUtil;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.optimization.PortalVisibilityHistory;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Backend-independent occlusion queries: depth-tested coverage, conservative GPU max reduction,
 * then a four-byte readback. The floating mask also implements the normal renderer's stencil.
 */
public final class NativePortalOcclusion {
    private static final List<Mask> MASKS = new ArrayList<>();
    private static final Set<Readback> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int MAX_READBACKS = 128;
    private static long probes, skipped, completed, epoch, synchronousOccluded;
    private static final BindGroupLayout TRANSFORM =
            BindGroupLayout.builder()
                    .withUniform("MaskTransform", UniformType.UNIFORM_BUFFER)
                    .build();
    private static final RenderPipeline MASK_PIPELINE =
            RenderPipeline.builder()
                    .withLocation(id("pipeline/portal_coverage"))
                    .withVertexShader(id("core/portal_coverage"))
                    .withFragmentShader(id("core/portal_coverage"))
                    .withBindGroupLayout(TRANSFORM)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withColorTargetState(
                            new ColorTargetState(
                                    Optional.of(
                                            new BlendFunction(
                                                    BlendFactor.ONE, BlendFactor.ONE, BlendOp.MAX)),
                                    GpuFormat.RG32_FLOAT,
                                    ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(
                            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .build();
    private static final RenderPipeline REDUCE_PIPELINE =
            RenderPipeline.builder()
                    .withLocation(id("pipeline/portal_coverage_reduce"))
                    .withVertexShader(id("core/portal_reduce"))
                    .withFragmentShader(id("core/portal_reduce"))
                    .withBindGroupLayout(BindGroupLayout.builder().withSampler("Coverage").build())
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withColorTargetState(
                            new ColorTargetState(
                                    Optional.empty(),
                                    GpuFormat.RGBA8_UNORM,
                                    ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(Optional.empty())
                    .build();

    private NativePortalOcclusion() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("imm_ptl", path);
    }

    public record Statistics(
            long probes,
            long skipped,
            long completed,
            int pending,
            int masks,
            long synchronousOccluded) {}

    public static Statistics statistics() {
        return new Statistics(
                probes, skipped, completed, PENDING.size(), MASKS.size(), synchronousOccluded);
    }

    public static final class Mask implements AutoCloseable {
        private final GpuTexture texture;
        private final GpuTextureView view;
        private final List<GpuTexture> reduced = new ArrayList<>();
        private final List<GpuTextureView> reducedViews = new ArrayList<>();
        private final int width, height;
        private boolean inUse;
        private boolean visible;
        private long lastUsed;

        private Mask(int width, int height) {
            this.width = width;
            this.height = height;
            var device = RenderSystem.getDevice();
            texture =
                    device.createTexture(
                            "Portal coverage/depth",
                            GpuTexture.USAGE_RENDER_ATTACHMENT
                                    | GpuTexture.USAGE_TEXTURE_BINDING
                                    | GpuTexture.USAGE_COPY_DST,
                            GpuFormat.RG32_FLOAT,
                            width,
                            height,
                            1,
                            1);
            view = device.createTextureView(texture);
            int w = width, h = height;
            do {
                w = (w + 7) / 8;
                h = (h + 7) / 8;
                var target =
                        device.createTexture(
                                "Portal visibility reduction",
                                GpuTexture.USAGE_RENDER_ATTACHMENT
                                        | GpuTexture.USAGE_TEXTURE_BINDING
                                        | GpuTexture.USAGE_COPY_SRC,
                                GpuFormat.RGBA8_UNORM,
                                w,
                                h,
                                1,
                                1);
                reduced.add(target);
                reducedViews.add(device.createTextureView(target));
            } while (w > 1 || h > 1);
        }

        public GpuTextureView textureView() {
            return view;
        }

        public boolean isVisible() {
            return visible;
        }

        @Override
        public void close() {
            inUse = false;
        }

        private void destroy() {
            view.close();
            texture.close();
            reducedViews.forEach(GpuTextureView::close);
            reduced.forEach(GpuTexture::close);
        }
    }

    private static final class Readback {
        final GpuBuffer buffer;
        final PortalVisibilityHistory history;
        final long frame, signature;
        GpuFence fence;
        boolean done;

        Readback(PortalVisibilityHistory history, long signature) {
            this.history = history;
            this.signature = signature;
            frame = RenderStates.frameIndex;
            buffer =
                    RenderSystem.getDevice()
                            .createBuffer(
                                    () -> "Portal visibility result",
                                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                                    4);
        }

        void finish() {
            if (done) return;
            done = true;
            try (var mapped = buffer.map(true, false)) {
                history.complete(frame, signature, mapped.data().get(0) != 0);
                completed++;
            } finally {
                if (fence != null) fence.close();
                buffer.close();
                PENDING.remove(this);
            }
        }

        void cancel() {
            if (!done) {
                done = true;
                if (fence != null) fence.close();
                buffer.close();
            }
        }
    }

    public static Mask query(
            Portal portal, RenderTarget parent, Vec3 camera, Matrix4fc view, Matrix4fc projection) {
        // Poll our own completion points before deciding. The engine's generic readback
        // callbacks may be deferred until a later frame even after the GPU copy has finished.
        for (Readback result : List.copyOf(PENDING))
            if (result.frame < RenderStates.frameIndex
                    && result.fence != null
                    && result.fence.awaitCompletion(0)) result.finish();
        Mask mask =
                MASKS.stream()
                        .filter(
                                m ->
                                        !m.inUse
                                                && m.width == parent.width
                                                && m.height == parent.height)
                        .findFirst()
                        .orElseGet(
                                () -> {
                                    Mask created = new Mask(parent.width, parent.height);
                                    MASKS.add(created);
                                    return created;
                                });
        mask.inUse = true;
        mask.lastUsed = RenderStates.frameIndex;
        try {
            Vec3 terrainCamera = NativePortalDepth.renderCamera(portal, camera);
            float[] vertices = mesh(portal, terrainCamera);
            Matrix4f transform = new Matrix4f(projection).mul(view);
            var history = PortalRenderInfo.get(portal).visibility();
            Vector4f clip =
                    NativePortalClipping.equation(NativePortalClipping.activePlane(), terrainCamera);
            long signature =
                    signature(transform, vertices, clip) * 31
                            + parent.width * 31L
                            + parent.height
                            + epoch;
            IrisInterface.invoker.withCompositeTarget(
                    parent,
                    target -> {
                        renderMask(portal, mask, target, transform, vertices, clip);
                        if (PENDING.size() < MAX_READBACKS
                                && history.begin(RenderStates.frameIndex)) {
                            var encoder = RenderSystem.getDevice().createCommandEncoder();
                            reduce(mask, encoder);
                            var readback = new Readback(history, signature);
                            PENDING.add(readback);
                            encoder.copyTextureToBuffer(
                                    mask.reduced.getLast(),
                                    readback.buffer,
                                    0,
                                    readback::finish,
                                    0);
                            readback.fence = encoder.createFence();
                            if (!IPGlobal.offsetOcclusionQuery) {
                                // Compatibility with the old non-predictive mode, with a bounded
                                // stall.
                                encoder.submit();
                                if (!readback.done && readback.fence.awaitCompletion(5_000_000L))
                                    readback.finish();
                            }
                        }
                    });
            probes++;
            mask.visible =
                    history.shouldRender(
                            RenderStates.frameIndex, signature, IPGlobal.offsetOcclusionQuery);
            // A portal touching the eye/near plane must never disappear through a delayed result.
            if (portal.getDistanceToNearestPointInPortal(camera) < .1) mask.visible = true;
            if (!mask.visible) {
                skipped++;
                if (!IPGlobal.offsetOcclusionQuery) synchronousOccluded++;
            }
            return mask;
        } catch (RuntimeException | Error error) {
            mask.close();
            throw error;
        }
    }

    static float[] mesh(Portal portal, Vec3 camera) {
        var values = new ArrayList<Float>();
        portal.renderViewAreaMesh(
                portal.getOriginPos().subtract(camera),
                (a, b, c, d, e, f, g, h, i) -> {
                    for (double value : new double[] {a, b, c, d, e, f, g, h, i})
                        values.add((float) value);
                });
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private static long signature(Matrix4fc transform, float[] vertices, Vector4f clip) {
        long hash = 1;
        float[] matrix = transform.get(new float[16]);
        for (float f : matrix) hash = hash * 31 + Float.floatToIntBits(f);
        for (float f : vertices) hash = hash * 31 + Float.floatToIntBits(f);
        if (clip != null)
            for (float f : new float[] {clip.x, clip.y, clip.z, clip.w})
                hash = hash * 31 + Float.floatToIntBits(f);
        return hash * 31 + (IPGlobal.enableDepthClampForPortalRendering ? 1 : 0);
    }

    private static void renderMask(
            Portal portal, Mask mask, RenderTarget parent, Matrix4fc transform,
            float[] vertices, Vector4f clip) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(mask.texture, new Vector4f());
        if (vertices.length == 0) return;
        ByteBuffer mesh = MemoryUtil.memAlloc(vertices.length * 4),
                uniform = MemoryUtil.memAlloc(112);
        try {
            for (float f : vertices) mesh.putFloat(f);
            mesh.flip();
            transform.get(0, uniform);
            uniform.position(64)
                    .putFloat(IPGlobal.enableDepthClampForPortalRendering ? 1 : 0)
                    .putFloat(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                    .putFloat(clip == null ? 0 : 1)
                    .putFloat(0);
            (clip == null ? new Vector4f() : clip).get(80, uniform);
            NativePortalDepth.equation(portal, transform, vertices, parent.width, parent.height)
                    .get(96, uniform);
            uniform.position(112).flip();
            var device = RenderSystem.getDevice();
            try (var vb =
                            device.createBuffer(
                                    () -> "Portal visibility mesh", GpuBuffer.USAGE_VERTEX, mesh);
                    var ub =
                            device.createBuffer(
                                    () -> "Portal mask projection",
                                    GpuBuffer.USAGE_UNIFORM,
                                    uniform);
                    var pass =
                            encoder.createRenderPass(
                                    () -> "Portal visibility coverage",
                                    mask.view,
                                    Optional.empty(),
                                    parent.getDepthTextureView(),
                                    OptionalDouble.empty())) {
                pass.setPipeline(MASK_PIPELINE);
                pass.setVertexBuffer(0, vb.slice());
                pass.setUniform("MaskTransform", ub);
                pass.draw(vertices.length / 3, 1, 0, 0);
            }
        } finally {
            MemoryUtil.memFree(mesh);
            MemoryUtil.memFree(uniform);
        }
    }

    private static void reduce(Mask mask, CommandEncoder encoder) {
        GpuTextureView source = mask.view;
        for (GpuTextureView target : mask.reducedViews) {
            try (var pass =
                    encoder.createRenderPass(
                            () -> "Portal visibility max reduction", target, Optional.empty())) {
                pass.setPipeline(REDUCE_PIPELINE);
                pass.bindTexture(
                        "Coverage",
                        source,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
            source = target;
        }
    }

    public static void endFrame() {
        MASKS.removeIf(
                m -> {
                    if (!m.inUse && RenderStates.frameIndex - m.lastUsed > 120) {
                        m.destroy();
                        return true;
                    }
                    return false;
                });
    }

    public static void close() {
        epoch++;
        PENDING.forEach(Readback::cancel);
        PENDING.clear();
        MASKS.forEach(Mask::destroy);
        MASKS.clear();
    }
}
