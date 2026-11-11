package qouteall.imm_ptl.core.render.renderer;

import org.joml.Matrix4f;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.*;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.ArrayList;

/** Layered texture-stencil masks, independent of backend-specific stencil attachment support. */
public class RendererUsingStencil extends RendererUsingFrameBuffer {
    protected boolean batchVisibility() {
        return false;
    }

    @Override
    public void onBeforeTranslucentRendering(Matrix4f view) {
        if (PortalRendering.getPortalLayer() >= PortalRendering.getMaxPortalLayer()) return;
        var projection = NativeWorldRenderer.projection(client.gameRenderer);
        var target = client.gameRenderer.mainRenderTarget();
        var camera = client.gameRenderer.mainCamera().position();
        record Prepared(Portal portal, NativePortalOcclusion.Mask mask) {}
        var prepared = new ArrayList<Prepared>();
        try {
            for (Portal portal : getPortalsToRender(view)) {
                var mask = NativePortalOcclusion.query(portal, target, camera, view, projection);
                if (batchVisibility()) {
                    prepared.add(new Prepared(portal, mask));
                    // Bound scratch texture memory even with hundreds of visible portals.
                    if (prepared.size() == 4) {
                        for (Prepared entry : prepared)
                            renderMasked(entry.portal(), entry.mask(), target, projection);
                        prepared.forEach(entry -> entry.mask().close());
                        prepared.clear();
                    }
                } else
                    try (mask) {
                        renderMasked(portal, mask, target, projection);
                    }
            }
            for (Prepared entry : prepared)
                renderMasked(entry.portal(), entry.mask(), target, projection);
        } finally {
            prepared.forEach(entry -> entry.mask().close());
        }
    }

    private void renderMasked(
            Portal portal,
            NativePortalOcclusion.Mask mask,
            com.mojang.blaze3d.pipeline.RenderTarget target,
            Matrix4f projection) {
        if (!mask.isVisible()) return;
        PortalRendering.pushPortalLayer(portal);
        try {
            PortalRenderInfo.get(portal).markRendered();
            renderPortalContent(portal);
            NativePortalCompositor.drawPortalMasked(
                    portal, MyGameRenderer.renderedTarget(), target, projection, mask);
        } finally {
            PortalRendering.popPortalLayer();
        }
    }
}
