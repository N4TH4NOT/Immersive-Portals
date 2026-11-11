package qouteall.imm_ptl.core.compat.iris_compatibility;

import org.joml.Matrix4f;

import qouteall.imm_ptl.core.render.renderer.RendererDebug;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

/** Batches visibility prepasses before world rendering, limiting live masks to four per layer. */
public final class ExperimentalIrisPortalRenderer extends RendererUsingStencil {
    public static final ExperimentalIrisPortalRenderer instance =
            new ExperimentalIrisPortalRenderer(false);
    public static final ExperimentalIrisPortalRenderer debugModeInstance =
            new ExperimentalIrisPortalRenderer(true);
    private final boolean debug;
    private final RendererDebug debugRenderer = new RendererDebug();

    private ExperimentalIrisPortalRenderer(boolean debug) {
        this.debug = debug;
    }

    @Override
    protected boolean batchVisibility() {
        return true;
    }

    @Override
    public void onBeforeTranslucentRendering(Matrix4f view) {
        if (debug) debugRenderer.onBeforeTranslucentRendering(view);
        else super.onBeforeTranslucentRendering(view);
    }

    public static void init() {}
}
