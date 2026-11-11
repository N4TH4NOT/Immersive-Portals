package qouteall.imm_ptl.core.render.renderer;

import org.joml.Matrix4f;
import qouteall.imm_ptl.core.render.*;
import qouteall.imm_ptl.core.render.context_management.*;

public final class RendererDebug extends RendererUsingFrameBuffer {
    @Override public void onBeforeTranslucentRendering(Matrix4f view) {
        if (PortalRendering.isRendering() || RenderStates.getRenderedPortalNum() != 0) return;
        var portals = getPortalsToRender(view);
        if (portals.isEmpty()) return;
        var target = client.gameRenderer.mainRenderTarget();
        PortalRendering.pushPortalLayer(portals.getFirst());
        try {
            renderPortalContent(portals.getFirst());
            NativePortalCompositor.drawRectangle(MyGameRenderer.renderedTarget(), target, 0, 0, target.width, target.height, false, true);
        } finally { PortalRendering.popPortalLayer(); }
    }
}
