package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.Plane;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** Portal mesh compositing through Blaze3D; works with its Vulkan and OpenGL devices. */
public final class NativePortalCompositor {
    private NativePortalCompositor() {}

    private record PipelineKey(
            boolean depth,
            boolean writeDepth,
            boolean blend,
            boolean writeAlpha,
            GpuFormat format) {}

    private static final Map<PipelineKey, RenderPipeline> PIPELINES = new HashMap<>();

    private static RenderPipeline pipeline(PipelineKey key) {
        return PIPELINES.computeIfAbsent(
                key,
                k ->
                        RenderPipeline.builder()
                                .withLocation(
                                        Identifier.fromNamespaceAndPath(
                                                "imm_ptl", "pipeline/portal_" + PIPELINES.size()))
                                .withVertexShader(
                                        Identifier.fromNamespaceAndPath(
                                                "imm_ptl", "core/portal_native"))
                                .withFragmentShader(
                                        Identifier.fromNamespaceAndPath(
                                                "imm_ptl", "core/portal_native"))
                                .withBindGroupLayout(
                                        BindGroupLayout.builder()
                                                .withUniform(
                                                        "PortalTransform",
                                                        UniformType.UNIFORM_BUFFER)
                                                .withSampler("PortalColor")
                                                .withSampler("PortalDepth")
                                                .withSampler("PortalMask")
                                                .build())
                                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                                .withCull(false)
                                .withColorTargetState(
                                        new ColorTargetState(
                                                k.blend
                                                        ? Optional.of(
                                                                BlendFunction
                                                                        .TRANSLUCENT_PREMULTIPLIED_ALPHA)
                                                        : Optional.empty(),
                                                k.format,
                                                k.writeAlpha
                                                        ? ColorTargetState.WRITE_ALL
                                                        : ColorTargetState.WRITE_COLOR))
                                .withDepthStencilState(
                                        new DepthStencilState(
                                                k.depth
                                                        ? CompareOp.GREATER_THAN_OR_EQUAL
                                                        : CompareOp.ALWAYS_PASS,
                                                k.writeDepth))
                                .build());
    }

    public static void drawPortal(
            Portal portal,
            RenderTarget source,
            RenderTarget destination,
            Vec3 camera,
            Matrix4fc view,
            Matrix4fc projection) {
        drawPortal(portal, source, destination, camera, view, projection, null);
    }

    public static void drawPortal(
            Portal portal,
            RenderTarget source,
            RenderTarget destination,
            Vec3 camera,
            Matrix4fc view,
            Matrix4fc projection,
            Plane parentClip) {
        Vec3 terrainCamera = NativePortalDepth.renderCamera(portal, camera);
        ArrayList<Float> vertices = new ArrayList<>();
        portal.renderViewAreaMesh(
                portal.getOriginPos().subtract(terrainCamera),
                (ax, ay, az, bx, by, bz, cx, cy, cz) -> {
                    for (double v : new double[] {ax, ay, az, bx, by, bz, cx, cy, cz})
                        vertices.add((float) v);
                });
        qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface.invoker.withCompositeTarget(
                destination,
                target ->
                        draw(
                                vertices,
                                new Matrix4f(projection).mul(view),
                                source,
                                target,
                                NativeWorldRenderer.lastFlipX() ? target.width : 0,
                                0,
                                NativeWorldRenderer.lastFlipX() ? -target.width : target.width,
                                target.height,
                                new PipelineKey(
                                        true,
                                        true,
                                        false,
                                        true,
                                        target.getColorTexture().getFormat()),
                                portal.isFuseView(),
                                projection,
                                null,
                                NativePortalClipping.equation(parentClip, terrainCamera),
                                NativePortalDepth.equation(portal,
                                        new Matrix4f(projection).mul(view), vertices,
                                        target.width, target.height)));
    }

    /** Texture-stencil composition: coverage and biased surface depth come from the prepass. */
    public static void drawPortalMasked(
            Portal portal,
            RenderTarget source,
            RenderTarget destination,
            Matrix4fc projection,
            NativePortalOcclusion.Mask mask) {
        var vertices = new ArrayList<Float>();
        for (float v : new float[] {-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0})
            vertices.add(v);
        qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface.invoker.withCompositeTarget(
                destination,
                target ->
                        draw(
                                vertices,
                                new Matrix4f(),
                                source,
                                target,
                                NativeWorldRenderer.lastFlipX() ? target.width : 0,
                                0,
                                NativeWorldRenderer.lastFlipX() ? -target.width : target.width,
                                target.height,
                                new PipelineKey(
                                        true,
                                        true,
                                        false,
                                        true,
                                        target.getColorTexture().getFormat()),
                                portal.isFuseView(),
                                projection,
                                mask.textureView(),
                                null,
                                null));
    }

