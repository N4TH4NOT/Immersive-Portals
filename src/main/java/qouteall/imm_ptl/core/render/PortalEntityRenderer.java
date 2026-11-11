package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import qouteall.imm_ptl.core.portal.Portal;
import java.util.List;

public final class PortalEntityRenderer extends EntityRenderer<Portal,PortalEntityRenderer.State> {
    public static final class State extends EntityRenderState { public List<OverlayRendering.Part> overlay=List.of(); }
    public PortalEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(Portal portal, State state, float partial) {
        super.extractRenderState(portal,state,partial);
        state.overlay=OverlayRendering.extract(portal);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        OverlayRendering.submit(state.overlay,pose,collector);
        super.submit(state,pose,collector,camera);
    }
}
