package qouteall.imm_ptl.core.compat.iris_compatibility;

import org.joml.Matrix4f;

import qouteall.imm_ptl.core.render.renderer.RendererDebug;
import qouteall.imm_ptl.core.render.renderer.RendererUsingFrameBuffer;

/** Compatibility mode composites the portal mesh directly instead of using a texture stencil. */
public final class IrisCompatibilityPortalRenderer extends RendererUsingFrameBuffer {
    public static final IrisCompatibilityPortalRenderer instance =
            new IrisCompatibilityPortalRenderer(false);
    public static final IrisCompatibilityPortalRenderer debugModeInstance =
            new IrisCompatibilityPortalRenderer(true);
    private final boolean debug;
    private final RendererDebug debugRenderer = new RendererDebug();

    private IrisCompatibilityPortalRenderer(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void onBeforeTranslucentRendering(Matrix4f view) {
        if (debug) debugRenderer.onBeforeTranslucentRendering(view);
        else super.onBeforeTranslucentRendering(view);
    }

    public static void init() {}
}
