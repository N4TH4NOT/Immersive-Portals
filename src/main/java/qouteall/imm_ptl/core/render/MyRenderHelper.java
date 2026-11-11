package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** GPU and GUI adapters. Framebuffer coordinates are physical pixels from the top left. */
public final class MyRenderHelper {
    public static final Minecraft client = Minecraft.getInstance();
    private static final Map<Integer, RenderPipeline> GUI_PIPELINES = new HashMap<>();
    private MyRenderHelper() {}
    public static void init() {}

    public static void drawPortalAreaWithFramebuffer(Portal portal, RenderTarget source, Matrix4f view, Matrix4f projection) {
        NativePortalCompositor.drawPortal(portal, source, client.gameRenderer.mainRenderTarget(), client.gameRenderer.mainCamera().position(), view, projection);
    }
    public static void drawScreenFrameBuffer(RenderTarget source, boolean blend, boolean alpha) {
        drawFramebufferWithCoordinatesAndDimensions(source, blend, alpha, 0, 0, source.width, source.height);
    }
    public static void drawFramebuffer(RenderTarget source, boolean blend, boolean alpha, float xMin, float xMax, float yMin, float yMax) {
        drawFramebufferWithBounds(source, blend, alpha, Math.round(xMin), Math.round(xMax), Math.round(yMin), Math.round(yMax));
    }
    public static void drawFramebuffer(RenderTarget source, boolean blend, boolean alpha, float xMin, double xMax, float yMin, double yMax) {
        drawFramebufferWithBounds(source, blend, alpha, Math.round(xMin), (int)Math.round(xMax), Math.round(yMin), (int)Math.round(yMax));
    }
    public static void drawFramebufferWithViewport(RenderTarget source, boolean blend, boolean alpha,
        float left, float right, float bottom, float up, int viewportWidth, int viewportHeight) {
        drawFramebufferWithViewport(source, blend, alpha, left, (double)right, bottom, (double)up, viewportWidth, viewportHeight);
    }
    public static void drawFramebufferWithViewport(RenderTarget source, boolean blend, boolean alpha,
        float left, double right, float bottom, double up, int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;
        float sx = (float)client.getWindow().getWidth() / viewportWidth;
        float sy = (float)client.getWindow().getHeight() / viewportHeight;
        drawFramebufferWithBounds(source, blend, alpha, Math.round(left*sx), (int)Math.round(right*sx), Math.round(bottom*sy), (int)Math.round(up*sy));
    }
    public static void drawFramebufferWithBounds(RenderTarget source, boolean blend, boolean alpha, int xMin, int xMax, int yMin, int yMax) {
        drawFramebufferWithCoordinatesAndDimensions(source, blend, alpha, Math.min(xMin,xMax), Math.min(yMin,yMax), Math.abs(xMax-xMin), Math.abs(yMax-yMin));
    }
    public static void drawFramebufferWithCoordinatesAndDimensions(RenderTarget source, boolean blend, boolean alpha, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        // Submit into the GUI extraction stream so ordering, resizing and GUI scale
        // remain correct when drawing is performed later by the native renderer.
        RenderPipeline pipeline = GUI_PIPELINES.computeIfAbsent((blend ? 1 : 0) | (alpha ? 2 : 0), key -> {
            RenderPipeline base = RenderPipelines.GUI_TEXTURED;
            RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("imm_ptl", "pipeline/gui_portal_" + key))
                .withVertexShader(base.getVertexShader()).withFragmentShader(base.getFragmentShader())
                .withVertexBinding(0, base.getVertexFormatBinding(0)).withPrimitiveTopology(base.getPrimitiveTopology())
                .withCull(base.isCull()).withDepthStencilState(Optional.ofNullable(base.getDepthStencilState()))
                .withColorTargetState(new ColorTargetState(blend ? Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA) : Optional.empty(),
                    GpuFormat.RGBA8_UNORM, alpha ? ColorTargetState.WRITE_ALL : ColorTargetState.WRITE_COLOR));
            base.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
            return builder.build();
        });
        float inverseGuiScale = 1f / client.getWindow().getGuiScale();
        client.gameRenderer.gameRenderState().guiRenderState.addGuiElement(new BlitRenderState(
            pipeline, TextureSetup.singleTexture(source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f().scale(inverseGuiScale), x, y, x+width, y+height, 0, 1, 1, 0, -1, null));
    }
    public static void lateUpdateLight() {
        if (ClientWorldLoader.getIsInitialized()) ClientWorldLoader.getClientWorlds().forEach(world -> world.getChunkSource().getLightEngine().runLightUpdates());
    }
    public static void earlyRemoteUpload() {
        ClientWorldLoader.WORLD_RENDERER_MAP.values().forEach(renderer -> {
            if (renderer.sectionRenderDispatcher() != null) renderer.sectionRenderDispatcher().uploadTerrainBuffersToGpu();
        });
    }
    public static float transformFogDistance(float distance) { return WorldRenderInfo.isFogEnabled() ? distance : Float.MAX_VALUE; }
}
