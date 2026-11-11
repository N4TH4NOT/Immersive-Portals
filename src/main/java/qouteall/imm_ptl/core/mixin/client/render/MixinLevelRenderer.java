package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer implements IEWorldRenderer {
    @Shadow @Final private GameRenderer gameRenderer;
    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow public abstract ViewArea viewArea();
    @Shadow public abstract EntityRenderDispatcher entityRenderDispatcher();
    @Shadow public abstract ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections();

    // Render portals after opaque features, before depth is copied to the translucent targets.
    @WrapOperation(method="lambda$addMainPass$0", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V"))
    private void ip_portalPass(FeatureRenderDispatcher.PreparedFrame frame, Operation<Void> original) {
        original.call(frame);
        if (!qouteall.imm_ptl.core.render.NativeWorldRenderer.isRenderingScene()
                && !qouteall.imm_ptl.core.ClientWorldLoader.isPrimaryWorldReady()) return;
        qouteall.imm_ptl.core.render.NativeEntityPass.render(gameRenderer.gameRenderState().levelRenderState);
        if (IPCGlobal.renderer != null && !IrisInterface.invoker.isRenderingShadowMap()) {
            IPCGlobal.renderer.onBeforeTranslucentRendering(gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix);
        }
    }
    @WrapOperation(method="compileSections",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileAsync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V"))
    private void ip_immediateTerrain(SectionRenderDispatcher.RenderSection section,net.minecraft.client.renderer.chunk.RenderSectionRegion region,Operation<Void> original) {
        if(qouteall.imm_ptl.core.render.ForceMainThreadRebuild.isCurrentFrameForceMainThreadRebuild())section.compileSync(region);
        else original.call(section,region);
    }
    @Override public EntityRenderDispatcher ip_getEntityRenderDispatcher() { return entityRenderDispatcher(); }
    @Override public ViewArea ip_getBuiltChunkStorage() { return viewArea(); }
    @Override public RenderBuffers ip_getRenderBuffers() { return renderBuffers; }
    @Override public ObjectArrayList<SectionRenderDispatcher.RenderSection> portal_getChunkInfoList() { return visibleSections(); }
}
