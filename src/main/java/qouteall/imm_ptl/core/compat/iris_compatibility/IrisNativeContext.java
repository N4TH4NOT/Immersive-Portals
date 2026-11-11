package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.GpuDeviceAccessor;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;

import org.joml.*;

import qouteall.imm_ptl.core.compat.mixin.iris.*;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Each offscreen native renderer owns its Iris pipeline, including recursion into the same
 * dimension.
 */
final class IrisNativeContext {
    private IrisNativeContext() {}

    private static final Map<Object, PipelineManager> MANAGERS = new IdentityHashMap<>();
    private static final Map<
                    Object, net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings>
            SETTINGS = new IdentityHashMap<>();

    static Runnable push(Object key, boolean isolated) {
        if (!isolated) return () -> {};
        var parent = Iris.getPipelineManager();
        var parentSettings =
                net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE;
        IrisWorldSettingsAccess.ip_set(
                SETTINGS.computeIfAbsent(
                        key,
                        k ->
                                new net.irisshaders.iris.shaderpack.materialmap
                                        .WorldRenderingSettings()));
        var child =
                MANAGERS.computeIfAbsent(
                        key,
                        k ->
                                new PipelineManager(
                                        ((IrisPipelineManagerAccess) parent).ip_factory()));
        var state = CapturedRenderingState.INSTANCE;
        Matrix4f view = new Matrix4f(state.getGbufferModelView()),
                projection = new Matrix4f(state.getGbufferProjection());
        Vector3d fog = new Vector3d(state.getFogColor());
        float density = state.getFogDensity(),
                darkness = state.getDarknessLightFactor(),
                partial = state.getTickDelta(),
                real = state.getRealTickDelta(),
                clouds = state.getCloudTime(),
                alpha = state.getCurrentAlphaTest();
        int entity = state.getCurrentRenderedEntity(),
                block = state.getCurrentRenderedBlockEntity(),
                item = state.getCurrentRenderedItem();
        boolean safe = ImmediateState.safeToMultiply,
                rendering = ImmediateState.isRenderingLevel,
                bypass = ImmediateState.bypass;
        IrisManagerAccess.ip_set(child);
        return () -> {
            IrisManagerAccess.ip_set(parent);
            IrisWorldSettingsAccess.ip_set(parentSettings);
            state.setGbufferModelView(view);
            state.setGbufferProjection(projection);
            state.setFogColor((float) fog.x, (float) fog.y, (float) fog.z);
            state.setFogDensity(density);
            state.setDarknessLightFactor(darkness);
            state.setTickDelta(partial);
            state.setRealTickDelta(real);
            state.setCloudTime(clouds);
            state.setCurrentAlphaTest(alpha);
            state.setCurrentEntity(entity);
            state.setCurrentBlockEntity(block);
            state.setCurrentRenderedItem(item);
            ImmediateState.safeToMultiply = safe;
            ImmediateState.isRenderingLevel = rendering;
            ImmediateState.bypass = bypass;
        };
    }

    static void finish() {
        Iris.getPipelineManager()
                .getPipeline()
                .ifPresent(WorldRenderingPipeline::finalizeGameRendering);
    }

    static void close(Object key) {
        SETTINGS.remove(key);
        var manager = MANAGERS.remove(key);
        if (manager != null) manager.destroyPipeline();
    }

    private static final class BorrowedTexture extends GlTexture {
        BorrowedTexture(
                String label,
                GpuFormat format,
                int width,
                int height,
                int id,
                FrameBufferCache cache) {
            super(
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                    label,
                    format,
                    width,
                    height,
                    1,
                    1,
                    id,
                    cache);
        }

        @Override
        public void close() {
            /* The texture is owned by Iris. */
        }
    }

    private static final class BorrowedTarget extends RenderTarget implements AutoCloseable {
        BorrowedTarget(net.irisshaders.iris.targets.RenderTarget color, int id, GpuTexture depth) {
            super("Iris portal composite", true, format(color.getInternalFormat().name()));
            width = color.getWidth();
            height = color.getHeight();
            var cache =
                    ((IrisGlDeviceAccess)
                                    ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend())
                            .ip_framebuffers();
            colorTexture = new BorrowedTexture(label, format, width, height, id, cache);
            colorTextureView = RenderSystem.getDevice().createTextureView(colorTexture);
            depthTexture = depth;
            depthTextureView = RenderSystem.getDevice().createTextureView(depth);
        }

        private static GpuFormat format(String iris) {
            String suffix =
                    iris.endsWith("F")
                            ? "_FLOAT"
                            : iris.endsWith("UI")
                                    ? "_UINT"
                                    : iris.endsWith("I")
                                            ? "_SINT"
                                            : iris.endsWith("SNORM") ? "_SNORM" : "_UNORM";
            String base = iris.replace("_SNORM", "").replaceAll("UI$|I$|F$", "");
            if (base.equals("RGBA")) base = "RGBA8";
            try {
                return GpuFormat.valueOf(base + suffix);
            } catch (IllegalArgumentException ignored) {
                return GpuFormat.RGBA8_UNORM;
            }
        }

        @Override
        public void close() {
            colorTextureView.close();
            depthTextureView.close();
        }
    }

    static void composite(RenderTarget fallback, Consumer<RenderTarget> draw) {
        if (!(Iris.getPipelineManager().getPipelineNullable()
                instanceof IrisRenderingPipeline pipeline)) {
            draw.accept(fallback);
            return;
        }
        var targets = ((IrisRenderTargetsAccess) pipeline).ip_targets();
        var color = targets.getOrCreate(0);
        int texture =
                pipeline.getFlippedAfterPrepare().contains(0)
                        ? color.getAltTexture()
                        : color.getMainTexture();
        boolean safe = ImmediateState.safeToMultiply,
                bypass = ImmediateState.bypass,
                ignore = ImmediateState.temporarilyIgnorePass;
        try (var target = new BorrowedTarget(color, texture, targets.getDepthTexture())) {
            ImmediateState.safeToMultiply = false;
            ImmediateState.bypass = true;
            ImmediateState.temporarilyIgnorePass = true;
            draw.accept(target);
        } finally {
            ImmediateState.safeToMultiply = safe;
            ImmediateState.bypass = bypass;
            ImmediateState.temporarilyIgnorePass = ignore;
        }
    }
}
