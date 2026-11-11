package qouteall.imm_ptl.core.compat.iris_compatibility;

import org.joml.Matrix4f;

import qouteall.imm_ptl.core.render.renderer.RendererDebug;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

/** Iris normal mode uses depth-tested texture stencil windows in the active shader target. */
public final class IrisPortalRenderer extends RendererUsingStencil {
    public static final IrisPortalRenderer instance = new IrisPortalRenderer(false);
    public static final IrisPortalRenderer debugModeInstance = new IrisPortalRenderer(true);
    private final boolean debug;
    private final RendererDebug debugRenderer = new RendererDebug();

    private IrisPortalRenderer(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void onBeforeTranslucentRendering(Matrix4f view) {
        if (debug) debugRenderer.onBeforeTranslucentRendering(view);
        else super.onBeforeTranslucentRendering(view);
    }

    public static void init() {}
}
