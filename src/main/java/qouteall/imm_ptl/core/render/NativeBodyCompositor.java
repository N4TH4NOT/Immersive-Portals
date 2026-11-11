package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import qouteall.q_misc_util.my_util.Plane;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** Clips native entity features in world space without changing their perspective or depth. */
final class NativeBodyCompositor {
    private static final Map<GpuFormat, RenderPipeline> PIPELINES = new HashMap<>();

    private NativeBodyCompositor() {}

    private static RenderPipeline pipeline(GpuFormat format) {
        return PIPELINES.computeIfAbsent(format, value -> RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("imm_ptl", "pipeline/body_" + PIPELINES.size()))
            .withVertexShader(Identifier.fromNamespaceAndPath("imm_ptl", "core/body_clip"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("imm_ptl", "core/body_clip"))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform("BodyClipping", UniformType.UNIFORM_BUFFER)
                .withSampler("BodyColor").withSampler("BodyDepth").build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), value,
                ColorTargetState.WRITE_ALL))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .build());
    }

    static Vector4f planeEquation(Plane plane, Vec3 camera) {
        if (plane == null) return new Vector4f(0, 0, 0, 0);
        return new Vector4f((float) plane.normal().x, (float) plane.normal().y,
            (float) plane.normal().z, (float) -plane.normal().dot(plane.pos().subtract(camera)));
    }

    static void draw(RenderTarget source, RenderTarget destination, Matrix4fc projection,
            Matrix4fc view, Vec3 camera, Plane bodyClip, Plane viewClip) {
        var uniform = MemoryUtil.memAlloc(112);
        try {
            new Matrix4f(projection).mul(view).invert().get(0, uniform);
            planeEquation(bodyClip, camera).get(64, uniform);
            planeEquation(viewClip, camera).get(80, uniform);
            uniform.position(96).putFloat(destination.width).putFloat(destination.height)
                .putFloat(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                .putFloat(0);
            uniform.position(112).flip();
            var device = RenderSystem.getDevice();
            try (var buffer = device.createBuffer(() -> "Portal body clipping",
                    GpuBuffer.USAGE_UNIFORM, uniform);
                 var pass = device.createCommandEncoder().createRenderPass(
                    () -> "Clipped portal body", destination.getColorTextureView(), Optional.empty(),
                    destination.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(pipeline(destination.getColorTexture().getFormat()));
                pass.setUniform("BodyClipping", buffer);
                pass.bindTexture("BodyColor", source.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.bindTexture("BodyDepth", source.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
        } finally {
            MemoryUtil.memFree(uniform);
        }
    }
}
