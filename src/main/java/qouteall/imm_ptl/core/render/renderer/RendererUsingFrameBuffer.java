package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.*;
import qouteall.imm_ptl.core.render.context_management.*;

/** Recursive compositing of native render targets, independent of the graphics backend. */
public class RendererUsingFrameBuffer extends PortalRenderer {
    @Override
    public void onBeforeTranslucentRendering(Matrix4f view) {
        if (PortalRendering.getPortalLayer() >= PortalRendering.getMaxPortalLayer()) return;
        Matrix4f projection = NativeWorldRenderer.projection(client.gameRenderer);
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        Vec3 camera = client.gameRenderer.mainCamera().position();
        var parentClip = NativePortalClipping.activePlane();
        for (Portal portal : getPortalsToRender(view)) {
            try (var mask = NativePortalOcclusion.query(portal, target, camera, view, projection)) {
                if (!mask.isVisible()) continue;
                PortalRendering.pushPortalLayer(portal);
                try {
                    qouteall.imm_ptl.core.portal.PortalRenderInfo.get(portal).markRendered();
                    renderPortalContent(portal);
                    NativePortalCompositor.drawPortal(
                            portal,
                            MyGameRenderer.renderedTarget(),
                            target,
                            camera,
                            view,
                            projection,
                            parentClip);
                } finally {
                    PortalRendering.popPortalLayer();
                }
            }
        }
    }

    @Override
    public void onAfterTranslucentRendering(Matrix4f view) {}

    @Override
    public void onHandRenderingEnded() {}

    @Override
    public void prepareRendering() {}

    @Override
    public void finishRendering() {}

    @Override
    public void renderPortalInEntityRenderer(Portal portal) {}

    @Override
    public boolean replaceFrameBufferClearing() {
        return false;
    }
}