    /** Coordinates are physical pixels measured from the top left of the destination. */
    public static void drawRectangle(
            RenderTarget source,
            RenderTarget destination,
            int x,
            int y,
            int width,
            int height,
            boolean blend,
            boolean writeAlpha) {
        if (width <= 0 || height <= 0) return;
        float left = 2f * x / destination.width - 1;
        float right = 2f * (x + width) / destination.width - 1;
        float bottom = 1 - 2f * (y + height) / destination.height;
        float top = 1 - 2f * y / destination.height;
        ArrayList<Float> vertices = new ArrayList<>();
        for (float v :
                new float[] {
                    left, bottom, 0, right, bottom, 0, right, top, 0, left, bottom, 0, right, top,
                    0, left, top, 0
                }) vertices.add(v);
        draw(
                vertices,
                new Matrix4f(),
                source,
                destination,
                x,
                destination.height - y - height,
                width,
                height,
                new PipelineKey(
                        false, false, blend, writeAlpha, destination.getColorTexture().getFormat()),
                false,
                new Matrix4f(),
                null,
                null,
                null);
    }

    private static void draw(
            ArrayList<Float> vertices,
            Matrix4fc transform,
            RenderTarget source,
            RenderTarget destination,
            int x,
            int y,
            int width,
            int height,
            PipelineKey key,
            boolean fuse,
            Matrix4fc parentProjection,
            GpuTextureView mask,
            Vector4f clip,
            Vector4f depthEquation) {
        RenderSystem.assertOnRenderThread();
        if (vertices.isEmpty()) return;
        if (source == destination)
            throw new IllegalArgumentException(
                    "A portal cannot sample its destination render target");
        ByteBuffer mesh = MemoryUtil.memAlloc(Math.multiplyExact(vertices.size(), Float.BYTES));
        ByteBuffer uniform = MemoryUtil.memAlloc(256);
        try {
            for (float value : vertices) mesh.putFloat(value);
            mesh.flip();
            transform.get(0, uniform);
            uniform.position(64).putFloat(x).putFloat(y).putFloat(width).putFloat(height);
            NativeWorldRenderer.lastProjection().invert().get(80, uniform);
            parentProjection.get(144, uniform);
            uniform.position(208)
                    .putFloat(fuse ? 1 : 0)
                    .putFloat(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                    .putFloat(NativeWorldRenderer.lastFlipX() ? 1 : 0)
                    .putFloat(
                            (mask == null ? 0 : 1)
                                    | (IPGlobal.enableDepthClampForPortalRendering ? 2 : 0)
                                    | (clip == null ? 0 : 4)
                                    | (key.depth ? 8 : 0));
            (clip == null ? new Vector4f() : clip).get(224, uniform);
            (depthEquation == null ? new Vector4f(0, 0, 0, 0) : depthEquation).get(240, uniform);
            uniform.position(256).flip();
            var device = RenderSystem.getDevice();
            try (GpuBuffer vertexBuffer =
                            device.createBuffer(() -> "Portal mesh", GpuBuffer.USAGE_VERTEX, mesh);
                    GpuBuffer transformBuffer =
                            device.createBuffer(
                                    () -> "Portal projection", GpuBuffer.USAGE_UNIFORM, uniform);
                    RenderPass pass =
                            device.createCommandEncoder()
                                    .createRenderPass(
                                            () -> "Immersive portal",
                                            destination.getColorTextureView(),
                                            Optional.empty(),
                                            destination.getDepthTextureView(),
                                            OptionalDouble.empty())) {
                pass.setPipeline(pipeline(key));
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.setUniform("PortalTransform", transformBuffer);
                pass.bindTexture(
                        "PortalColor",
                        source.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.bindTexture(
                        "PortalDepth",
                        source.getDepthTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.bindTexture(
                        "PortalMask",
                        mask == null ? source.getColorTextureView() : mask,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(vertices.size() / 3, 1, 0, 0);
            }
        } finally {
            MemoryUtil.memFree(mesh);
            MemoryUtil.memFree(uniform);
        }
    }
}
